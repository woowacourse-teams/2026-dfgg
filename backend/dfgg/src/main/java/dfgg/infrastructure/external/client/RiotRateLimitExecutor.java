package dfgg.infrastructure.external.client;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;

final class RiotRateLimitExecutor {

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

    synchronized <T> T execute(Supplier<T> request) {
        Objects.requireNonNull(request, "request must not be null");

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            awaitAvailable();

            try {
                return request.get();
            } catch (HttpClientErrorException.TooManyRequests exception) {
                Duration retryAfter = retryAfter(exception);
                blockRequests(retryAfter);

                if (attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
            }
        }

        throw new IllegalStateException("[Error] Riot API retry attempts exhausted");
    }

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

    private void blockRequests(Duration retryAfter) {
        long deadline = clock.millis() + retryAfter.toMillis();
        blockedUntilMillis = Math.max(blockedUntilMillis, deadline);
    }

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

        void sleep(Duration duration) throws InterruptedException;
    }
}
