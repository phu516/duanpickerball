package com.pickleball.elo;

import java.util.Arrays;
import java.util.UUID;
import com.pickleball.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;

class EloControllerTest {
    private JdbcTemplate jdbc;
    private EloController controller;
    private final double[] face = new double[128];
    private final Authentication playerAuth = new UsernamePasswordAuthenticationToken("player1", "", java.util.List.of());

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:elo-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table tournament_registrations (id bigint auto_increment primary key, full_name varchar(120), phone varchar(16))");
        jdbc.execute("create table elo_players (id bigint auto_increment primary key, full_name varchar(120), phone varchar(16) unique, experience varchar(24), pickleball_months int, other_sport varchar(120), face_embedding text, rating int, games_played int, created_at timestamp)");
        jdbc.execute("create table elo_matches (id bigint auto_increment primary key, player_a_id bigint, player_b_id bigint, status varchar(20), score_a int, score_b int, created_at timestamp, confirmed_at timestamp)");
        jdbc.execute("create table elo_face_registration_lock (id int primary key)");
        jdbc.update("insert into elo_face_registration_lock(id) values(1)");
        jdbc.execute("create table users (id bigint auto_increment primary key, username varchar(80), full_name varchar(120), phone varchar(16), role varchar(20), enabled boolean, elo_player_id bigint unique)");
        jdbc.update("insert into users(username,full_name,phone,role,enabled) values('player1','Nguyễn Văn A','0900000001','PLAYER',true)");
        jdbc.update("insert into users(username,full_name,phone,role,enabled) values('player2','Nguyễn Văn B','0900000002','PLAYER',true)");
        var verifier = org.mockito.Mockito.mock(LiveFaceVerifier.class);
        org.mockito.Mockito.when(verifier.consume(org.mockito.Mockito.anyString(), org.mockito.Mockito.eq("test-proof"))).thenReturn(face);
        double[] secondFace = new double[128]; secondFace[0] = 1;
        org.mockito.Mockito.when(verifier.consume(org.mockito.Mockito.anyString(), org.mockito.Mockito.eq("second-proof"))).thenReturn(secondFace);
        controller = new EloController(jdbc, verifier);
        Arrays.fill(face, 0.1);
    }

    @Test
    void newPlayerWithoutTournamentCanBeRegisteredAndIdentified() {
        var request = new EloController.Registration("A", "0900000001", "NEW", null, null, "test-proof");
        var player = controller.register(request);
        assertEquals(800, player.rating());
        assertEquals("BRONZE", player.rank());
        assertEquals(1, controller.players().size());
        assertEquals(player.id(), controller.identify(new EloController.Face("test-proof"), playerAuth).player().id());
        assertTrue(controller.registrants().isEmpty());
    }

    @Test
    void playerCanCreateOwnEloAndCannotCreateSecondProfile() {
        var registered = controller.selfRegister(new EloController.SelfRegistration("NEW", "test-proof"), playerAuth);
        assertEquals(800, registered.player().rating());
        assertEquals(registered.player().id(), jdbc.queryForObject("select elo_player_id from users where username='player1'", Long.class));
        assertEquals(registered.player().id(), controller.queue(new EloController.QueueRequest(registered.token()), playerAuth).playerId());
        assertThrows(BusinessException.class, () -> controller.selfRegister(new EloController.SelfRegistration("NEW", "test-proof"), playerAuth));
        assertEquals(1, controller.players().size());
    }

    @Test
    void playerWithoutPhoneCanScanAndCreateElo() {
        jdbc.update("update users set phone=null where username='player1'");
        var registered = controller.selfRegister(new EloController.SelfRegistration("NEW", "test-proof"), playerAuth);
        assertNull(registered.player().phone());
        assertEquals(registered.player().id(), jdbc.queryForObject("select elo_player_id from users where username='player1'", Long.class));
    }

    @Test
    void existingFaceWithSamePhoneIsLinkedWithoutCreatingDuplicate() {
        var existing = controller.register(new EloController.Registration("Nguyễn Văn A", "0900000001", "NEW", null, null, "test-proof"));
        var linked = controller.selfRegister(new EloController.SelfRegistration("SPORT", "test-proof"), playerAuth);
        assertEquals(existing.id(), linked.player().id());
        assertEquals(1, controller.players().size());
    }

    @Test
    void adminCannotCreateOrReassignDuplicateFace() {
        var first = controller.register(new EloController.Registration("A", "0900000001", "NEW", null, null, "test-proof"));
        assertThrows(BusinessException.class, () -> controller.register(
                new EloController.Registration("B", "0900000002", "NEW", null, null, "test-proof")));
        var second = controller.register(new EloController.Registration("B", "0900000002", "NEW", null, null, "second-proof"));
        assertThrows(BusinessException.class, () -> controller.enroll(second.id(), new EloController.Face("test-proof")));
        assertEquals(2, controller.players().size());
        assertNotEquals(first.id(), second.id());
    }

    @Test
    void onlyOwnerCanAccessQueueAndFaceProfileCannotBeClaimedTwice() {
        var player = controller.register(new EloController.Registration("A", "0900000001", "NEW", null, null, "test-proof"));
        var verification = controller.identify(new EloController.Face("test-proof"), playerAuth);
        var ticket = controller.queue(new EloController.QueueRequest(verification.token()), playerAuth);
        var other = new UsernamePasswordAuthenticationToken("player2", "", java.util.List.of());
        assertThrows(BusinessException.class, () -> controller.poll(ticket.ticketId(), other));
        assertThrows(BusinessException.class, () -> controller.accept(ticket.ticketId(), other));
        assertThrows(BusinessException.class, () -> controller.cancel(ticket.ticketId(), other));
        assertThrows(BusinessException.class, () -> controller.identify(new EloController.Face("test-proof"), other));
        assertEquals(player.id(), controller.poll(ticket.ticketId(), playerAuth).playerId());
    }

    @Test
    void rejectsInvalidNameAndPhoneBeforeSaving() {
        assertThrows(IllegalArgumentException.class, () -> controller.register(
                new EloController.Registration("123", "0900000001", "NEW", null, null, "test-proof")));
        assertThrows(IllegalArgumentException.class, () -> controller.register(
                new EloController.Registration("Nguyễn Văn A", "090000000", "NEW", null, null, "test-proof")));
        assertTrue(controller.players().isEmpty());
    }

    @Test
    void treatsLocalAndInternationalPhoneAsSamePlayer() {
        controller.register(new EloController.Registration("Nguyễn Văn A", "+84900000001", "NEW", null, null, "test-proof"));
        assertEquals("0900000001", controller.players().getFirst().phone());
        assertThrows(BusinessException.class, () -> controller.register(
                new EloController.Registration("Nguyễn Văn B", "0900000001", "NEW", null, null, "test-proof")));
    }

    @Test
    void adminScoreUpdatesBothPlayersFromTheirInitialAssessment() {
        var a = controller.register(new EloController.Registration("A", "0900000001", "NEW", null, null, "test-proof"));
        var b = controller.register(new EloController.Registration("B", "0900000002", "NEW", null, null, "second-proof"));
        var match = controller.createMatch(new EloController.Pair(a.id(), b.id()));
        controller.score(match.id(), new EloController.Score(11, 9));
        assertEquals(816, controller.players().stream().filter(p -> p.id() == a.id()).findFirst().orElseThrow().rating());
        assertEquals(784, controller.players().stream().filter(p -> p.id() == b.id()).findFirst().orElseThrow().rating());
        assertEquals("COMPLETED", controller.matches().getFirst().status());
    }

    @Test
    void rankChangesAtEveryEloBoundary() {
        assertEquals("BRONZE", EloController.rankFor(899));
        assertEquals("SILVER", EloController.rankFor(900));
        assertEquals("GOLD", EloController.rankFor(1000));
        assertEquals("PLATINUM", EloController.rankFor(1100));
        assertEquals("DIAMOND", EloController.rankFor(1200));
        assertEquals("MASTER", EloController.rankFor(1300));
        assertEquals("CHALLENGER", EloController.rankFor(1400));
        assertEquals("CHALLENGER", EloController.rankFor(2000));
    }

    @Test
    void completedMatchUpdatesRankWithElo() {
        var a = controller.register(new EloController.Registration("A", "0900000001", "SPORT", null, "Bóng đá", "test-proof"));
        var b = controller.register(new EloController.Registration("B", "0900000002", "SPORT", null, "Bóng đá", "second-proof"));
        var match = controller.createMatch(new EloController.Pair(a.id(), b.id()));
        controller.score(match.id(), new EloController.Score(9, 11));
        assertEquals("BRONZE", controller.players().stream().filter(p -> p.id() == a.id()).findFirst().orElseThrow().rank());
        assertEquals("SILVER", controller.players().stream().filter(p -> p.id() == b.id()).findFirst().orElseThrow().rank());
    }
}
