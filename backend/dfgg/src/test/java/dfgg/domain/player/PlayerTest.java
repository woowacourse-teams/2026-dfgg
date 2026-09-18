package dfgg.domain.player;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlayerTest {

    @Test
    void 티어와_마지막_관측_시각을_갱신한다() {
        Instant firstObservedAt = Instant.parse("2026-08-06T08:00:00Z");
        Instant lastObservedAt = Instant.parse("2026-08-06T09:00:00Z");
        Player player = new Player("puuid-1", "KR", "GOLD", "I", firstObservedAt);

        player.updateRank("PLATINUM", "II", lastObservedAt);

        assertThat(player.getTier()).isEqualTo("PLATINUM");
        assertThat(player.getDivision()).isEqualTo("II");
        assertThat(player.getFirstSeenAt()).isEqualTo(firstObservedAt);
        assertThat(player.getLastSeenAt()).isEqualTo(lastObservedAt);
    }
}
