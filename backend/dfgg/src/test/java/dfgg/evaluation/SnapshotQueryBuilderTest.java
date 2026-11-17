package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.recommend.v3.GameSituation;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.CoreItemAcquisition;
import dfgg.domain.match.CoreItemChange;
import dfgg.domain.match.MatchStateTimeline;
import dfgg.domain.match.NormalizedMatchParticipant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnapshotQueryBuilderTest {

    private final SnapshotQueryBuilder builder = new SnapshotQueryBuilder(Set.of());

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
    @DisplayName("구매 시점 입력은 사후 Core summary와 무관하게 전달받은 과거 구매만 사용한다")
    void purchaseQueryUsesOnlySuppliedPastItems() {
        var me = participant("me", 157, 100, "MIDDLE", List.of(6673, 3031, 3036), false);
        var query = builder.buildAtPurchase(List.of(me), 1, "16.18", List.of(6673L));
        assertThat(query.purchasedItemIds()).containsExactly(6673L);
        assertThat(query.position()).isEqualTo(ChampionPosition.MID);
        assertThat(query.myChampionId()).isEqualTo(157L);
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

    // ─── 구매 시점의 상황 채우기 ────────────────────────────────────────────

    private static final long FIRST_CORE_AT = 600_000L;
    private static final long SECOND_CORE_AT = 1_200_000L;

    private NormalizedMatchParticipant numbered(
            String puuid, int participantId, int championId, int teamId, String position, List<Integer> order
    ) {
        return new NormalizedMatchParticipant(
                puuid, participantId, championId, teamId, position, "EMERALD", true, order, order, true
        );
    }

    /** 나(1번)는 6673, 3031 순으로 샀다. 아군 2번, 상대 6번. */
    private List<NormalizedMatchParticipant> numberedMatch() {
        return List.of(
                numbered("me", 1, 157, 100, "MIDDLE", List.of(6673, 3031)),
                numbered("ally", 2, 222, 100, "BOTTOM", List.of(3031)),
                numbered("enemy", 6, 103, 200, "MIDDLE", List.of(3089))
        );
    }

    private Optional<List<CoreItemAcquisition>> myAcquisitions() {
        return Optional.of(List.of(
                new CoreItemAcquisition(6673, FIRST_CORE_AT), new CoreItemAcquisition(3031, SECOND_CORE_AT)));
    }

    /**
     * 첫 Core 전에 내가 한 번 잡았고, 첫 Core와 <b>같은 시각</b>에 한 번 더 잡았다.
     * 그 뒤 상대가 3089를 사고 나를 한 번 잡았다.
     */
    private Optional<MatchStateTimeline> stateTimeline() {
        return Optional.of(new MatchStateTimeline(List.of(
                new MatchStateTimeline.Kill(500_000L, 1, 6, List.of()),
                new MatchStateTimeline.Kill(FIRST_CORE_AT, 1, 6, List.of()),
                new MatchStateTimeline.CoreItemChanged(700_000L, 6, new CoreItemChange(700_000L, null, 3089)),
                new MatchStateTimeline.Kill(900_000L, 6, 1, List.of())
        )));
    }

    private GameSituation.Known knownSituationOf(SnapshotQuery snapshot) {
        assertThat(snapshot.query().situation()).isInstanceOf(GameSituation.Known.class);
        return (GameSituation.Known) snapshot.query().situation();
    }

    @Test
    @DisplayName("스냅샷마다 그 Core를 산 시각 직전의 상황을 싣는다 — 구매와 같은 시각의 기록은 넣지 않는다")
    void buildWithSituation_WhenStateAndPurchaseTimesGiven_FillsSituationJustBeforeEachPurchase() {
        // when
        List<SnapshotQuery> snapshots = builder.buildWithSituation(
                numberedMatch(), "me", "16.17", stateTimeline(), myAcquisitions());

        // then: 첫 Core 직전에는 한 번만 잡은 상태다. 같은 시각의 킬과 그 뒤의 기록은 아직 없다
        GameSituation.Known beforeFirstCore = knownSituationOf(snapshots.get(0));
        assertThat(beforeFirstCore.me().kills()).isEqualTo(1);
        assertThat(beforeFirstCore.me().deaths()).isZero();
        assertThat(beforeFirstCore.enemies().get(0).coreItemIds()).isEmpty();

        // then: 두 번째 Core 직전에는 그 사이의 기록이 모두 들어 있다
        GameSituation.Known beforeSecondCore = knownSituationOf(snapshots.get(1));
        assertThat(beforeSecondCore.me().kills()).isEqualTo(2);
        assertThat(beforeSecondCore.me().deaths()).isEqualTo(1);
        assertThat(beforeSecondCore.enemies().get(0).coreItemIds()).containsExactly(3089L);
    }

    @Test
    @DisplayName("상황 말고는 기존 스냅샷과 같다 — 표본과 정답이 달라지면 기준 모델과 비교할 수 없다")
    void buildWithSituation_WhenSituationFilled_KeepsEverythingElseSameAsBuild() {
        // given
        List<SnapshotQuery> plain = builder.build(numberedMatch(), "me", "16.17");

        // when
        List<SnapshotQuery> withSituation = builder.buildWithSituation(
                numberedMatch(), "me", "16.17", stateTimeline(), myAcquisitions());

        // then
        assertThat(withSituation).hasSameSizeAs(plain);
        for (int step = 0; step < plain.size(); step++) {
            assertThat(withSituation.get(step).purchaseStep()).isEqualTo(plain.get(step).purchaseStep());
            assertThat(withSituation.get(step).groundTruthItemId()).isEqualTo(plain.get(step).groundTruthItemId());
            assertThat(withSituation.get(step).matchId()).isEqualTo(plain.get(step).matchId());
            // 상황만 뺀 요청 전체가 같아야 한다. 값을 옮기다 두 칸이 바뀌면 여기서 잡힌다.
            var filled = withSituation.get(step).query();
            var original = plain.get(step).query();
            assertThat(filled.myChampionId()).isEqualTo(original.myChampionId());
            assertThat(filled.position()).isEqualTo(original.position());
            assertThat(filled.purchasedItemIds()).isEqualTo(original.purchasedItemIds());
            assertThat(filled.allyChampionIds()).isEqualTo(original.allyChampionIds());
            assertThat(filled.enemyChampionIds()).isEqualTo(original.enemyChampionIds());
            assertThat(filled.tier()).isEqualTo(original.tier());
            assertThat(filled.patch()).isEqualTo(original.patch());
            assertThat(filled.coreItemIds()).isEqualTo(original.coreItemIds());
            assertThat(filled.situation().isUnknown()).isFalse();
        }
    }

    @Test
    @DisplayName("다시 계산한 구매 목록이 저장된 것보다 짧아도 상황을 모름으로 둔다 — 시각이 없는 스냅샷이 생긴다")
    void buildWithSituation_WhenRecomputedOrderIsShorterThanStoredOrder_LeavesSituationUnknown() {
        // given: 저장된 순서는 6673, 3031인데 다시 계산한 것은 6673뿐이다
        Optional<List<CoreItemAcquisition>> shorter = Optional.of(List.of(
                new CoreItemAcquisition(6673, FIRST_CORE_AT)));

        // when
        List<SnapshotQuery> snapshots = builder.buildWithSituation(
                numberedMatch(), "me", "16.17", stateTimeline(), shorter);

        // then
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots).allSatisfy(snapshot -> assertThat(snapshot.query().situation().isUnknown()).isTrue());
    }

    @Test
    @DisplayName("다시 계산한 구매 순서가 저장된 순서와 다르면 상황을 모름으로 둔다 — 다른 Core의 시각을 쓰게 된다")
    void buildWithSituation_WhenRecomputedOrderDiffersFromStoredOrder_LeavesSituationUnknown() {
        // given: 저장된 순서는 6673, 3031인데 다시 계산한 순서는 3031, 6673이다
        Optional<List<CoreItemAcquisition>> reordered = Optional.of(List.of(
                new CoreItemAcquisition(3031, FIRST_CORE_AT), new CoreItemAcquisition(6673, SECOND_CORE_AT)));

        // when
        List<SnapshotQuery> snapshots = builder.buildWithSituation(
                numberedMatch(), "me", "16.17", stateTimeline(), reordered);

        // then: 스냅샷은 그대로 나오고 상황만 모름이다
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots).allSatisfy(snapshot -> assertThat(snapshot.query().situation().isUnknown()).isTrue());
    }

    @Test
    @DisplayName("상태 타임라인을 만들지 못한 경기는 상황을 모름으로 두고 스냅샷은 그대로 낸다")
    void buildWithSituation_WhenStateTimelineMissing_LeavesSituationUnknown() {
        // when
        List<SnapshotQuery> snapshots = builder.buildWithSituation(
                numberedMatch(), "me", "16.17", Optional.empty(), myAcquisitions());

        // then
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots).allSatisfy(snapshot -> assertThat(snapshot.query().situation().isUnknown()).isTrue());
    }

    @Test
    @DisplayName("구매 시각을 얻지 못한 참가자는 상황을 모름으로 두고 스냅샷은 그대로 낸다")
    void buildWithSituation_WhenPurchaseTimesMissing_LeavesSituationUnknown() {
        // when
        List<SnapshotQuery> snapshots = builder.buildWithSituation(
                numberedMatch(), "me", "16.17", stateTimeline(), Optional.empty());

        // then
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots).allSatisfy(snapshot -> assertThat(snapshot.query().situation().isUnknown()).isTrue());
    }
}
