package com.attendance.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayProfileConfigurationTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void baselineOnMigrateIsDisabledForCommonReleaseAndIntegrationProfiles() throws IOException {
        assertThat(property("application.yml", "spring.flyway.baseline-on-migrate")).isEqualTo(false);
        assertThat(property("application-release.yml", "spring.flyway.baseline-on-migrate")).isEqualTo(false);
        assertThat(property("application-integration.yml", "spring.flyway.baseline-on-migrate")).isEqualTo(false);
    }

    @Test
    void integrationProfileDoesNotContainUnsupportedAutoRepairSettings() throws IOException {
        assertThat(property("application-integration.yml", "spring.flyway.clean-on-validation-error")).isNull();
        assertThat(property("application-integration.yml", "spring.flyway.repair-on-migrate")).isNull();
    }

    private Object property(String resourceName, String propertyName) throws IOException {
        Path resourcePath = resourceName.equals("application-integration.yml")
                ? Path.of("src", "test", "resources", resourceName)
                : Path.of("src", "main", "resources", resourceName);
        String yaml = Files.readString(resourcePath).replace("@spring.profiles.active@", "local");
        ByteArrayResource resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8), resourceName);
        List<PropertySource<?>> sources = loader.load(resourceName, resource);
        return sources.stream()
                .map(source -> source.getProperty(propertyName))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }
}
