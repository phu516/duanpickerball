package com.pickleball.config;

import com.pickleball.auth.repository.UserAccountRepository;
import com.pickleball.common.response.ApiError;
import jakarta.servlet.DispatcherType;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    public SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() { return new HttpSessionEventPublisher(); }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public UserDetailsService userDetailsService(UserAccountRepository users) {
        return username -> users.findByUsername(username)
                .map(account -> User.withUsername(account.getUsername())
                        .password(account.getPasswordHash())
                        .roles(normalizeRole(account.getRole()))
                        .disabled(!account.isEnabled()).build())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(username));
    }

    private static String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring("ROLE_".length()) : normalized;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper, SessionRegistry sessions,
                                                   @Value("${app.frontend-origin}") String origin,
                                                   @Value("${server.servlet.session.cookie.same-site:lax}") String cookieSameSite,
                                                   @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) throws Exception {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(java.util.List.of(origin));
        cors.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(java.util.List.of("Content-Type", "X-XSRF-TOKEN", "X-Guest-Access-Token", "Idempotency-Key"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource corsSource = new UrlBasedCorsConfigurationSource();
        corsSource.registerCorsConfiguration("/**", cors);

        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokens.setCookieCustomizer(cookie -> {
            cookie.sameSite(cookieSameSite);
            if (secureCookies) cookie.secure(true);
        });

        http.cors(c -> c.configurationSource(corsSource))
                .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions))
                .csrf(c -> c.csrfTokenRepository(csrfTokens)
                        .ignoringRequestMatchers("/api/v1/webhooks/payos"))
                .authorizeHttpRequests(auth -> auth
                        // Render the original HTTP error instead of replacing it
                        // with a 403 when the servlet container dispatches to /error.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Keep already-authenticated sessions working if their authority was
                        // created from a lowercase legacy role before normalizeRole was applied.
                        .requestMatchers("/api/v1/admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_admin")
                        .requestMatchers("/api/v1/staff/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/v1/auth/me", "/api/v1/elo/**").hasRole("PLAYER")
                        .requestMatchers("/api/v1/**").permitAll()
                        .anyRequest().denyAll())
                .formLogin(form -> form.loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> {
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(response.getWriter(), Map.of("username", authentication.getName()));
                        })
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(401);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(response.getWriter(), ApiError.of("LOGIN_FAILED", "Sai tài khoản hoặc mật khẩu"));
                        }))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(response.getWriter(), ApiError.of("LOGIN_REQUIRED", "Cần đăng nhập"));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            if (exception instanceof CsrfException) {
                                mapper.writeValue(response.getWriter(), ApiError.of("CSRF_INVALID",
                                        "Không duy trì được phiên bảo mật. Vui lòng tải lại trang và thử lại."));
                            } else {
                                mapper.writeValue(response.getWriter(), ApiError.of("ACCESS_DENIED", "Không đủ quyền"));
                            }
                        }));
        return http.build();
    }
}
