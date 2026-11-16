package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.domain.champion.ChampionPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecommendationQueryTest {

    private RecommendationQuery queryWith(List<Long> purchasedItemIds) {
        return new RecommendationQuery(
                157L, ChampionPosition.MID, purchasedItemIds,
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17", Set.of(6673L, 3031L)
        );
    }

    @Test
    @DisplayName("생성 후 Core 목록 원본을 바꿔도 query는 영향받지 않는다")
    void construct_WhenCoreItemIdsMutatedAfterwards_QueryIsUnaffected() {
        // given
        Set<Long> mutableCore = new java.util.HashSet<>(Set.of(6673L));
        RecommendationQuery query = new RecommendationQuery(
                157L, ChampionPosition.MID, List.of(),
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17", mutableCore
        );

        // when
        mutableCore.add(3031L);

        // then
        assertThat(query.coreItemIds()).containsExactly(6673L);
    }

    @Test
    @DisplayName("생성 후 원본 리스트를 바꿔도 query는 영향받지 않는다 — 4개 generator가 같은 query를 공유한다")
    void construct_WhenSourceListMutatedAfterwards_QueryIsUnaffected() {
        // given
        List<Long> mutablePurchased = new ArrayList<>(List.of(6673L));
        RecommendationQuery query = queryWith(mutablePurchased);

        // when
        mutablePurchased.add(3031L);

        // then
        assertThat(query.purchasedItemIds()).containsExactly(6673L);
    }

    @Test
    @DisplayName("구매한 코어 개수는 다음 구매 시점(purchase step)을 뜻한다")
    void purchasedItemCount_WhenTwoItemsPurchased_ReturnsTwo() {
        // given
        RecommendationQuery query = queryWith(List.of(6673L, 3031L));

        // when & then
        assertThat(query.purchasedItemCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("아직 아무것도 안 샀으면 구매 개수는 0이다")
    void purchasedItemCount_WhenNothingPurchased_ReturnsZero() {
        // given
        RecommendationQuery query = queryWith(List.of());

        // when & then
        assertThat(query.purchasedItemCount()).isZero();
    }

    @Test
    @DisplayName("내 챔피언이 적군에 함께 들어있으면 거부한다 — 조합 데이터가 뒤섞인 것이다")
    void construct_WhenMyChampionIsAlsoAnEnemy_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> new RecommendationQuery(
                157L, ChampionPosition.MID, List.of(),
                List.of(222L, 412L, 64L, 516L), List.of(157L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17", Set.of(6673L, 3031L)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("patch가 비어있으면 거부한다 — 최근 윈도 통계 조회의 기준이다")
    void construct_WhenPatchIsBlank_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> new RecommendationQuery(
                157L, ChampionPosition.MID, List.of(),
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "  ", Set.of(6673L, 3031L)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("내 챔피언 ID가 없으면 거부한다")
    void construct_WhenMyChampionIdIsNull_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> new RecommendationQuery(
                null, ChampionPosition.MID, List.of(),
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17", Set.of(6673L, 3031L)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("상황 없이 만든 query의 상황은 모름이다 — 상황을 보내지 않는 요청이 그대로 동작한다")
    void construct_WhenSituationNotGiven_SituationIsUnknown() {
        // when
        RecommendationQuery query = queryWith(List.of());

        // then
        assertThat(query.situation().isUnknown()).isTrue();
    }

    @Test
    @DisplayName("상황을 넘겨 만든 query는 그 상황을 그대로 갖는다")
    void construct_WhenSituationGiven_KeepsSituation() {
        // given
        GameSituation situation = GameSituation.of(
                new MyState(9, 3, 1, 4),
                List.of(new PlayerState(222L, ChampionPosition.BOTTOM, 8, 2, 1, List.of(3031L))),
                List.of(new PlayerState(103L, ChampionPosition.MID, 10, 5, 2, List.of(3089L))));

        // when
        RecommendationQuery query = new RecommendationQuery(
                157L, ChampionPosition.MID, List.of(),
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17", Set.of(6673L, 3031L), situation
        );

        // then
        assertThat(query.situation()).isEqualTo(situation);
    }

    @Test
    @DisplayName("상황 자리에 null을 넘기면 거부한다 — 모름은 null이 아니라 모름으로 싣는다")
    void construct_WhenSituationIsNull_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> new RecommendationQuery(
                157L, ChampionPosition.MID, List.of(),
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17", Set.of(6673L, 3031L), null
        )).isInstanceOf(NullPointerException.class);
    }
}
