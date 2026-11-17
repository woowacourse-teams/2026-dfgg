package dfgg.evaluation;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.recommend.v3.GameSituation;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.CoreItemAcquisition;
import dfgg.domain.match.MatchStateTimeline;
import dfgg.domain.match.NormalizedMatchParticipant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 실제 매치 한 판을 구매 단계별 평가 스냅샷으로 펼친다.
 *
 * <p>스냅샷 k의 구매 이력에는 <b>k번째까지만</b> 담고 정답은 k+1번째 아이템으로 둔다.
 * 이후에 산 아이템이 입력에 섞이면 미래를 보고 맞히는 셈이라 지표가 통째로 무의미해진다.
 *
 * <p>구매 순서가 불완전한 참가자는 건너뛴다 — 순서를 신뢰할 수 없으면 "다음 아이템"이라는
 * 정답 자체가 성립하지 않는다.
 */
public final class SnapshotQueryBuilder {

    private final ChampionPositionNormalizer positionNormalizer = new ChampionPositionNormalizer();
    private final SnapshotSituationMapper situationMapper = new SnapshotSituationMapper();
    private final Set<Long> coreItemIds;

    /** 서빙과 같은 기준({@code ItemService.findCoreItemIds()})의 Core 아이템을 모든 query에 싣는다 — 학습·평가와 서빙의 입력을 맞춘다. */
    public SnapshotQueryBuilder(Collection<Integer> coreItemIds) {
        this.coreItemIds = coreItemIds.stream().map(Long::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    /** 구매 시점 검증용: 경기 종료 후 Core summary 대신 호출자가 복원한 과거 구매만 받는다. */
    public RecommendationQuery buildAtPurchase(List<NormalizedMatchParticipant> participants,
            int participantId, String patch, List<Long> purchasedCoreIds) {
        var me = participants.stream().filter(p -> p.getParticipantId() == participantId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("매치에 없는 참가자입니다: " + participantId));
        var position = positionNormalizer.normalize(me.getPosition())
                .orElseThrow(() -> new IllegalArgumentException("참가자 포지션을 확인할 수 없습니다."));
        return queryOf(participants, me, patch, purchasedCoreIds, position);
    }

    public List<SnapshotQuery> build(
            List<NormalizedMatchParticipant> matchParticipants, String puuid, String patch
    ) {
        NormalizedMatchParticipant me = matchParticipants.stream()
                .filter(participant -> Objects.equals(participant.getPuuid(), puuid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("매치에 없는 참가자입니다: " + puuid));

        if (!me.isCoreItemPurchaseOrderComplete() || me.getCoreItemPurchaseOrder().isEmpty()) {
            return List.of();
        }
        Optional<ChampionPosition> position = positionNormalizer.normalize(me.getPosition());
        if (position.isEmpty()) {
            return List.of();
        }

        List<Long> purchaseOrder = me.getCoreItemPurchaseOrder().stream()
                .map(Long::valueOf)
                .toList();

        List<SnapshotQuery> snapshots = new ArrayList<>();
        for (int step = 0; step < purchaseOrder.size(); step++) {
            RecommendationQuery query = queryOf(matchParticipants, me, patch,
                    purchaseOrder.subList(0, step), position.get());
            snapshots.add(new SnapshotQuery(me.getMatchId(), step, query, purchaseOrder.get(step)));
        }
        return snapshots;
    }

    /**
     * {@link #build}와 같은 스냅샷에, 각 Core를 산 시각 <b>직전</b>의 10명 상태를 상황으로 싣는다.
     *
     * <p>스냅샷의 수, 구매 이력, 정답은 {@link #build}와 같다. 상황을 만들 수 없는 경우에도 스냅샷은 그대로 내고
     * 상황만 "모름"으로 둔다 — 표본이 달라지면 상황 없이 학습한 모델과 비교할 수 없다.
     *
     * <p>상황을 만들 수 없는 경우: 상태 타임라인이나 구매 시각을 얻지 못했을 때, 그리고 다시 계산한 구매 순서가
     * 저장된 순서와 다를 때다. 순서가 다른데 시각을 차례로 붙이면 다른 Core를 산 시각의 상황이 실린다.
     *
     * @param stateTimeline  이 경기의 상태 기록. 만들지 못했으면 비어 있다
     * @param myAcquisitions 이 참가자가 Core를 얻은 시각을 구매 순서대로. 얻지 못했으면 비어 있다
     */
    public List<SnapshotQuery> buildWithSituation(
            List<NormalizedMatchParticipant> matchParticipants, String puuid, String patch,
            Optional<MatchStateTimeline> stateTimeline, Optional<List<CoreItemAcquisition>> myAcquisitions
    ) {
        List<SnapshotQuery> snapshots = build(matchParticipants, puuid, patch);
        if (snapshots.isEmpty() || stateTimeline.isEmpty() || myAcquisitions.isEmpty()) {
            return snapshots;
        }
        NormalizedMatchParticipant me = matchParticipants.stream()
                .filter(participant -> Objects.equals(participant.getPuuid(), puuid))
                .findFirst()
                .orElseThrow();
        if (!followsStoredOrder(myAcquisitions.get(), me)) {
            return snapshots;
        }
        return snapshots.stream()
                .map(snapshot -> withSituation(snapshot, situationMapper.situationOf(
                        matchParticipants, me,
                        stateTimeline.get().before(myAcquisitions.get().get(snapshot.purchaseStep()).gameTimeMs()))))
                .toList();
    }

    private boolean followsStoredOrder(List<CoreItemAcquisition> acquisitions, NormalizedMatchParticipant me) {
        return acquisitions.stream().map(CoreItemAcquisition::itemId).toList().equals(me.getCoreItemPurchaseOrder());
    }

    private SnapshotQuery withSituation(SnapshotQuery snapshot, GameSituation situation) {
        RecommendationQuery query = snapshot.query();
        return new SnapshotQuery(snapshot.matchId(), snapshot.purchaseStep(),
                new RecommendationQuery(query.myChampionId(), query.position(), query.purchasedItemIds(),
                        query.allyChampionIds(), query.enemyChampionIds(), query.tier(), query.patch(),
                        query.coreItemIds(), situation),
                snapshot.groundTruthItemId());
    }

    private RecommendationQuery queryOf(List<NormalizedMatchParticipant> participants,
            NormalizedMatchParticipant me, String patch, List<Long> purchasedIds, ChampionPosition position) {
        return new RecommendationQuery(Long.valueOf(me.getChampionId()), position, purchasedIds,
                championIdsOf(participants, me, true), championIdsOf(participants, me, false), me.getTier(), patch,
                coreItemIds);
    }

    private List<Long> championIdsOf(
            List<NormalizedMatchParticipant> matchParticipants, NormalizedMatchParticipant me, boolean sameTeam
    ) {
        return matchParticipants.stream()
                .filter(participant -> !Objects.equals(participant.getPuuid(), me.getPuuid()))
                .filter(participant -> Objects.equals(participant.getTeamId(), me.getTeamId()) == sameTeam)
                .map(participant -> Long.valueOf(participant.getChampionId()))
                .toList();
    }
}
