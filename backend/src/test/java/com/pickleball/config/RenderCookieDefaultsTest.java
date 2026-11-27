package com.pickleball.config;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RenderCookieDefaultsTest {
    @Test
    void usesSecureCrossSiteCookiesOnRenderWithoutOverridingExplicitSettings() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "RENDER", "true", "PICKLE_COOKIE_SAME_SITE", "lax")));

        new RenderCookieDefaults().postProcessEnvironment(environment, null);

        assertEquals("lax", environment.getProperty("PICKLE_COOKIE_SAME_SITE"));
        assertEquals("true", environment.getProperty("PICKLE_COOKIE_SECURE"));
    }

    @Test
    void keepsLocalCookieDefaultsUnchanged() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of("RENDER", "false")));

        new RenderCookieDefaults().postProcessEnvironment(environment, null);

        assertFalse(environment.getPropertySources().contains("renderCookieDefaults"));
    }
}
