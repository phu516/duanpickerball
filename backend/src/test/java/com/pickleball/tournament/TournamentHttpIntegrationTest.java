package com.pickleball.tournament;

import com.pickleball.auth.entity.UserAccount;
import com.pickleball.auth.repository.UserAccountRepository;
import com.pickleball.auth.controller.AuthController;
import com.pickleball.common.exception.GlobalExceptionHandler;
import com.pickleball.config.SecurityConfig;
import com.pickleball.tournament.controller.TournamentController;
import com.pickleball.tournament.service.TournamentService;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

// Use a real servlet container: MockMvc alone does not reproduce error dispatches
// that can turn JSON parsing errors into misleading security responses.
@SpringBootTest(classes = TournamentHttpIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:tournament-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/migration/V1__create_users_and_access.sql,classpath:db/test/player_account_columns.sql,classpath:db/migration/V15__add_tournament_management.sql,classpath:db/migration/V16__add_tournament_times.sql",
        "app.frontend-origin=http://localhost:5173",
        "app.seed.enabled=false"
})
@ActiveProfiles({ "test", "tournament-http" })
class TournamentHttpIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @Profile("tournament-http")
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = UserAccount.class)
    @EnableJpaRepositories(basePackageClasses = UserAccountRepository.class)
    @Import({ SecurityConfig.class, AuthController.class, TournamentController.class,
            TournamentService.class, GlobalExceptionHandler.class })
    static class TestApplication {}

    @LocalServerPort int port;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired TournamentService tournaments;
    private HttpClient browser;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        browser = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(10)).build();
        today = LocalDate.now(TournamentService.LOCAL_ZONE);
        jdbc.update("delete from tournament_settings");
    }

    @AfterEach
    void closeBrowser() {
        browser.close();
    }

    @ParameterizedTest
    @ValueSource(strings = { "ADMIN", "admin", "ROLE_ADMIN" })
    void adminCanSaveExactlyTheDatesSentByTheForm(String role) throws Exception {
        login(role);
        HttpResponse<String> saved = save(dateBody(), csrf());
        assertEquals(200, saved.statusCode(), saved.body());
        JsonNode schedule = mapper.readTree(saved.body());
        assertEquals(today.plusDays(1).toString(), schedule.get("startsOn").asString());
        assertEquals("09:00:00", schedule.get("startsTime").asString());
        assertEquals("18:00:00", schedule.get("endsTime").asString());
        assertTrue(schedule.get("enabled").asBoolean());
        assertEquals("scheduled", schedule.path("status").asString());
        assertEquals(today.plusDays(1), tournaments.schedule().startsOn());
        assertTrue(tournaments.isBlocked(today.plusDays(1)));

        HttpResponse<String> loaded = send(request("/admin/tournament").GET().build());
        assertEquals(200, loaded.statusCode(), loaded.body());
        assertEquals(schedule, mapper.readTree(loaded.body()));

        HttpResponse<String> publicSchedule = send(request("/tournament/schedule").GET().build());
        assertEquals(200, publicSchedule.statusCode(), publicSchedule.body());
        assertEquals(schedule.get("startsOn"), mapper.readTree(publicSchedule.body()).get("startsOn"));
        assertEquals(schedule.get("startsTime"), mapper.readTree(publicSchedule.body()).get("startsTime"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void legacyEnabledFieldDoesNotPreventActivatingTheSchedule(boolean enabled) throws Exception {
        login("ADMIN");
        String legacyBody = dateBody().replace("}", ",\"enabled\":" + enabled + "}");
        HttpResponse<String> saved = save(legacyBody, csrf());
        assertEquals(200, saved.statusCode(), saved.body());
        assertEquals("scheduled", mapper.readTree(saved.body()).path("status").asString());
        assertTrue(tournaments.schedule().enabled());
    }

    @Test
    void malformedScheduleReturnsBadRequestInsteadOfPermissionDenied() throws Exception {
        login("ADMIN");
        HttpResponse<String> response = save("{\"startsOn\":\"invalid-date\"}", csrf());
        assertEquals(400, response.statusCode(), response.body());
        assertEquals("INVALID_REQUEST", mapper.readTree(response.body()).get("code").asString());
        assertNull(tournaments.schedule());
    }

    @Test
    void registrationRequiresAListedCategory() throws Exception {
        login("ADMIN");
        assertEquals(200, save(dateBody(), csrf()).statusCode());
        JsonNode token = csrf();

        HttpResponse<String> invalid = send(request("/tournament/registrations")
                .header("Content-Type", "application/json")
                .header(token.get("headerName").asString(), token.get("token").asString())
                .POST(HttpRequest.BodyPublishers.ofString("{\"fullName\":\"Nguyen Van A\",\"phone\":\"0901234567\",\"category\":\"Tự nhập\"}"))
                .build());
        assertEquals(400, invalid.statusCode(), invalid.body());

        HttpResponse<String> valid = send(request("/tournament/registrations")
                .header("Content-Type", "application/json")
                .header(token.get("headerName").asString(), token.get("token").asString())
                .POST(HttpRequest.BodyPublishers.ofString("{\"fullName\":\"Nguyen Van A\",\"phone\":\"0901234567\",\"category\":\"Đôi nam nữ\"}"))
                .build());
        assertEquals(201, valid.statusCode(), valid.body());
        assertEquals("Đôi nam nữ", mapper.readTree(valid.body()).get("category").asString());
    }

    @Test
    void missingEndDateReturnsValidationError() throws Exception {
        login("ADMIN");
        HttpResponse<String> response = save("{\"startsOn\":\"" + today + "\"}", csrf());
        assertEquals(400, response.statusCode(), response.body());
        assertEquals("INVALID_ARGUMENT", mapper.readTree(response.body()).get("code").asString());
        assertNull(tournaments.schedule());
    }

    @Test
    void staffCannotSaveTournamentEvenWithValidCsrf() throws Exception {
        login("STAFF");
        HttpResponse<String> response = save(dateBody(), csrf());
        assertEquals(403, response.statusCode(), response.body());
        assertEquals("ACCESS_DENIED", mapper.readTree(response.body()).get("code").asString());
        assertNull(tournaments.schedule());
    }

    @Test
    void anonymousVisitorCannotSaveTournamentEvenWithValidCsrf() throws Exception {
        HttpResponse<String> response = save(dateBody(), csrf());
        assertEquals(401, response.statusCode(), response.body());
        assertEquals("LOGIN_REQUIRED", mapper.readTree(response.body()).get("code").asString());
        assertNull(tournaments.schedule());
    }

    @Test
    void adminStillNeedsValidCsrfToSave() throws Exception {
        login("ADMIN");
        csrf();
        // An unrelated token is not accepted even with a valid admin session.
        JsonNode invalid = mapper.readTree("{\"headerName\":\"X-XSRF-TOKEN\",\"token\":\"invalid\"}");
        HttpResponse<String> response = save(dateBody(), invalid);
        assertEquals(403, response.statusCode(), response.body());
        assertEquals("CSRF_INVALID", mapper.readTree(response.body()).get("code").asString());
        assertNull(tournaments.schedule());
        assertEquals(200, save(dateBody(), csrf()).statusCode());
    }

    @Test
    void servletErrorsKeepTheirOriginalStatusWithoutOpeningDirectErrorRequests() throws Exception {
        login("ADMIN");
        HttpResponse<String> missing = send(request("/admin/tournament/not-found").GET().build());
        assertEquals(404, missing.statusCode(), missing.body());
        HttpResponse<String> directError = send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/error")).GET().build());
        assertEquals(403, directError.statusCode(), directError.body());
    }

    @Test
    void matchSearchRequiresLoginWhileGuestBookingPathDoesNot() throws Exception {
        HttpResponse<String> match = send(request("/elo/queue/missing").GET().build());
        assertEquals(401, match.statusCode(), match.body());
        HttpResponse<String> guest = send(request("/guest/bookings/missing").GET().build());
        assertEquals(404, guest.statusCode(), guest.body());
    }

    private void login(String role) throws Exception {
        String username = "test-" + UUID.randomUUID();
        String password = "tournament-test-password";
        users.save(new UserAccount(username, passwords.encode(password), role));
        JsonNode token = csrf();
        HttpResponse<String> login = send(request("/auth/login")
                .header(token.get("headerName").asString(), token.get("token").asString())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=" + username + "&password=" + password))
                .build());
        assertEquals(200, login.statusCode(), login.body());
    }

    private JsonNode csrf() throws Exception {
        HttpResponse<String> response = send(request("/auth/csrf").GET().build());
        assertEquals(200, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private HttpResponse<String> save(String body, JsonNode token) throws Exception {
        return send(request("/admin/tournament")
                .header("Content-Type", "application/json")
                .header(token.get("headerName").asString(), token.get("token").asString())
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private String dateBody() {
        return "{\"startsOn\":\"" + today.plusDays(1) + "\",\"startsTime\":\"09:00\",\"endsOn\":\"" + today.plusDays(2) + "\",\"endsTime\":\"18:00\"}";
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(15)).header("Origin", "http://localhost:5173");
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return browser.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
