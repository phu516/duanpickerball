package com.pickleball.config;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Cookie defaults for HTTPS services hosted on separate Render origins. */
public class RenderCookieDefaults implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if ("true".equalsIgnoreCase(environment.getProperty("RENDER"))) {
            environment.getPropertySources().addLast(new MapPropertySource("renderCookieDefaults", Map.of(
                    "PICKLE_COOKIE_SAME_SITE", "none",
                    "PICKLE_COOKIE_SECURE", "true")));
        }
    }
}
