package dfgg.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 로그 출력 설정은 서버에서만 파일을 만들고, 디스크를 채우지 않아야 한다.
 * 잘못되면 개발자 노트북에 로그 파일이 생기거나 운영 디스크가 로그로 가득 찬다.
 */
class LoggingConfigurationTest {

    private final PropertySource<?> applicationProperties = loadApplicationProperties();

    @Test
    @DisplayName("로그 파일 경로는 환경변수에서 오고, 없으면 파일을 만들지 않는다")
    void logFile_WhenEnvironmentVariableAbsent_ProduceNoFile() {
        assertThat(property("logging.file.name"))
                .asString()
                .as("로컬·테스트에서는 환경변수가 없다. 빈 값이면 스프링이 파일을 만들지 않는다")
                .isEqualTo("${LOGGING_FILE_NAME:}");
    }

    @Test
    @DisplayName("회전 정책이 디스크 사용량을 묶어 둔다")
    void rollingPolicy_WhenConfigured_CapDiskUsage() {
        assertThat(property("logging.logback.rollingpolicy.max-file-size")).isEqualTo("100MB");
        assertThat(property("logging.logback.rollingpolicy.max-history")).isEqualTo(30);
        assertThat(property("logging.logback.rollingpolicy.total-size-cap"))
                .as("상한이 없으면 로그가 디스크를 채우고 앱이 함께 죽는다")
                .isEqualTo("1GB");
    }

    @Test
    @DisplayName("로그 한 줄에 추적 번호와 슬롯이 들어간다")
    void pattern_WhenConfigured_CarryTraceIdAndSlot() {
        for (String key : new String[] {"logging.pattern.console", "logging.pattern.file"}) {
            assertThat(property(key))
                    .asString()
                    .as("%s: 지표에서 본 이상을 로그에서 찾아가는 연결고리다", key)
                    .contains("%X{traceId")
                    .as("%s: 배포마다 blue/green이 바뀐다. 어느 슬롯이 남긴 줄인지 보여야 한다", key)
                    .contains("${DFGG_SLOT:");
        }
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
