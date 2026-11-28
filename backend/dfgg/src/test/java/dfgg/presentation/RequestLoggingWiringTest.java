package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dfgg.common.handler.GlobalExceptionHandler;
import dfgg.common.logging.RequestLoggingFilter;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * 요청 로그 필터가 실제로 앱에 붙어 있는지 본다.
 * 단위 테스트는 필터 자체만 보므로, 등록이 빠져도 통과한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RequestLoggingWiringTest {

    @LocalServerPort
    private int port;

    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        filterLogger.addAppender(appender);
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(appender);
        handlerLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("API 응답에 추적 번호가 실린다")
    void request_WhenApiCalled_CarryTraceIdHeader() {
        // when & then
        given().accept(ContentType.JSON)
                .when().get("/api/champions")
                .then().statusCode(200)
                .header("X-Trace-Id", matchesPattern("[0-9a-f]{8}"));
    }

    @Test
    @DisplayName("actuator 요청에는 추적 번호를 붙이지 않는다")
    void request_WhenActuatorCalled_SkipFilter() {
        // when & then
        given()
                .when().get("/actuator/health")
                .then().statusCode(200)
                .header("X-Trace-Id", nullValue());
    }

    @Test
    @DisplayName("본문 기록 경로는 실제 컨트롤러를 거친 요청·응답 본문을 남기고, 응답 본문은 그대로 나간다")
    void request_WhenBodyPathCalled_LogRequestAndResponseBody() {
        // when & then
        given().contentType(ContentType.JSON)
                .body("{\"puuid\":\"Ab3xQwErTy\"}")
                .when().post("/api/df-score")
                .then().statusCode(400)
                .body("status", equalTo(400));

        ILoggingEvent accessLine = appender.list.getLast();
        assertThat(keyValue(accessLine, "req")).isEqualTo(Map.of("puuid", "Ab3x…"));
        assertThat(keyValue(accessLine, "res"))
                .asInstanceOf(MAP)
                .containsEntry("status", 400);
    }

    @Test
    @DisplayName("설치 ID 헤더를 보내면 4xx 사유 줄과 access 줄에 같은 설치 ID와 추적 번호가 붙는다")
    void request_WhenInstallIdSent_TagWarnAndAccessLinesWithSameInstallId() {
        // when
        given().contentType(ContentType.JSON)
                .header("X-Install-Id", "3f2a9c1e-7b4d-4e2a-9c1e-5d6f7a8b9c0d")
                .header("X-Client-Version", "1.4.2")
                .body("{}")
                .when().post("/api/df-score")
                .then().statusCode(400);

        // then
        ILoggingEvent warnLine = appender.list.stream()
                .filter(event -> event.getLoggerName().equals(GlobalExceptionHandler.class.getName()))
                .findFirst()
                .orElseThrow();
        ILoggingEvent accessLine = appender.list.getLast();
        assertThat(warnLine.getMDCPropertyMap())
                .containsEntry("installId", "3f2a9c1e-7b4d-4e2a-9c1e-5d6f7a8b9c0d")
                .containsEntry("traceId", accessLine.getMDCPropertyMap().get("traceId"));
        assertThat(accessLine.getMDCPropertyMap())
                .containsEntry("installId", "3f2a9c1e-7b4d-4e2a-9c1e-5d6f7a8b9c0d");
        assertThat(keyValue(accessLine, "clientVersion")).isEqualTo("1.4.2");
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .map(pair -> pair.value)
                .findFirst()
                .orElse(null);
    }
}
