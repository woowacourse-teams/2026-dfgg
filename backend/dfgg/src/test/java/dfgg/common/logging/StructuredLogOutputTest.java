package dfgg.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class StructuredLogOutputTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggerContext loggerContext = new LoggerContext();

    @Test
    @DisplayName("파일 로그 한 줄은 JSON 객체 하나이고 추적 번호와 슬롯을 최상위 필드로 가진다")
    void encode_WhenFileFormatConfigured_WriteOneJsonObjectPerLine() throws IOException {
        // given
        LoggingEvent event = event(
                "method=GET uri=/api/champions status=200 duration_ms=3",
                null,
                Map.of(RequestLoggingFilter.TRACE_ID, "0f56051d"));

        // when
        String line = encode(event);

        // then
        assertThat(line.stripTrailing()).doesNotContain("\n");
        JsonNode json = objectMapper.readTree(line);
        assertThat(json.get("@timestamp").asText()).isNotBlank();
        assertThat(json.get("level").asText()).isEqualTo("INFO");
        assertThat(json.get("message").asText()).isEqualTo("method=GET uri=/api/champions status=200 duration_ms=3");
        assertThat(json.get("traceId").asText()).isEqualTo("0f56051d");
        assertThat(json.get("slot").asText()).isEqualTo("local");
    }

    @Test
    @DisplayName("key-value로 넣은 Map은 중첩 객체로, 숫자는 숫자로 남는다")
    void encode_WhenKeyValueIsMapOrNumber_KeepJsonType() throws IOException {
        // given
        LoggingEvent event = event("method=POST uri=/api/df-score status=200 duration_ms=4", null, Map.of());
        event.addKeyValuePair(new KeyValuePair("status", 200));
        event.addKeyValuePair(new KeyValuePair("req", Map.of("queueId", 420)));

        // when
        JsonNode json = objectMapper.readTree(encode(event));

        // then
        assertThat(json.get("status").isNumber()).isTrue();
        assertThat(json.get("req").isObject()).isTrue();
        assertThat(json.get("req").get("queueId").asInt()).isEqualTo(420);
    }

    @Test
    @DisplayName("예외 스택 트레이스는 여러 줄로 쪼개지지 않고 같은 줄의 stack_trace에 들어간다")
    void encode_WhenThrowableAttached_KeepStackTraceInSameLine() throws IOException {
        // given
        LoggingEvent event = event("처리 중 예외", new IllegalStateException("깨짐"), Map.of());

        // when
        String line = encode(event);

        // then
        assertThat(line.stripTrailing()).doesNotContain("\n");
        assertThat(objectMapper.readTree(line).get("stack_trace").asText())
                .contains("IllegalStateException: 깨짐");
    }

    // 테스트용 LoggerContext에는 MDC 어댑터가 없어서 MDC를 이벤트에 직접 넣는다.
    private LoggingEvent event(String message, Throwable throwable, Map<String, String> mdc) {
        LoggingEvent event = new LoggingEvent(
                StructuredLogOutputTest.class.getName(),
                loggerContext.getLogger("dfgg.test"),
                Level.INFO,
                message,
                throwable,
                null);
        event.setMDCPropertyMap(mdc);
        return event;
    }

    // 서버와 같은 application.yml 설정으로 인코더를 만들어, 설정과 출력 형식을 함께 확인한다.
    private String encode(LoggingEvent event) throws IOException {
        Environment environment = environmentFromApplicationYml();
        loggerContext.putObject(Environment.class.getName(), environment);
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(loggerContext);
        encoder.setFormat(environment.getProperty("logging.structured.format.file"));
        encoder.start();
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }

    private Environment environmentFromApplicationYml() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
