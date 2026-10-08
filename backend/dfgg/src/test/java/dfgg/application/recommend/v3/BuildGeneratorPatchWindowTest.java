package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.recommend.v3.generator.BuildCandidateGenerator;
import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * 최근 패치 윈도 크기는 집계와 서빙이 <b>같은 값</b>을 써야 한다. 집계가 만든
 * {@code champion_item_stats._recent}와 서빙의 전개 통계가 서로 다른 윈도를 보면
 * 같은 "최근"이라는 말이 두 가지를 뜻하게 된다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/build-generator-test-data.sql")
class BuildGeneratorPatchWindowTest {

    private static final long KRAKEN = 6673L;
    private static final long INFINITY_EDGE = 3031L;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;

    @Autowired
    private ChampionItemStatsRepository championItemStatsRepository;

    private BuildCandidateGenerator generatorWithWindow(int recentPatchWindowSize) {
        return new BuildCandidateGenerator(
                participantRepository, championItemStatsRepository,
                new ChampionPositionNormalizer(), new WilsonScoreCalculator(),
                recentPatchWindowSize
        );
    }

    private double scoreOf(BuildCandidateGenerator generator, long itemId) {
        RecommendationQuery query = new RecommendationQuery(
                157L, ChampionPosition.MID, List.of(KRAKEN),
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17"
        );
        return generator.generate(query, 10).rankedItems().stream()
                .filter(item -> item.itemId() == itemId)
                .findFirst().orElseThrow()
                .score();
    }

    @Test
    @DisplayName("주입한 윈도 크기를 실제로 반영한다 — 좁은 윈도일수록 최근 편중 아이템의 점수가 오른다")
    void generate_WhenWindowSizeDiffers_ProducesDifferentScores() {
        // given: 6673 다음은 3031(5판, 전부 16.17)과 3072(4판, 전부 16.15)
        //        윈도 1이면 최근 표본이 3031뿐이라 5/5, 윈도 3이면 두 패치가 다 최근이라 5/9
        double narrowWindowScore = scoreOf(generatorWithWindow(1), INFINITY_EDGE);
        double wideWindowScore = scoreOf(generatorWithWindow(3), INFINITY_EDGE);

        // then
        assertThat(narrowWindowScore).isGreaterThan(wideWindowScore);
    }

    @Test
    @DisplayName("윈도가 좁을수록 최근 편중 아이템과 옛 패치 아이템의 점수 격차가 벌어진다")
    void generate_WhenWindowNarrowed_WidensGapBetweenRecentAndOldItems() {
        // given: 3031은 16.17 전용(5판), 3072는 16.15 전용(4판).
        //        점수가 max(all, recent)라 옛 아이템은 윈도를 넓혀도 all 점수를 넘지 못한다.
        //        윈도가 좁아질 때 실제로 움직이는 건 최근 편중 아이템 쪽이고, 그 결과 격차가 벌어진다.
        BuildCandidateGenerator narrow = generatorWithWindow(1);
        BuildCandidateGenerator wide = generatorWithWindow(3);

        double narrowGap = scoreOf(narrow, INFINITY_EDGE) - scoreOf(narrow, 3072L);
        double wideGap = scoreOf(wide, INFINITY_EDGE) - scoreOf(wide, 3072L);

        // then
        assertThat(narrowGap).isGreaterThan(wideGap);
    }
}
