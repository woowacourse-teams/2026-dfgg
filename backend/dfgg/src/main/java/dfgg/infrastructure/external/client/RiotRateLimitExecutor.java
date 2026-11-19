package dfgg.infrastructure.external.client;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Riot API 요청을 순차적으로 실행하고 429 응답에 맞춰 재시도한다.
 *
 * <p>요청을 synchronized로 보호하고 platform/regional routing scope별로
 * 선제 호출 간격과 429 대기 상태를 관리한다.
 */
final class RiotRateLimitExecutor {

    private static final Logger log = LoggerFactory.getLogger(RiotRateLimitExecutor.class);
    private static final int DEFAULT_REQUESTS_PER_SECOND = 18;
    private static final int DEFAULT_REQUESTS_PER_TWO_MINUTES = 95;
    private static final Duration TWO_MINUTES = Duration.ofMinutes(2);

    private final Clock clock;
    private final Sleeper sleeper;
    private final long minimumIntervalMillis;
    private final Map<Scope, LimitState> states = new EnumMap<>(Scope.class);

    RiotRateLimitExecutor() {
        this(DEFAULT_REQUESTS_PER_SECOND, DEFAULT_REQUESTS_PER_TWO_MINUTES);
    }

    RiotRateLimitExecutor(int requestsPerSecond, int requestsPerTwoMinutes) {
        this(
                Clock.systemUTC(),
                duration -> Thread.sleep(duration.toMillis()),
                requestsPerSecond,
                requestsPerTwoMinutes
        );
    }

    RiotRateLimitExecutor(Clock clock, Sleeper sleeper) {
        this(
                clock,
                sleeper,
                DEFAULT_REQUESTS_PER_SECOND,
                DEFAULT_REQUESTS_PER_TWO_MINUTES
        );
    }

    RiotRateLimitExecutor(
            Clock clock,
            Sleeper sleeper,
            int requestsPerSecond,
            int requestsPerTwoMinutes
    ) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
        if (requestsPerSecond < 1) {
            throw new IllegalArgumentException("requestsPerSecond must be positive");
        }
        if (requestsPerTwoMinutes < 1) {
            throw new IllegalArgumentException("requestsPerTwoMinutes must be positive");
        }
        long perSecondInterval = divideRoundingUp(1_000L, requestsPerSecond);
        long perTwoMinutesInterval = divideRoundingUp(
                TWO_MINUTES.toMillis(),
                requestsPerTwoMinutes
        );
        this.minimumIntervalMillis = Math.max(perSecondInterval, perTwoMinutesInterval);
        for (Scope scope : Scope.values()) {
            states.put(scope, new LimitState());
        }
    }

    /**
     * 요청을 실행하고 Riot API가 알려준 대기 시간만큼 이후 요청을 지연한다.
     *
     * <p>429 응답에 Retry-After 헤더가 있으면 해당 시간 동안 대기한 뒤 성공할 때까지 재시도하고,
     * 헤더가 없거나 올바르지 않으면 원래 예외를 다시 던진다.
     */
    synchronized <T> T execute(Scope scope, Supplier<T> request) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(request, "request must not be null");

        long attempt = 1;
        while (true) {
            // 같은 Riot routing scope의 요청 간격과 429 대기 시간을 함께 지킨다.
            awaitAvailable(scope);

            try {
                return request.get();
            } catch (HttpClientErrorException.TooManyRequests exception) {
                // 서버가 응답한 Retry-After를 다음 요청의 대기 시간으로 기록한다.
                Duration retryAfter = retryAfter(exception);
                blockRequests(scope, retryAfter);

                log.warn(
                        "Riot API 호출 제한: attempt={}, retryAfterMs={}",
                        attempt,
                        retryAfter.toMillis()
                );
                attempt++;
            }
        }
    }

    /**
     * 429 응답의 Retry-After 헤더를 초 단위 대기 시간으로 변환한다.
     * 헤더가 없거나 숫자가 아니면 서버가 준 원래 429 예외를 그대로 전달한다.
     */
    private Duration retryAfter(HttpClientErrorException.TooManyRequests exception) {
        HttpHeaders responseHeaders = exception.getResponseHeaders();
        if (responseHeaders == null) {
            log.error("Riot API 호출 제한 응답 오류: Retry-After 없음");
            throw exception;
        }

        String header = responseHeaders.getFirst(HttpHeaders.RETRY_AFTER);
        if (!StringUtils.hasText(header)) {
            log.error("Riot API 호출 제한 응답 오류: Retry-After 없음");
            throw exception;
        }

        try {
            long seconds = Long.parseLong(header);
            if (seconds <= 0) {
                log.error("Riot API 호출 제한 응답 오류: Retry-After={}", header);
                throw exception;
            }
            long retryAfterMillis = Math.multiplyExact(seconds, 1_000L);
            Math.addExact(clock.millis(), retryAfterMillis);
            return Duration.ofMillis(retryAfterMillis);
        } catch (NumberFormatException | ArithmeticException parseException) {
            log.error("Riot API 호출 제한 응답 오류: Retry-After={}", header);
            throw exception;
        }
    }

    /**
     * 현재 시각과 Retry-After를 더해 다음 요청이 가능한 시각을 기록한다.
     * 이미 더 긴 대기 시간이 설정되어 있다면 기존 기한을 유지한다.
     */
    private void blockRequests(Scope scope, Duration retryAfter) {
        long deadline = clock.millis() + retryAfter.toMillis();
        LimitState state = states.get(scope);
        state.blockedUntilMillis = Math.max(state.blockedUntilMillis, deadline);
    }

    /**
     * 해당 routing scope의 선제 호출 간격이나 429 대기 기한이 남아 있을 때 Sleeper를 호출한다.
     * 인터럽트가 발생하면 인터럽트 상태를 복원하고 요청을 중단한다.
     */
    private void awaitAvailable(Scope scope) {
        LimitState state = states.get(scope);
        long now = clock.millis();
        long requestAt = Math.max(
                now,
                Math.max(state.blockedUntilMillis, state.nextRequestAtMillis)
        );
        long waitMillis = requestAt - now;

        if (waitMillis > 0) {
            try {
                sleeper.sleep(Duration.ofMillis(waitMillis));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "[Error] Interrupted while waiting for Riot API rate limit",
                        exception
                );
            }
        }

        state.nextRequestAtMillis = Math.addExact(requestAt, minimumIntervalMillis);
        if (requestAt >= state.blockedUntilMillis) {
            state.blockedUntilMillis = 0;
        }
    }

    private static long divideRoundingUp(long dividend, long divisor) {
        return Math.floorDiv(Math.addExact(dividend, divisor - 1), divisor);
    }

    enum Scope {
        PLATFORM,
        REGIONAL
    }

    private static final class LimitState {

        private long blockedUntilMillis;
        private long nextRequestAtMillis;
    }

    @FunctionalInterface
    interface Sleeper {

        /**
         * 지정된 시간만큼 현재 요청 실행을 대기시킨다.
         */
        void sleep(Duration duration) throws InterruptedException;
    }
}
