package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.NormalizedMatchParticipant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnapshotQueryBuilderTest {

    private final SnapshotQueryBuilder builder = new SnapshotQueryBuilder();

    private NormalizedMatchParticipant participant(
            String puuid, int championId, int teamId, String position, List<Integer> order, boolean complete
    ) {
        return new NormalizedMatchParticipant(
                puuid, 1, championId, teamId, position, "EMERALD", true, order, order, complete
        );
    }

    private List<NormalizedMatchParticipant> match() {
        return List.of(
                participant("me", 157, 100, "MIDDLE", List.of(6673, 3031, 3036), true),
                participant("ally1", 222, 100, "BOTTOM", List.of(3031), true),
                participant("ally2", 412, 100, "UTILITY", List.of(3190), true),
                participant("enemy1", 33, 200, "TOP", List.of(3068), true),
                participant("enemy2", 103, 200, "MIDDLE", List.of(6653), true)
        );
    }

    @Test
    @DisplayName("구매 단계마다 스냅샷 하나씩 만든다 — 코어 3개면 query 3개")
    void build_WhenParticipantBoughtThreeItems_ProducesThreeSnapshots() {
        // when
        List<SnapshotQuery> queries = builder.build(match(), "me", "16.17");

        // then
        assertThat(queries).hasSize(3);
    }

    @Test
    @DisplayName("각 스냅샷의 정답은 그 시점의 다음 구매 아이템이다")
    void build_WhenSnapshotting_GroundTruthIsTheNextPurchase() {
        // when
        List<SnapshotQuery> queries = builder.build(match(), "me", "16.17");

        // then
        assertThat(queries).extracting(SnapshotQuery::groundTruthItemId)
                .containsExactly(6673L, 3031L, 3036L);
    }

    @Test
    @DisplayName("스냅샷의 구매 이력은 그 시점까지만 담는다 — 미래 정보가 새면 안 된다")
    void build_WhenSnapshotting_PurchasedItemsContainOnlyThePast() {
        // when
        List<SnapshotQuery> queries = builder.build(match(), "me", "16.17");

        // then
        assertThat(queries.get(0).query().purchasedItemIds()).isEmpty();
        assertThat(queries.get(1).query().purchasedItemIds()).containsExactly(6673L);
        assertThat(queries.get(2).query().purchasedItemIds()).containsExactly(6673L, 3031L);
    }

    @Test
    @DisplayName("같은 팀은 아군으로, 다른 팀은 적으로 넣는다")
    void build_WhenBuildingQuery_SeparatesAlliesFromEnemiesByTeam() {
        // when
        SnapshotQuery first = builder.build(match(), "me", "16.17").get(0);

        // then
        assertThat(first.query().allyChampionIds()).containsExactlyInAnyOrder(222L, 412L);
        assertThat(first.query().enemyChampionIds()).containsExactlyInAnyOrder(33L, 103L);
    }

    @Test
    @DisplayName("자기 자신은 아군 목록에 넣지 않는다")
    void build_WhenBuildingQuery_ExcludesSelfFromAllies() {
        // when
        SnapshotQuery first = builder.build(match(), "me", "16.17").get(0);

        // then
        assertThat(first.query().allyChampionIds()).doesNotContain(157L);
        assertThat(first.query().myChampionId()).isEqualTo(157L);
    }

    @Test
    @DisplayName("Riot 원시 포지션을 정규화한다 — MIDDLE은 MID로")
    void build_WhenRiotRawPosition_NormalizesIt() {
        // when
        SnapshotQuery first = builder.build(match(), "me", "16.17").get(0);

        // then
        assertThat(first.query().position()).isEqualTo(ChampionPosition.MID);
    }

    @Test
    @DisplayName("구매 순서가 불완전한 참가자는 스냅샷을 만들지 않는다 — 정답을 신뢰할 수 없다")
    void build_WhenPurchaseOrderIsIncomplete_ProducesNothing() {
        // given
        List<NormalizedMatchParticipant> matchParticipants = List.of(
                participant("me", 157, 100, "MIDDLE", List.of(6673, 3031), false),
                participant("enemy1", 33, 200, "TOP", List.of(3068), true)
        );

        // when & then
        assertThat(builder.build(matchParticipants, "me", "16.17")).isEmpty();
    }

    @Test
    @DisplayName("아무것도 안 산 참가자는 스냅샷을 만들지 않는다")
    void build_WhenNothingPurchased_ProducesNothing() {
        // given
        List<NormalizedMatchParticipant> matchParticipants = List.of(
                participant("me", 157, 100, "MIDDLE", List.of(), true),
                participant("enemy1", 33, 200, "TOP", List.of(3068), true)
        );

        // when & then
        assertThat(builder.build(matchParticipants, "me", "16.17")).isEmpty();
    }

    @Test
    @DisplayName("포지션을 알 수 없는 참가자는 건너뛴다")
    void build_WhenPositionIsUnknown_ProducesNothing() {
        // given
        List<NormalizedMatchParticipant> matchParticipants = List.of(
                participant("me", 157, 100, "", List.of(6673), true),
                participant("enemy1", 33, 200, "TOP", List.of(3068), true)
        );

        // when & then
        assertThat(builder.build(matchParticipants, "me", "16.17")).isEmpty();
    }
}
