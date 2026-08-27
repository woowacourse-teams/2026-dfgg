package dfgg.infrastructure.external.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiotRateLimitExecutorTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-27T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void 같은_routing_scope의_요청을_95회_2분_간격으로_제한한다() {
        List<Duration> delays = new ArrayList<>();
        RiotRateLimitExecutor executor = new RiotRateLimitExecutor(
                FIXED_CLOCK,
                delays::add,
                18,
                95
        );

        executor.execute(RiotRateLimitExecutor.Scope.REGIONAL, () -> "first");
        executor.execute(RiotRateLimitExecutor.Scope.REGIONAL, () -> "second");

        assertThat(delays).containsExactly(Duration.ofMillis(1_264));
    }

    @Test
    void platform과_regional의_호출_예산을_분리한다() {
        List<Duration> delays = new ArrayList<>();
        RiotRateLimitExecutor executor = new RiotRateLimitExecutor(
                FIXED_CLOCK,
                delays::add,
                18,
                95
        );

        executor.execute(RiotRateLimitExecutor.Scope.REGIONAL, () -> "regional");
        executor.execute(RiotRateLimitExecutor.Scope.PLATFORM, () -> "platform");

        assertThat(delays).isEmpty();
    }

    @Test
    void 호출_제한은_양수여야_한다() {
        assertThatThrownBy(() -> new RiotRateLimitExecutor(FIXED_CLOCK, ignored -> {
        }, 0, 95))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestsPerSecond must be positive");

        assertThatThrownBy(() -> new RiotRateLimitExecutor(FIXED_CLOCK, ignored -> {
        }, 18, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestsPerTwoMinutes must be positive");
    }
}
