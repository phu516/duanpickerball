package com.pickleball.setting;

import tools.jackson.databind.ObjectMapper;
import com.pickleball.setting.controller.HeroBannerController;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HeroBannerIntegrationTest {
    private HeroBannerController controller;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:hero-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        try (var stream = getClass().getClassLoader().getResourceAsStream("db/migration/V14__create_hero_banner.sql")) {
            assertNotNull(stream);
            jdbc.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        controller = new HeroBannerController(jdbc, mapper);
    }

    @Test
    void controllerUsesTheObjectMapperProvidedBySpringBoot() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withBean(JdbcTemplate.class, () -> new JdbcTemplate(new DriverManagerDataSource(
                        "jdbc:h2:mem:hero-context;MODE=MySQL", "sa", "")))
                .withBean(HeroBannerController.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(HeroBannerController.class));
                });
    }

    @Test
    void bannerPublishesOnlyWithinItsWindowAndKeepsImageWhenEditing() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        MockMultipartFile image = new MockMultipartFile("image", "event.png", "image/png", output.toByteArray());

        HeroBannerController.Details active = new HeroBannerController.Details(
                "PickleZone Open", "Weekend tournament", now.plusDays(2).toString(),
                now.minusHours(1).toString(), now.plusDays(3).toString(), "https://example.com/event", true);
        MockMvcBuilders.standaloneSetup(controller).build().perform(multipart("/api/v1/admin/hero/banner")
                .file(new MockMultipartFile("details", "", "text/plain",
                        mapper.writeValueAsBytes(active))).file(image)).andExpect(status().isOk());
        assertEquals(HttpStatus.OK, controller.publicBanner().getStatusCode());
        assertEquals("PickleZone Open", controller.publicBanner().getBody().title());
        assertArrayEquals(output.toByteArray(), controller.image().getBody());

        HeroBannerController.Details hidden = new HeroBannerController.Details(
                active.title(), active.summary(), active.eventStartAt(),
                active.visibleFrom(), active.visibleUntil(), active.linkUrl(), false);
        controller.save(mapper.writeValueAsString(hidden), null);
        assertEquals(HttpStatus.NO_CONTENT, controller.publicBanner().getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.image().getStatusCode());
        assertArrayEquals(output.toByteArray(), controller.adminImage().getBody());

        HeroBannerController.Details expired = new HeroBannerController.Details(
                active.title(), active.summary(), active.eventStartAt(),
                now.minusDays(3).toString(), now.minusDays(1).toString(), active.linkUrl(), true);
        controller.save(mapper.writeValueAsString(expired), null);
        assertEquals(HttpStatus.NO_CONTENT, controller.publicBanner().getStatusCode());
        assertNotNull(controller.adminBanner().getBody());
    }
}
