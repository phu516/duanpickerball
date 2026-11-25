package com.pickleball.auth.controller;

import com.pickleball.auth.repository.UserAccountRepository;
import com.pickleball.auth.service.PlayerAccountService;
import com.pickleball.elo.EloController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class PlayerAuthController {
    private final PlayerAccountService accounts;
    private final UserAccountRepository users;
    private final JdbcTemplate jdbc;

    public PlayerAuthController(PlayerAccountService accounts, UserAccountRepository users, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.users = users;
        this.jdbc = jdbc;
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        var account = users.findByUsername(authentication.getName()).orElseThrow();
        Map<String, Object> result = new HashMap<>();
        result.put("username", account.getUsername());
        result.put("fullName", account.getFullName() == null ? "" : account.getFullName());
        result.put("phone", account.getPhone() == null ? "" : account.getPhone());
        result.put("role", "PLAYER");

        Long playerId = account.getEloPlayerId();
        if (playerId == null && account.getPhone() != null && !account.getPhone().isBlank()) {
            String phone = account.getPhone().trim();
            String localPhone = phone.startsWith("+84") ? "0" + phone.substring(3) : phone;
            String internationalPhone = localPhone.startsWith("0") ? "+84" + localPhone.substring(1) : localPhone;
            playerId = jdbc.query("select id from elo_players where phone in (?,?) order by id limit 1",
                    rs -> rs.next() ? rs.getLong(1) : null, localPhone, internationalPhone);
        }
        Map<String, Object> elo = null;
        if (playerId != null) {
            Long linkedPlayerId = playerId;
            elo = jdbc.query("select id,full_name,rating,games_played from elo_players where id=?", rs -> {
                if (!rs.next()) return null;
                int rating = rs.getInt("rating");
                return Map.of("id", rs.getLong("id"), "fullName", rs.getString("full_name"),
                        "rating", rating, "gamesPlayed", rs.getInt("games_played"),
                        "rank", EloController.rankFor(rating));
            }, linkedPlayerId);
        }
        result.put("elo", elo);
        return result;
    }

    @PostMapping("/register")
    public Map<String, String> register(@RequestBody Registration request, HttpServletRequest http) {
        accounts.register(request.username(), request.fullName(), request.phone(), request.email(), request.password(), http.getRemoteAddr());
        return Map.of("message", "Đã gửi mã xác minh đến email của bạn.");
    }

    @PostMapping("/verify-email")
    public Map<String, String> verify(@RequestBody EmailOtp request, HttpServletRequest http) {
        accounts.verifyEmail(request.email(), request.otp(), http.getRemoteAddr());
        return Map.of("message", "Tài khoản đã được xác minh. Bạn có thể đăng nhập.");
    }

    @PostMapping("/resend-verification")
    public Map<String, String> resend(@RequestBody Email request, HttpServletRequest http) {
        accounts.resendVerification(request.email(), http.getRemoteAddr());
        return Map.of("message", "Nếu email hợp lệ, chúng tôi đã gửi mã xác minh vào hộp thư.");
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgot(@RequestBody Email request, HttpServletRequest http) {
        accounts.forgotPassword(request.email(), http.getRemoteAddr());
        return Map.of("message", "Nếu email hợp lệ, chúng tôi đã gửi mã xác minh vào hộp thư.");
    }

    @PostMapping("/verify-reset-otp")
    public Map<String, String> verifyResetOtp(@RequestBody EmailOtp request, HttpServletRequest http) {
        return Map.of("resetToken", accounts.verifyResetOtp(request.email(), request.otp(), http.getRemoteAddr()));
    }

    @PostMapping("/reset-password")
    public Map<String, String> reset(@RequestBody Reset request) {
        accounts.resetPassword(request.resetToken(), request.password());
        return Map.of("message", "Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
    }

    private Map<String, String> genericMailResponse() {
        return Map.of("message", "Nếu email hợp lệ, chúng tôi sẽ gửi hướng dẫn vào hộp thư.");
    }

    public record Registration(String username, String fullName, String phone, String email, String password) {}
    public record Email(String email) {}
    public record EmailOtp(String email, String otp) {}
    public record Reset(String resetToken, String password) {}
}
