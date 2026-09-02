package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class DeploymentAvailabilityConfigurationTest {

    private final PropertySource<?> applicationProperties = loadApplicationProperties();

    @Test
    void readinessProbeChecksApplicationAndDatabaseAvailability() {
        assertThat(property("management.endpoint.health.probes.enabled"))
                .isEqualTo(true);
        assertThat(property("management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,db");
        assertThat(property("management.endpoints.web.exposure.include"))
                .isEqualTo("health");
    }

    @Test
    void gracefulShutdownWaitsUpToThirtySeconds() {
        assertThat(property("server.shutdown"))
                .isEqualTo("graceful");
        assertThat(property("spring.lifecycle.timeout-per-shutdown-phase"))
                .isEqualTo("30s");
    }

    private Object property(String name) {
        return applicationProperties.getProperty(name);
    }

    private static PropertySource<?> loadApplicationProperties() {
        try {
            return new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"))
                    .getFirst();
        } catch (IOException exception) {
            throw new IllegalStateException("application.yml을 읽을 수 없습니다", exception);
        }
    }
}
