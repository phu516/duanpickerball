package com.pickleball.auth;

import com.pickleball.auth.entity.UserAccount;
import com.pickleball.auth.repository.UserAccountRepository;
import com.pickleball.auth.service.PlayerAccountService;
import com.pickleball.common.exception.BusinessException;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = PlayerAccountServiceTest.TestApplication.class, properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:player-account;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/migration/V1__create_users_and_access.sql,classpath:db/test/player_account_tokens.sql",
        "spring.mail.host=example.invalid", "app.mail-from=no-reply@example.com", "app.public-url=http://localhost:5173"
})
@ActiveProfiles({"test", "player-account"})
class PlayerAccountServiceTest {
    @Configuration(proxyBeanMethods = false)
    @Profile("player-account")
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = UserAccount.class)
    @EnableJpaRepositories(basePackageClasses = UserAccountRepository.class)
    @Import(PlayerAccountService.class)
    static class TestApplication {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
        @Bean SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }
        @Bean JavaMailSender mailSender() { return mock(JavaMailSender.class); }
    }

    @Autowired PlayerAccountService accounts;
    @Autowired UserAccountRepository users;
    @Autowired JavaMailSender mail;
    @Autowired PasswordEncoder passwords;
    @Autowired SessionRegistry sessions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void registrationVerificationAndPasswordResetAreSingleUse() {
        clearInvocations(mail);
        accounts.register("player_test", "Nguyễn Văn A", "0900000001", "player@example.com", "OldPassword123", "test-register");
        var account = users.findByUsername("player_test").orElseThrow();
        assertFalse(account.isEnabled());
        String verification = mailOtp(1, 6);
        String wrongVerification = "000000".equals(verification) ? "000001" : "000000";
        assertThrows(BusinessException.class, () -> accounts.verifyEmail("player@example.com", wrongVerification, "test-verify-email"));
        assertEquals(1, jdbc.queryForObject("select attempts from email_verification_tokens where user_id=?", Integer.class, account.getId()));
        accounts.verifyEmail("player@example.com", verification, "test-verify-email");
        assertTrue(users.findByUsername("player_test").orElseThrow().isEnabled());
        assertThrows(BusinessException.class, () -> accounts.verifyEmail("player@example.com", verification, "test-verify-email"));

        var principal = User.withUsername("player_test").password(account.getPasswordHash()).roles("PLAYER").build();
        sessions.registerNewSession("old-session", principal);
        accounts.forgotPassword("player@example.com", "test-forgot");
        String otp = mailOtp(2, 8);
        assertThrows(BusinessException.class, () -> accounts.verifyResetOtp("player@example.com", "00000000".equals(otp) ? "00000001" : "00000000", "test-verify-wrong"));
        String reset = accounts.verifyResetOtp("player@example.com", otp, "test-verify");
        assertThrows(BusinessException.class, () -> accounts.verifyResetOtp("player@example.com", otp, "test-verify-again"));
        accounts.resetPassword(reset, "NewPassword123");
        assertTrue(passwords.matches("NewPassword123", users.findByUsername("player_test").orElseThrow().getPasswordHash()));
        assertFalse(passwords.matches("OldPassword123", users.findByUsername("player_test").orElseThrow().getPasswordHash()));
        assertTrue(sessions.getSessionInformation("old-session").isExpired());
        assertThrows(BusinessException.class, () -> accounts.resetPassword(reset, "AnotherPassword123"));
        accounts.forgotPassword("player@example.com", "test-forgot-again");
        // The resend cooldown is server-side; move the previous request outside it.
        jdbc.update("update password_reset_tokens set created_at=? where user_id=?", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(2), account.getId());
        accounts.forgotPassword("player@example.com", "test-forgot-again");
        String expired = mailOtp(3, 8);
        jdbc.update("update password_reset_tokens set expires_at=? where used_at is null", java.time.LocalDateTime.now().minusDays(1));
        assertThrows(BusinessException.class, () -> accounts.verifyResetOtp("player@example.com", expired, "test-expired"));

        jdbc.update("update password_reset_tokens set created_at=? where user_id=?", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(2), account.getId());
        accounts.forgotPassword("player@example.com", "test-lockout");
        String lockedOtp = mailOtp(4, 8);
        String wrongOtp = "00000000".equals(lockedOtp) ? "00000001" : "00000000";
        for (int i = 0; i < 5; i++)
            assertThrows(BusinessException.class, () -> accounts.verifyResetOtp("player@example.com", wrongOtp, "test-lockout"));
        assertEquals(5, jdbc.queryForObject("select attempts from password_reset_tokens where user_id=? order by id desc limit 1", Integer.class, account.getId()));
        assertThrows(BusinessException.class, () -> accounts.verifyResetOtp("player@example.com", lockedOtp, "test-lockout"));

        jdbc.update("update users set enabled=false where username='player_test'");
        accounts.resendVerification("player@example.com", "test-resend-disabled");
        verify(mail, times(4)).send(any(SimpleMailMessage.class));
    }

    @Test
    void resendingVerificationInvalidatesOldCodeAndLocksAfterFiveWrongAttempts() {
        clearInvocations(mail);
        accounts.register("resend_player", "Nguyễn Văn B", "0900000002", "resend@example.com", "OldPassword123", "test-register-resend");
        var user = users.findByUsername("resend_player").orElseThrow();
        String oldCode = mailOtp(1, 6);
        jdbc.update("update email_verification_tokens set created_at=? where user_id=?",
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(2), user.getId());
        accounts.resendVerification("resend@example.com", "test-resend");
        String newCode = mailOtp(2, 6);
        if (!oldCode.equals(newCode))
            assertThrows(BusinessException.class, () -> accounts.verifyEmail("resend@example.com", oldCode, "test-old-code"));
        String wrongCode = "000000".equals(newCode) ? "000001" : "000000";
        for (int i = 0; i < (oldCode.equals(newCode) ? 5 : 4); i++)
            assertThrows(BusinessException.class, () -> accounts.verifyEmail("resend@example.com", wrongCode, "test-wrong-code"));
        assertEquals(5, jdbc.queryForObject("select attempts from email_verification_tokens where user_id=? order by id desc limit 1", Integer.class, user.getId()));
        assertThrows(BusinessException.class, () -> accounts.verifyEmail("resend@example.com", newCode, "test-locked-code"));
        assertFalse(users.findById(user.getId()).orElseThrow().isEnabled());
    }

    private String mailOtp(int call, int digits) {
        var messages = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(call)).send(messages.capture());
        var matcher = Pattern.compile("(?<![0-9])[0-9]{" + digits + "}(?![0-9])").matcher(messages.getAllValues().getLast().getText());
        assertTrue(matcher.find());
        return matcher.group();
    }
}
