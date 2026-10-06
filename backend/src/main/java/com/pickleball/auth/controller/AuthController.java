package com.pickleball.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    @GetMapping("/auth/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/admin/me")
    public Map<String, String> me(Authentication authentication) {
        return Map.of("username", authentication.getName(), "role", authentication.getAuthorities()
                .stream().findFirst().map(authority -> authority.getAuthority().replace("ROLE_", "")).orElse("UNKNOWN"));
    }
}
