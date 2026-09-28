package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
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
}
