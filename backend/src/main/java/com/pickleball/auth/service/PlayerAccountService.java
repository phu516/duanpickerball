package com.pickleball.auth.service;

import com.pickleball.auth.entity.UserAccount;
import com.pickleball.auth.repository.UserAccountRepository;
import com.pickleball.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerAccountService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserAccountRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final ObjectProvider<JavaMailSender> mailProvider;
    private final SessionRegistry sessions;
    private final String mailFrom;
    private final String mailHost;
    private final byte[] otpSecret;
    private final Map<String, long[]> requestCounts = new ConcurrentHashMap<>();

    public PlayerAccountService(UserAccountRepository users, JdbcTemplate jdbc, PasswordEncoder passwords,
            ObjectProvider<JavaMailSender> mailProvider, SessionRegistry sessions,
            @Value("${app.mail-from:}") String mailFrom,
            @Value("${spring.mail.host:}") String mailHost, @Value("${app.otp-secret:}") String otpSecret) {
        this.users = users;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.mailProvider = mailProvider;
        this.sessions = sessions;
        this.mailFrom = mailFrom;
        this.mailHost = mailHost;
        this.otpSecret = otpSecret.isBlank() ? randomBytes(32) : otpSecret.getBytes(StandardCharsets.UTF_8);
        if (this.otpSecret.length < 32) throw new IllegalArgumentException("PICKLE_OTP_SECRET must be at least 32 bytes");
    }

    @Transactional
    public void register(String username, String fullName, String phone, String email, String password, String address) {
        limit("register:" + address, 5);
        requireMail();
        username = username == null ? "" : username.trim();
        fullName = fullName == null ? "" : fullName.trim();
        phone = phone == null ? "" : phone.trim();
        email = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        validatePassword(password);
        if (!username.matches("[A-Za-z0-9_]{3,40}")) throw invalid("Tên đăng nhập cần 3–40 ký tự chữ, số hoặc dấu gạch dưới.");
        if (fullName.length() < 2 || fullName.length() > 120) throw invalid("Họ tên cần 2–120 ký tự.");
        if (!phone.matches("(?:0[0-9]{9}|\\+84[0-9]{9})")) throw invalid("Số điện thoại không hợp lệ.");
        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw invalid("Email không hợp lệ.");
        if (users.existsByUsername(username)) throw new BusinessException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "Tên đăng nhập đã được sử dụng.");
        if (users.existsByEmailIgnoreCase(email)) throw new BusinessException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email đã được sử dụng.");
        try {
            UserAccount user = users.saveAndFlush(new UserAccount(username, passwords.encode(password), fullName, phone, email));
            issueVerification(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", "Tên đăng nhập hoặc email đã được sử dụng.");
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void verifyEmail(String email, String otp, String address) {
        limit("email-verify:" + address, 20);
        if (otp == null || !otp.matches("[0-9]{6}")) throw invalidVerificationOtp();
        UserAccount user = users.findByEmailIgnoreCase(normalize(email))
                .filter(account -> !account.isEmailVerified() && "PLAYER".equalsIgnoreCase(account.getRole()))
                .orElseThrow(PlayerAccountService::invalidVerificationOtp);
        var rows = jdbc.queryForList("select id,token_hash,otp_nonce,attempts from email_verification_tokens "
                + "where user_id=? and used_at is null and expires_at>? order by id desc limit 1 for update", user.getId(), now());
        if (rows.isEmpty()) throw invalidVerificationOtp();
        var row = rows.getFirst();
        long id = ((Number) row.get("id")).longValue();
        int attempts = ((Number) row.get("attempts")).intValue();
        if (attempts >= 5) throw invalidVerificationOtp();
        String expected = (String) row.get("token_hash");
        String actual = otpHash(user.getId(), (String) row.get("otp_nonce"), otp);
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(expected), HexFormat.of().parseHex(actual))) {
            jdbc.update("update email_verification_tokens set attempts=?,used_at=? where id=?", attempts + 1,
                    attempts + 1 >= 5 ? now() : null, id);
            throw invalidVerificationOtp();
        }
        jdbc.update("update email_verification_tokens set used_at=? where id=?", now(), id);
        if (jdbc.update("update users set email_verified=true, enabled=true where id=? and role='PLAYER' and email_verified=false", user.getId()) != 1)
            throw invalidVerificationOtp();
    }

    @Transactional
    public void resendVerification(String email, String address) {
        limit("verify:" + address, 5);
        requireMail();
        users.findByEmailIgnoreCase(normalize(email)).filter(user -> !user.isEmailVerified() && "PLAYER".equalsIgnoreCase(user.getRole()))
                .ifPresent(user -> {
                    if (recentTokens("email_verification_tokens", user.getId()) >= 3) return;
                    var recent = jdbc.queryForList("select created_at from email_verification_tokens where user_id=? order by id desc limit 1", LocalDateTime.class, user.getId());
                    if (!recent.isEmpty() && recent.getFirst().isAfter(now().minusSeconds(60))) return;
                    issueVerification(user);
                });
    }

    @Transactional
    public void forgotPassword(String email, String address) {
        limit("forgot:" + address, 5);
        requireMail();
        users.findByEmailIgnoreCase(normalize(email)).filter(user -> user.isEnabled() && "PLAYER".equalsIgnoreCase(user.getRole()))
                .ifPresent(user -> {
                    if (recentTokens("password_reset_tokens", user.getId()) >= 3) return;
                    var recent = jdbc.queryForList("select created_at from password_reset_tokens where user_id=? order by id desc limit 1", LocalDateTime.class, user.getId());
                    if (!recent.isEmpty() && recent.getFirst().isAfter(now().minusSeconds(60))) return;
                    String otp = String.format(Locale.ROOT, "%08d", RANDOM.nextInt(100_000_000));
                    String nonce = HexFormat.of().formatHex(randomBytes(16));
                    jdbc.update("update password_reset_tokens set used_at=? where user_id=? and used_at is null", now(), user.getId());
                    jdbc.update("insert into password_reset_tokens(user_id,token_hash,otp_nonce,expires_at,created_at) values(?,?,?,?,?)",
                            user.getId(), otpHash(user.getId(), nonce, otp), nonce, now().plusMinutes(10), now());
                    send(user.getEmail(), "Mã đặt lại mật khẩu PickleZone", "Mã xác minh của bạn: " + otp
                            + "\nMã có hiệu lực 10 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.");
                });
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public String verifyResetOtp(String email, String otp, String address) {
        limit("reset-verify:" + address, 20);
        if (otp == null || !otp.matches("[0-9]{8}")) throw invalidOtp();
        UserAccount user = users.findByEmailIgnoreCase(normalize(email))
                .filter(account -> account.isEnabled() && "PLAYER".equalsIgnoreCase(account.getRole()))
                .orElseThrow(PlayerAccountService::invalidOtp);
        var rows = jdbc.queryForList("select id,token_hash,otp_nonce,attempts from password_reset_tokens "
                + "where user_id=? and used_at is null and verified_at is null and expires_at>? order by id desc limit 1 for update",
                user.getId(), now());
        if (rows.isEmpty()) throw invalidOtp();
        var row = rows.getFirst();
        long id = ((Number) row.get("id")).longValue();
        int attempts = ((Number) row.get("attempts")).intValue();
        if (attempts >= 5) throw invalidOtp();
        String expected = (String) row.get("token_hash");
        String actual = otpHash(user.getId(), (String) row.get("otp_nonce"), otp);
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(expected), HexFormat.of().parseHex(actual))) {
            jdbc.update("update password_reset_tokens set attempts=?,used_at=? where id=?", attempts + 1,
                    attempts + 1 >= 5 ? now() : null, id);
            throw invalidOtp();
        }
        String ticket = token();
        jdbc.update("update password_reset_tokens set verified_at=?,reset_ticket_hash=?,reset_ticket_expires_at=? where id=?",
                now(), hash(ticket), now().plusMinutes(5), id);
        return ticket;
    }

    @Transactional
    public void resetPassword(String resetTicket, String password) {
        validatePassword(password);
        if (resetTicket == null || !resetTicket.matches("[A-Za-z0-9_-]{43}")) throw invalidResetTicket();
        var rows = jdbc.queryForList("select id,user_id from password_reset_tokens where reset_ticket_hash=? "
                + "and verified_at is not null and reset_ticket_expires_at>? and used_at is null for update", hash(resetTicket), now());
        if (rows.size() != 1) throw invalidResetTicket();
        Long userId = ((Number) rows.getFirst().get("user_id")).longValue();
        UserAccount user = users.findById(userId).orElseThrow(PlayerAccountService::invalidResetTicket);
        if (!user.isEnabled() || !"PLAYER".equalsIgnoreCase(user.getRole())) throw invalidResetTicket();
        jdbc.update("update users set password_hash=? where id=?", passwords.encode(password), userId);
        jdbc.update("update password_reset_tokens set used_at=? where user_id=? and used_at is null", now(), userId);
        sessions.getAllPrincipals().stream()
                .filter(principal -> principal instanceof org.springframework.security.core.userdetails.UserDetails details
                        && details.getUsername().equals(user.getUsername()))
                .forEach(principal -> sessions.getAllSessions(principal, false).forEach(session -> session.expireNow()));
    }

    private void issueVerification(UserAccount user) {
        String otp = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        String nonce = HexFormat.of().formatHex(randomBytes(16));
        jdbc.update("update email_verification_tokens set used_at=? where user_id=? and used_at is null", now(), user.getId());
        jdbc.update("insert into email_verification_tokens(user_id,token_hash,otp_nonce,expires_at,created_at) values(?,?,?,?,?)",
                user.getId(), otpHash(user.getId(), nonce, otp), nonce, now().plusMinutes(10), now());
        send(user.getEmail(), "Mã xác minh tài khoản PickleZone", "Mã xác minh tài khoản của bạn: " + otp
                + "\nMã có hiệu lực 10 phút. Nếu bạn không đăng ký, hãy bỏ qua email này.");
    }

    private int recentTokens(String table, Long userId) {
        return jdbc.queryForObject("select count(*) from " + table + " where user_id=? and created_at>?",
                Integer.class, userId, now().minusHours(1));
    }

    private void send(String to, String subject, String body) {
        JavaMailSender mail = requireMail();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try { mail.send(message); }
        catch (MailException ex) { throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE", "Chưa gửi được email. Vui lòng thử lại sau."); }
    }

    private JavaMailSender requireMail() {
        JavaMailSender mail = mailProvider.getIfAvailable();
        if (mail == null || mailHost.isBlank() || mailFrom.isBlank())
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE", "Dịch vụ email chưa được cấu hình.");
        return mail;
    }

    private void limit(String key, int maximum) {
        long time = System.currentTimeMillis();
        if (requestCounts.size() >= 10_000) {
            requestCounts.entrySet().removeIf(entry -> time - entry.getValue()[0] >= 3_600_000);
            if (requestCounts.size() >= 10_000 && !requestCounts.containsKey(key))
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Vui lòng thử lại sau.");
        }
        long[] state = requestCounts.compute(key, (ignored, current) -> {
            if (current == null || time - current[0] >= 3_600_000) return new long[]{time, 1};
            current[1]++;
            return current;
        });
        if (state[1] > maximum) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Bạn đã yêu cầu quá nhiều lần. Vui lòng thử lại sau.");
    }

    private static String normalize(String email) { return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT); }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private static String token() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static byte[] randomBytes(int size) { byte[] bytes = new byte[size]; RANDOM.nextBytes(bytes); return bytes; }
    private String otpHash(Long userId, String nonce, String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(otpSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((userId + ":" + nonce + ":" + otp).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static void validatePassword(String password) {
        if (password == null || password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw invalid("Mật khẩu cần ít nhất 10 ký tự và tối đa 72 byte.");
    }
    private static BusinessException invalid(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT", message); }
    private static BusinessException invalidVerificationOtp() { return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_VERIFICATION_OTP", "Mã xác minh không hợp lệ hoặc đã hết hạn."); }
    private static BusinessException invalidResetTicket() { return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_RESET", "Phiên đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu mã mới."); }
    private static BusinessException invalidOtp() { return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Mã xác minh không hợp lệ hoặc đã hết hạn."); }
}
