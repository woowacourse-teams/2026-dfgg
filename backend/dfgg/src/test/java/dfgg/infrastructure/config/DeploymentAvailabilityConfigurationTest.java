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
                .asString()
                .as("CD의 readiness 검사가 이 노출에 의존한다")
                .contains("health")
                .as("무엇을 더 열든 통째로 열지는 않는다. env·heapdump까지 열리면 내부 정보가 샌다")
                .doesNotContain("*");
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
