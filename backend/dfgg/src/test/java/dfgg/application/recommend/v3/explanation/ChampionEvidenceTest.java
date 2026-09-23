package dfgg.application.recommend.v3.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 근거로 지목할 챔피언을 고른다.
 * <p>
 * generator가 남긴 상대별 점수에서 상위 몇 명만 뽑는다. 다섯 명을 다 늘어놓으면 "누구 때문인가"가 흐려지고, 문턱 없이 뽑으면 상관없는 챔피언까지 이유가 된다.
 */
class ChampionEvidenceTest {

    private static final long RAMMUS = 33L;
    private static final long AHRI = 103L;
    private static final long JINX = 222L;

    @Test
    @DisplayName("점수가 높은 순으로 고른다")
    void topChampionIds_OrdersByScoreDescending() {
        Map<Long, Double> byChampion = Map.of(RAMMUS, 1.8, AHRI, 2.4, JINX, 1.2);

        assertThat(ChampionEvidence.topChampionIds(byChampion, 1.0, 3))
                .containsExactly(AHRI, RAMMUS, JINX);
    }

    @Test
    @DisplayName("요청한 인원까지만 낸다")
    void topChampionIds_LimitsToTheRequestedCount() {
        Map<Long, Double> byChampion = Map.of(RAMMUS, 1.8, AHRI, 2.4, JINX, 1.2);

        assertThat(ChampionEvidence.topChampionIds(byChampion, 1.0, 2))
                .containsExactly(AHRI, RAMMUS);
    }

    @Test
    @DisplayName("문턱 이하인 챔피언은 빼낸다 — lift가 1 이하면 '이 적 때문에 올랐다'가 아니다")
    void topChampionIds_DropsChampionsAtOrBelowTheThreshold() {
        // 람머스 상대로는 평소보다 덜 사는(0.39) 아이템이라면 람머스를 이유로 댈 수 없다.
        Map<Long, Double> byChampion = Map.of(RAMMUS, 0.39, AHRI, 1.61);

        assertThat(ChampionEvidence.topChampionIds(byChampion, 1.0, 2))
                .containsExactly(AHRI);
    }

    @Test
    @DisplayName("문턱과 정확히 같으면 빼낸다 — lift 1.0은 '평소와 같다'는 뜻이다")
    void topChampionIds_DropsChampionsExactlyAtTheThreshold() {
        assertThat(ChampionEvidence.topChampionIds(Map.of(RAMMUS, 1.0), 1.0, 2)).isEmpty();
    }

    @Test
    @DisplayName("아무도 문턱을 넘지 못하면 빈 목록이다 — 억지로 채우지 않는다")
    void topChampionIds_WhenNobodyClearsTheThreshold_IsEmpty() {
        assertThat(ChampionEvidence.topChampionIds(Map.of(RAMMUS, 0.5, AHRI, 0.9), 1.0, 2))
                .isEmpty();
    }

    @Test
    @DisplayName("근거가 아예 없으면 빈 목록이다 — base rate로 백오프한 후보가 여기다")
    void topChampionIds_WhenThereIsNoEvidence_IsEmpty() {
        assertThat(ChampionEvidence.topChampionIds(Map.of(), 1.0, 2)).isEmpty();
    }

    @Test
    @DisplayName("점수가 같으면 매번 같은 순서를 낸다 — 순서가 흔들리면 응답이 흔들린다")
    void topChampionIds_BreaksTiesDeterministically() {
        Map<Long, Double> tied = Map.of(JINX, 2.0, RAMMUS, 2.0, AHRI, 2.0);

        List<Long> first = ChampionEvidence.topChampionIds(tied, 1.0, 2);

        assertThat(first).isEqualTo(ChampionEvidence.topChampionIds(tied, 1.0, 2));
        assertThat(first).containsExactly(RAMMUS, AHRI);
    }

    @Test
    @DisplayName("문턱을 넘는 챔피언이 요청 인원보다 적으면 있는 만큼만 낸다")
    void topChampionIds_WhenFewerQualifyThanRequested_ReturnsWhatItHas() {
        assertThat(ChampionEvidence.topChampionIds(Map.of(RAMMUS, 1.8, AHRI, 0.4), 1.0, 2))
                .containsExactly(RAMMUS);
    }
}
