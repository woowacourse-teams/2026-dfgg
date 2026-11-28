package dfgg.common.handler;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dfgg.common.exception.ChampionNotFoundException;
import dfgg.common.exception.NextItemRecommendationNotFoundException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 4xx가 왜 났는지 로그만 보고 알 수 있어야 한다.
 *
 * <p>실제로 prod에서 `status=400`이 열여섯 건 찍혔는데 사유가 어디에도 없었다.
 * 검증 실패 메시지는 응답 본문에만 들어가고 로그에는 상태코드만 남았다.
 * 게다가 400은 경로가 둘이라(Bean 검증 / 챔피언 조회 실패) 소요 시간으로도 구분되지 않는다.
 */
class GlobalExceptionHandlerLoggingTest {

    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger logger;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private List<ILoggingEvent> events() {
        return appender.list;
    }

    @Test
    @DisplayName("잘못된 요청은 사유를 WARN으로 남긴다")
    void handleBadRequest_WhenChampionNotFound_LogReasonAtWarn() {
        // given
        ChampionNotFoundException exception = new ChampionNotFoundException("없는챔피언");

        // when
        handler.handleBadRequest(exception);

        // then
        assertThat(events()).hasSize(1);
        ILoggingEvent event = events().getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .as("로그만 보고 원인을 알 수 있어야 한다")
                .contains("400")
                .contains(exception.getMessage());
    }

    @Test
    @DisplayName("클라이언트 잘못에는 스택트레이스를 남기지 않는다")
    void handleBadRequest_WhenClientError_OmitStackTrace() {
        // when
        handler.handleBadRequest(new ChampionNotFoundException("없는챔피언"));

        // then
        assertThat(events().getFirst().getThrowableProxy())
                .as("4xx는 서버 결함이 아니다. 스택을 남기면 진짜 오류가 묻힌다")
                .isNull();
    }

    @Test
    @DisplayName("추천 결과 없음도 사유를 남긴다")
    void handleNotFound_WhenNoRecommendation_LogReasonAtWarn() {
        // given
        NextItemRecommendationNotFoundException exception =
                new NextItemRecommendationNotFoundException("야스오", "MID");

        // when
        handler.handleNotFound(exception);

        // then
        assertThat(events()).hasSize(1);
        assertThat(events().getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(events().getFirst().getFormattedMessage())
                .contains("404")
                .contains(exception.getMessage());
    }
}
