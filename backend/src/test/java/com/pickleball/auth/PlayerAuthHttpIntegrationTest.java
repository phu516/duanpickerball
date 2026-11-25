package com.pickleball.auth;

import com.pickleball.auth.controller.AuthController;
import com.pickleball.auth.controller.PlayerAuthController;
import com.pickleball.auth.entity.UserAccount;
import com.pickleball.auth.repository.UserAccountRepository;
import com.pickleball.auth.service.PlayerAccountService;
import com.pickleball.common.exception.GlobalExceptionHandler;
import com.pickleball.config.SecurityConfig;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = PlayerAuthHttpIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:player-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/migration/V1__create_users_and_access.sql,classpath:db/test/player_account_tokens.sql",
        "spring.mail.host=example.invalid", "app.mail-from=no-reply@example.com", "app.public-url=http://localhost:5173",
        "app.frontend-origin=http://localhost:5173"
})
@ActiveProfiles({"test", "player-http"})
class PlayerAuthHttpIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @Profile("player-http")
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = UserAccount.class)
    @EnableJpaRepositories(basePackageClasses = UserAccountRepository.class)
    @Import({SecurityConfig.class, AuthController.class, PlayerAuthController.class,
            PlayerAccountService.class, GlobalExceptionHandler.class})
    static class TestApplication {
        @Bean JavaMailSender mailSender() { return mock(JavaMailSender.class); }
    }

    @LocalServerPort int port;
    @Autowired JavaMailSender mail;
    @Autowired ObjectMapper mapper;

    @Test
    void guestBookingStaysPublicWhileMatchSearchRequiresVerifiedPlayerLogin() throws Exception {
        try (var browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build()) {
            assertEquals(401, send(browser, request("/elo/queue/missing").GET().build()).statusCode());
            assertEquals(404, send(browser, request("/guest/bookings/missing").GET().build()).statusCode());

            var csrf = mapper.readTree(send(browser, request("/auth/csrf").GET().build()).body());
            var registered = send(browser, request("/auth/register")
                    .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"web_player\",\"fullName\":\"Nguyễn Văn A\",\"phone\":\"0900000001\",\"email\":\"web@example.com\",\"password\":\"Password123\"}"))
                    .build());
            assertEquals(200, registered.statusCode(), registered.body());

            var messages = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mail).send(messages.capture());
            var matcher = Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(messages.getValue().getText());
            assertTrue(matcher.find());
            String verificationOtp = matcher.group();
            csrf = mapper.readTree(send(browser, request("/auth/csrf").GET().build()).body());
            var verified = send(browser, request("/auth/verify-email")
                    .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"web@example.com\",\"otp\":\"" + verificationOtp + "\"}"))
                    .build());
            assertEquals(200, verified.statusCode(), verified.body());

            csrf = mapper.readTree(send(browser, request("/auth/csrf").GET().build()).body());
            var login = send(browser, request("/auth/login")
                    .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=web_player&password=Password123"))
                    .build());
            assertEquals(200, login.statusCode(), login.body());
            var me = send(browser, request("/auth/me").GET().build());
            assertEquals(200, me.statusCode(), me.body());
            assertEquals("PLAYER", mapper.readTree(me.body()).get("role").asString());
            assertEquals(404, send(browser, request("/elo/queue/missing").GET().build()).statusCode());

            csrf = mapper.readTree(send(browser, request("/auth/csrf").GET().build()).body());
            var forgot = send(browser, request("/auth/forgot-password")
                    .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"web@example.com\"}")).build());
            assertEquals(200, forgot.statusCode(), forgot.body());
            messages = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mail, times(2)).send(messages.capture());
            matcher = Pattern.compile("(?<![0-9])[0-9]{8}(?![0-9])").matcher(messages.getAllValues().getLast().getText());
            assertTrue(matcher.find());
            String otp = matcher.group();

            csrf = mapper.readTree(send(browser, request("/auth/csrf").GET().build()).body());
            var otpResponse = send(browser, request("/auth/verify-reset-otp")
                    .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"web@example.com\",\"otp\":\"" + otp + "\"}")).build());
            assertEquals(200, otpResponse.statusCode(), otpResponse.body());
            String resetToken = mapper.readTree(otpResponse.body()).get("resetToken").asString();

            csrf = mapper.readTree(send(browser, request("/auth/csrf").GET().build()).body());
            var reset = send(browser, request("/auth/reset-password")
                    .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"resetToken\":\"" + resetToken + "\",\"password\":\"ChangedPassword123\"}")).build());
            assertEquals(200, reset.statusCode(), reset.body());
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(15)).header("Origin", "http://localhost:5173");
    }

    private HttpResponse<String> send(HttpClient browser, HttpRequest request) throws Exception {
        return browser.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
