package com.pickleball.auth;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerAccountMigrationTest {
    @Test
    void playerAccountMigrationAddsEmailAndFaceLinkWithoutRemovingGuestAccess() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:player-migration-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var scripts = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V1__create_users_and_access.sql"),
                new ClassPathResource("db/migration/V17__create_elo.sql"),
                new ClassPathResource("db/migration/V18__player_accounts.sql"),
                new ClassPathResource("db/migration/V19__password_reset_otp.sql"),
                new ClassPathResource("db/migration/V20__email_verification_otp.sql"),
                new ClassPathResource("db/migration/V21__optional_elo_phone.sql"),
                new ClassPathResource("db/migration/V22__elo_face_registration_lock.sql"));
        scripts.execute(source);
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        assertEquals(0, jdbc.queryForObject("select count(elo_player_id) from users", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from guest_access_tokens", Integer.class));
    }
}
