package dfgg.evaluation;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.NormalizedMatchParticipant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

        List<Long> allyChampionIds = championIdsOf(matchParticipants, me, true);
        List<Long> enemyChampionIds = championIdsOf(matchParticipants, me, false);
        List<Long> purchaseOrder = me.getCoreItemPurchaseOrder().stream()
                .map(Long::valueOf)
                .toList();

        List<SnapshotQuery> snapshots = new ArrayList<>();
        for (int step = 0; step < purchaseOrder.size(); step++) {
            RecommendationQuery query = new RecommendationQuery(
                    Long.valueOf(me.getChampionId()), position.get(),
                    purchaseOrder.subList(0, step),
                    allyChampionIds, enemyChampionIds,
                    me.getTier(), patch
            );
            snapshots.add(new SnapshotQuery(me.getMatchId(), step, query, purchaseOrder.get(step)));
        }
        return snapshots;
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
