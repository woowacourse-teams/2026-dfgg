package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.domain.champion.ChampionPosition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecommendationQueryTest {

    private RecommendationQuery queryWith(List<Long> purchasedItemIds) {
        return new RecommendationQuery(
                157L, ChampionPosition.MID, purchasedItemIds,
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17"
        );
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
                "EMERALD", "16.17"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("patch가 비어있으면 거부한다 — 최근 윈도 통계 조회의 기준이다")
    void construct_WhenPatchIsBlank_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> new RecommendationQuery(
                157L, ChampionPosition.MID, List.of(),
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "  "
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("내 챔피언 ID가 없으면 거부한다")
    void construct_WhenMyChampionIdIsNull_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> new RecommendationQuery(
                null, ChampionPosition.MID, List.of(),
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
