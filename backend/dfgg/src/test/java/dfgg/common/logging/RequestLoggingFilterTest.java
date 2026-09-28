package dfgg.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 요청 한 건이 로그 한 줄로 남는지 본다.
 *
 * <p>형식이 이 테스트의 핵심이다. CloudWatch Logs Insights의 {@code parse} 정규식이
 * {@code status=}·{@code duration_ms=}를 그대로 집어가므로, 키 이름이 바뀌면 대시보드 쿼리가
 * 조용히 빈 결과를 낸다. 형식과 쿼리는 한 쌍이다.
 */
class RequestLoggingFilterTest {

    private static final long NEVER_SLOW = Long.MAX_VALUE;
    private static final long ALWAYS_SLOW = 0L;

    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void setUp() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    private List<ILoggingEvent> events() {
        return appender.list;
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    @DisplayName("요청이 끝나면 메서드·경로·상태코드·소요시간을 한 줄로 남긴다")
    void doFilter_WhenRequestCompletes_LogMethodUriStatusAndDuration() throws Exception {
        // given
        RequestLoggingFilter filter = new RequestLoggingFilter(NEVER_SLOW);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // when
        filter.doFilter(request("POST", "/api/recommendations/v3"), response, new MockFilterChain());

        // then
        assertThat(events()).hasSize(1);
        String message = events().getFirst().getFormattedMessage();
        assertThat(message)
                .as("Insights의 parse 정규식이 이 키들을 집어간다")
                .contains("method=POST")
                .contains("uri=/api/recommendations/v3")
                .contains("status=200")
                .containsPattern("duration_ms=\\d+");
        assertThat(events().getFirst().getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    @DisplayName("요청마다 추적 식별자를 만들어 로그와 응답 헤더에 함께 싣는다")
    void doFilter_WhenRequestHandled_PublishTraceIdToLogAndResponseHeader() throws Exception {
        // given
        RequestLoggingFilter filter = new RequestLoggingFilter(NEVER_SLOW);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request("GET", "/api/champions"), response, new MockFilterChain());

        // then
        String traceId = events().getFirst().getMDCPropertyMap().get("traceId");
        assertThat(traceId)
                .as("지표에서 본 이상을 로그에서 찾아가는 연결고리다")
                .isNotBlank();
        assertThat(response.getHeader("X-Trace-Id"))
                .as("사용자가 겪은 오류를 로그에서 집으려면 클라이언트도 알아야 한다")
                .isEqualTo(traceId);
    }

    @Test
    @DisplayName("요청이 끝나면 MDC를 비운다")
    void doFilter_AfterRequest_ClearMdc() throws Exception {
        // given
        RequestLoggingFilter filter = new RequestLoggingFilter(NEVER_SLOW);

        // when
        filter.doFilter(request("GET", "/api/champions"), new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(MDC.get("traceId"))
                .as("스레드는 재사용된다. 안 비우면 다음 요청이 남의 추적 번호를 달고 나간다")
                .isNull();
    }

    @Test
    @DisplayName("actuator 요청은 남기지 않는다")
    void doFilter_WhenActuatorRequested_NotLog() throws Exception {
        // given
        RequestLoggingFilter filter = new RequestLoggingFilter(NEVER_SLOW);

        // when
        filter.doFilter(request("GET", "/actuator/prometheus"), new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(events())
                .as("Prometheus가 30초마다 긁는다. 남기면 하루 2,880줄이 노이즈로 쌓인다")
                .isEmpty();
    }

    @Test
    @DisplayName("기준 시간을 넘긴 요청은 WARN으로 남긴다")
    void doFilter_WhenRequestIsSlow_LogAtWarn() throws Exception {
        // given
        RequestLoggingFilter filter = new RequestLoggingFilter(ALWAYS_SLOW);

        // when
        filter.doFilter(request("POST", "/api/recommendations/v3"), new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(events().getFirst().getLevel())
                .as("느린 요청은 정상 요청 사이에 묻히면 안 된다")
                .isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("예외가 올라오면 스택트레이스와 함께 남기고 그대로 던진다")
    void doFilter_WhenChainThrows_LogStackTraceAndRethrow() {
        // given
        RequestLoggingFilter filter = new RequestLoggingFilter(NEVER_SLOW);
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("추천 파이프라인이 터졌다");
        };

        // when & then
        assertThatThrownBy(() -> filter.doFilter(
                request("POST", "/api/recommendations/v3"), new MockHttpServletResponse(), failing))
                .as("로깅이 응답 처리를 바꾸면 안 된다. 예외는 그대로 흘러가야 한다")
                .isInstanceOf(IllegalStateException.class);

        assertThat(events())
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getThrowableProxy())
                            .as("원인을 찾으려면 스택트레이스가 필요하다")
                            .isNotNull();
                });
    }

    @Test
    @DisplayName("예외가 나도 MDC를 비운다")
    void doFilter_WhenChainThrows_StillClearMdc() {
        // given
        RequestLoggingFilter filter = new RequestLoggingFilter(NEVER_SLOW);
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("터졌다");
        };

        // when
        try {
            filter.doFilter(request("GET", "/api/champions"), new MockHttpServletResponse(), failing);
        } catch (IllegalStateException | IOException | ServletException ignored) {
            // 예외 자체는 위 테스트가 본다
        }

        // then
        assertThat(MDC.get("traceId")).isNull();
    }
}
