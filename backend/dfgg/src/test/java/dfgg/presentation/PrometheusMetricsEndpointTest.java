package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prometheus가 30초마다 긁어갈 엔드포인트. 대시보드의 p95와 5xx 비율이 전부 여기서 나온다.
 *
 * <p>버킷 검증이 이 테스트의 핵심이다. 히스토그램을 켜지 않으면 엔드포인트는 200을 돌려주고
 * 지표도 나오지만 {@code _bucket} 계열만 빠진다. 그러면 {@code histogram_quantile}이 쓸 값이 없어
 * <b>대시보드의 p95 패널만 조용히 비어 있게 된다.</b> 배포하고 한참 뒤에야 알게 되는 종류의 실패다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PrometheusMetricsEndpointTest {

    private static final Pattern URI_LABEL = Pattern.compile("uri=\"([^\"]*)\"");

    /** 실측 47개(하한 10ms·상한 10s 기준). 여유를 조금 두되, 상·하한이 사라지면 잡히도록 한다. */
    private static final int MAX_BUCKETS_PER_REQUEST_SERIES = 55;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    private String scrape() {
        return given()
                .when().get("/actuator/prometheus")
                .then().statusCode(200)
                .extract().asString();
    }

    @Test
    @DisplayName("스크랩하면 JVM과 커넥션 풀 지표가 나온다")
    void scrape_WhenApplicationRunning_ExposeJvmAndConnectionPoolMetrics() {
        // when
        String body = scrape();

        // then
        assertThat(body)
                .contains("jvm_memory_used_bytes")
                .contains("hikaricp_connections");
    }

    @Test
    @DisplayName("요청을 처리하면 지연 히스토그램이 le 라벨을 단 버킷으로 나온다")
    void scrape_AfterHandlingRequest_ExposeLatencyHistogramBuckets() {
        // given
        given().accept(ContentType.JSON).when().get("/api/champions").then().statusCode(200);

        // when
        String body = scrape();

        // then
        assertThat(body)
                .as("p95는 버킷에서 계산된다. 버킷이 없으면 대시보드가 조용히 빈다")
                .contains("http_server_requests_seconds_bucket")
                .contains("le=\"");
        assertThat(body).contains("uri=\"/api/champions\"");
    }

    @Test
    @DisplayName("매핑되지 않은 경로는 경로마다 시계열을 만들지 않는다")
    void scrape_WhenUnmatchedPathRequested_NotCreateSeriesPerPath() {
        // given
        String unmatched = "zzz-cardinality-probe-" + System.nanoTime();
        given().when().get("/api/champions/" + unmatched).then().statusCode(404);

        // when
        Set<String> uriLabels = uriLabelsOf(scrape());

        // then
        assertThat(uriLabels)
                .as("요청 경로가 그대로 라벨이 되면 스캐너 한 번에 시계열이 수천 개로 늘어난다")
                .noneMatch(label -> label.contains(unmatched));
        assertThat(uriLabels)
                .as("매핑 없는 요청은 정해진 라벨 하나로 접혀야 한다")
                .containsAnyOf("NOT_FOUND", "/**", "UNKNOWN");
    }

    @Test
    @DisplayName("지연 히스토그램의 버킷 수가 예산 안에 있다")
    void scrape_WhenLatencyRecorded_KeepBucketCountWithinBudget() {
        // given
        given().accept(ContentType.JSON).when().get("/api/champions").then().statusCode(200);

        // when
        long buckets = scrape().lines()
                .filter(line -> line.startsWith("http_server_requests_seconds_bucket{"))
                .filter(line -> line.contains("uri=\"/api/champions\""))
                .count();

        // then
        assertThat(buckets)
                .as("버킷 하나가 시계열 하나다. 상·하한을 지우면 70개가 넘고, 경로·상태코드 조합마다 곱해진다")
                .isPositive()
                .isLessThanOrEqualTo(MAX_BUCKETS_PER_REQUEST_SERIES);
    }

    private Set<String> uriLabelsOf(String body) {
        Matcher matcher = URI_LABEL.matcher(body);
        Set<String> labels = new HashSet<>();
        while (matcher.find()) {
            labels.add(matcher.group(1));
        }
        return labels;
    }
}
