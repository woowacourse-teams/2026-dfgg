package dfgg.domain.rune;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunePageSamplesTest {

    private static final RuneRecommendationPolicy POLICY = new RuneRecommendationPolicy(100, 5, 3);

    private static final StatShards SHARDS = new StatShards(5005, 5008, 5001);
    private static final StatShards OTHER_SHARDS = new StatShards(5008, 5008, 5011);

    // 결의(8400) + 착취의 손아귀(8437), 보조 영감(8300)
    private static final RunePage GRASP_INSPIRATION = new RunePage(8400, List.of(8437, 8446, 8473, 8451),
            8300, List.of(8304, 8345), SHARDS);
    private static final RunePage GRASP_INSPIRATION_OTHER_SHARDS = new RunePage(8400,
            List.of(8437, 8446, 8473, 8451), 8300, List.of(8304, 8345), OTHER_SHARDS);
    // 마법(8200) + 난입(8230), 보조 결의(8400)
    private static final RunePage PHASE_RUSH_RESOLVE = new RunePage(8200, List.of(8230, 8226, 8233, 8236),
            8400, List.of(8444, 8242), SHARDS);
    // 정밀(8000) + 정복자(8010), 보조 결의(8400)
    private static final RunePage CONQUEROR_RESOLVE = new RunePage(8000, List.of(8010, 9111, 9104, 8299),
            8400, List.of(8444, 8242), SHARDS);
    // 정밀(8000) + 집중 공격(8005), 보조 결의(8400)
    private static final RunePage PRESS_THE_ATTACK_RESOLVE = new RunePage(8000, List.of(8005, 9111, 9104, 8299),
            8400, List.of(8444, 8242), SHARDS);

    @Test
    @DisplayName("전체 판 수가 기준보다 적으면 페이지 없이 판 수만 돌려준다")
    void recommend_WhenGamesBelowMinimum_ReturnsGamesWithoutPages() {
        // given
        RunePageSamples samples = samples(GRASP_INSPIRATION, 99);

        // when
        RuneRecommendation recommendation = samples.recommend(POLICY);

        // then
        assertThat(recommendation.games()).isEqualTo(99);
        assertThat(recommendation.pages()).isEmpty();
    }

    @Test
    @DisplayName("묶음 판 수 순으로 상위 3개 묶음을 고르고 각 묶음에서 가장 많이 쓴 페이지를 낸다")
    void recommend_PicksTopThreePathsAndMostUsedPageOfEach() {
        // given
        RunePageSamples samples = samples(
                GRASP_INSPIRATION, 40, GRASP_INSPIRATION_OTHER_SHARDS, 10,
                PHASE_RUSH_RESOLVE, 30,
                CONQUEROR_RESOLVE, 15,
                PRESS_THE_ATTACK_RESOLVE, 5);

        // when
        RuneRecommendation recommendation = samples.recommend(POLICY);

        // then
        assertThat(recommendation.games()).isEqualTo(100);
        assertThat(recommendation.pages()).extracting(RecommendedRunePage::page)
                .containsExactly(GRASP_INSPIRATION, PHASE_RUSH_RESOLVE, CONQUEROR_RESOLVE);
        assertThat(recommendation.pages()).extracting(RecommendedRunePage::rank).containsExactly(1, 2, 3);
        assertThat(recommendation.pages()).extracting(RecommendedRunePage::pathGames).containsExactly(50, 30, 15);
        assertThat(recommendation.pages()).extracting(RecommendedRunePage::pathPickRate)
                .containsExactly(0.5, 0.3, 0.15);
    }

    @Test
    @DisplayName("기준 판 수보다 적은 묶음은 빼고 1등 묶음의 다음으로 많이 쓴 페이지로 채운다")
    void recommend_WhenPathBelowMinimum_FillsWithTopPathVariant() {
        // given
        RunePageSamples samples = samples(
                GRASP_INSPIRATION, 60, GRASP_INSPIRATION_OTHER_SHARDS, 10,
                PHASE_RUSH_RESOLVE, 26,
                CONQUEROR_RESOLVE, 4);

        // when
        RuneRecommendation recommendation = samples.recommend(POLICY);

        // then
        assertThat(recommendation.pages()).extracting(RecommendedRunePage::page)
                .containsExactly(GRASP_INSPIRATION, PHASE_RUSH_RESOLVE, GRASP_INSPIRATION_OTHER_SHARDS);
        assertThat(recommendation.pages().get(2).pathGames()).isEqualTo(70);
    }

    @Test
    @DisplayName("서로 다른 페이지가 3개가 안 되면 있는 만큼만 낸다")
    void recommend_WhenFewerDistinctPages_ReturnsWhatExists() {
        // given
        RunePageSamples samples = samples(GRASP_INSPIRATION, 70, PHASE_RUSH_RESOLVE, 30);

        // when
        RuneRecommendation recommendation = samples.recommend(POLICY);

        // then
        assertThat(recommendation.pages()).extracting(RecommendedRunePage::page)
                .containsExactly(GRASP_INSPIRATION, PHASE_RUSH_RESOLVE);
    }

    @Test
    @DisplayName("판 수가 같은 묶음은 핵심 룬 id 순으로 정해 매번 같은 순서로 낸다")
    void recommend_WhenPathGamesTie_OrdersByKeystoneId() {
        // given
        RunePageSamples samples = samples(
                PRESS_THE_ATTACK_RESOLVE, 25, CONQUEROR_RESOLVE, 25,
                GRASP_INSPIRATION, 25, PHASE_RUSH_RESOLVE, 25);

        // when
        RuneRecommendation recommendation = samples.recommend(POLICY);

        // then
        assertThat(recommendation.pages()).extracting(page -> page.page().keystoneId())
                .containsExactly(8005, 8010, 8230);
    }

    @Test
    @DisplayName("기록 순서가 달라도 같은 추천을 낸다")
    void recommend_IsIndependentOfSampleOrder() {
        // given
        List<RunePage> pages = new ArrayList<>(pagesOf(
                GRASP_INSPIRATION, 40, GRASP_INSPIRATION_OTHER_SHARDS, 10,
                PHASE_RUSH_RESOLVE, 30, CONQUEROR_RESOLVE, 20));
        RuneRecommendation ordered = new RunePageSamples(pages).recommend(POLICY);
        Collections.reverse(pages);

        // when
        RuneRecommendation reversed = new RunePageSamples(pages).recommend(POLICY);

        // then
        assertThat(reversed).isEqualTo(ordered);
    }

    private RunePageSamples samples(Object... pageAndCounts) {
        return new RunePageSamples(pagesOf(pageAndCounts));
    }

    private List<RunePage> pagesOf(Object... pageAndCounts) {
        List<RunePage> pages = new ArrayList<>();
        for (int i = 0; i < pageAndCounts.length; i += 2) {
            pages.addAll(Collections.nCopies((Integer) pageAndCounts[i + 1], (RunePage) pageAndCounts[i]));
        }
        return pages;
    }
}
