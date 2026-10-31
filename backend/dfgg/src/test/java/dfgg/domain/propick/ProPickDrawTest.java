package dfgg.domain.propick;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionPosition;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProPickDrawTest {

    private static final int SIZE = 5;
    private static final long SEED = 42L;

    private final ProPickDraw draw = new ProPickDraw(SIZE);

    @Test
    @DisplayName("같은 챔피언은 한 번만 뽑는다")
    void draw_WhenSameChampionPickedSeveralTimes_DrawOnce() {
        // given
        List<ProPick> candidates = List.of(pick("g1", 1L), pick("g2", 1L), pick("g3", 1L), pick("g4", 2L));

        // when
        List<ProPick> drawn = draw.draw(candidates, new Random(SEED));

        // then
        assertThat(drawn).extracting(ProPick::getChampionId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("후보가 많아도 정해 둔 개수까지만 뽑는다")
    void draw_WhenManyCandidates_DrawUpToSize() {
        // given
        List<ProPick> candidates = IntStream.rangeClosed(1, 20).mapToObj(id -> pick("g" + id, (long) id)).toList();

        // when
        List<ProPick> drawn = draw.draw(candidates, new Random(SEED));

        // then
        assertThat(drawn).hasSize(SIZE);
        assertThat(drawn).extracting(ProPick::getChampionId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("후보가 모자라면 있는 만큼만, 없으면 빈 목록이다")
    void draw_WhenFewCandidates_DrawAvailable() {
        // given
        List<ProPick> candidates = List.of(pick("g1", 1L), pick("g2", 2L));

        // when & then
        assertThat(draw.draw(candidates, new Random(SEED))).hasSize(2);
        assertThat(draw.draw(List.of(), new Random(SEED))).isEmpty();
    }

    @Test
    @DisplayName("시드가 같으면 뽑는 픽과 순서가 같다")
    void draw_WhenSameSeed_ReturnSameResult() {
        // given
        List<ProPick> candidates = IntStream.rangeClosed(1, 20).mapToObj(id -> pick("g" + id, (long) id % 8)).toList();

        // when
        List<ProPick> first = draw.draw(candidates, new Random(SEED));
        List<ProPick> second = draw.draw(candidates, new Random(SEED));

        // then
        assertThat(first).containsExactlyElementsOf(second);
    }

    @Test
    @DisplayName("시드가 다르면 다른 픽이 나올 수 있다 — 매번 앞쪽 후보만 고정해 내지 않는다")
    void draw_WhenSeedsDiffer_VaryResult() {
        // given
        List<ProPick> candidates = IntStream.rangeClosed(1, 20).mapToObj(id -> pick("g" + id, (long) id)).toList();

        // when
        long distinctResults = IntStream.range(0, 10)
                .mapToObj(seed -> draw.draw(candidates, new Random(seed)))
                .distinct()
                .count();

        // then
        assertThat(distinctResults).isGreaterThan(1);
    }

    private static ProPick pick(String gameId, long championId) {
        ProPick pick = new ProPick(null, null, gameId);
        pick.update(null, OffsetDateTime.parse("2026-10-18T19:48:00Z"), "T1", ProPickSide.BLUE,
                ChampionPosition.MID, championId, true, List.of(), List.of(), List.of());
        return pick;
    }
}
