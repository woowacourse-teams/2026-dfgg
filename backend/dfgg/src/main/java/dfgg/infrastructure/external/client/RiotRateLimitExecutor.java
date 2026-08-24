package dfgg.infrastructure.external.client;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Riot API 요청을 순차적으로 실행하고 429 응답에 맞춰 재시도한다.
 *
 * <p>요청을 synchronized로 보호해 같은 애플리케이션 인스턴스의 호출이
 * rate-limit 대기 상태를 공유하도록 한다.
 */
final class RiotRateLimitExecutor {

    // 한 요청에서 허용하는 최대 시도 횟수다. 최초 요청도 시도 횟수에 포함한다.
    private static final int MAX_ATTEMPTS = 3;

    private final Clock clock;
    private final Sleeper sleeper;

    private long blockedUntilMillis;

    RiotRateLimitExecutor() {
        this(
                Clock.systemUTC(),
                duration -> Thread.sleep(duration.toMillis())
        );
    }

    RiotRateLimitExecutor(Clock clock, Sleeper sleeper) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
    }

    /**
     * 요청을 실행하고 Riot API가 알려준 대기 시간만큼 이후 요청을 지연한다.
     *
     * <p>429 응답에 Retry-After 헤더가 있으면 해당 시간 동안 대기한 뒤 재시도하고,
     * 헤더가 없거나 최대 시도 횟수를 넘으면 원래 예외를 다시 던진다.
     */
    synchronized <T> T execute(Supplier<T> request) {
        Objects.requireNonNull(request, "request must not be null");

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            // 이전 요청이 설정한 전역 대기 시간이 남아 있으면 먼저 기다린다.
            awaitAvailable();

            try {
                return request.get();
            } catch (HttpClientErrorException.TooManyRequests exception) {
                // 서버가 응답한 Retry-After를 다음 요청의 대기 시간으로 기록한다.
                Duration retryAfter = retryAfter(exception);
                blockRequests(retryAfter);

                if (attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
            }
        }

        throw new IllegalStateException("[Error] Riot API retry attempts exhausted");
    }

    /**
     * 429 응답의 Retry-After 헤더를 초 단위 대기 시간으로 변환한다.
     * 헤더가 없거나 숫자가 아니면 서버가 준 원래 429 예외를 그대로 전달한다.
     */
    private Duration retryAfter(HttpClientErrorException.TooManyRequests exception) {
        HttpHeaders responseHeaders = exception.getResponseHeaders();
        if (responseHeaders == null) {
            throw exception;
        }

        String header = responseHeaders.getFirst(HttpHeaders.RETRY_AFTER);
        if (!StringUtils.hasText(header)) {
            throw exception;
        }

        try {
            long seconds = Long.parseLong(header);
            if (seconds < 0) {
                throw exception;
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException parseException) {
            throw exception;
        }
    }

    /**
     * 현재 시각과 Retry-After를 더해 다음 요청이 가능한 시각을 기록한다.
     * 이미 더 긴 대기 시간이 설정되어 있다면 기존 기한을 유지한다.
     */
    private void blockRequests(Duration retryAfter) {
        long deadline = clock.millis() + retryAfter.toMillis();
        blockedUntilMillis = Math.max(blockedUntilMillis, deadline);
    }

    /**
     * 전역 대기 기한이 남아 있을 때만 주입된 Sleeper를 호출한다.
     * 인터럽트가 발생하면 인터럽트 상태를 복원하고 요청을 중단한다.
     */
    private void awaitAvailable() {
        long waitMillis = blockedUntilMillis - clock.millis();
        if (waitMillis <= 0) {
            return;
        }

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

    @FunctionalInterface
    interface Sleeper {

        /**
         * 지정된 시간만큼 현재 요청 실행을 대기시킨다.
         */
        void sleep(Duration duration) throws InterruptedException;
    }
}
