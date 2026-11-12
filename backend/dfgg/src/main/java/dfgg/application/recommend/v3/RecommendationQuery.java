package dfgg.application.recommend.v3;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 4개 generator가 공유하는 추천 요청 정보. 서빙 요청에서도, 오프라인 평가의 게임 스냅샷에서도
 * 동일하게 만들어진다 — 학습과 서빙이 같은 입력 타입을 거치게 해 skew를 줄인다.
 *
 * <p>아군·적군 인원 수는 강제하지 않는다. 서빙 요청은 DTO에서 4/5로 검증되지만, 평가
 * 하네스는 실제 매치 데이터에서 만들기 때문에 불완전한 조합을 만날 수 있다. 대신 aggregation
 * feature(max/mean/sum)가 인원 수에 무관하게 동작하도록 설계한다.
 *
 * <p>{@code coreItemIds}는 generator가 통계를 읽을 때 조건으로 넘기는 Core 아이템 목록이다. 통계에 섞인
 * 시작 아이템·재료가 후보로 오지 않게 한다(#297). 서빙은 요청마다 한 번 채우고, 평가도 같은 목록을 넣는다.
 */
public record RecommendationQuery(
        Long myChampionId,
        ChampionPosition position,
        List<Long> purchasedItemIds,
        List<Long> allyChampionIds,
        List<Long> enemyChampionIds,
        String tier,
        String patch,
        Set<Long> coreItemIds
) {

    public RecommendationQuery {
        if (myChampionId == null) {
            throw new IllegalArgumentException("내 챔피언 ID는 필수입니다.");
        }
        Objects.requireNonNull(position, "포지션은 필수입니다.");
        if (patch == null || patch.isBlank()) {
            throw new IllegalArgumentException("패치는 필수입니다.");
        }
        if (enemyChampionIds.contains(myChampionId) || allyChampionIds.contains(myChampionId)) {
            throw new IllegalArgumentException(
                    "내 챔피언이 아군/적군 목록에도 들어있습니다: " + myChampionId);
        }
        purchasedItemIds = List.copyOf(purchasedItemIds);
        allyChampionIds = List.copyOf(allyChampionIds);
        enemyChampionIds = List.copyOf(enemyChampionIds);
        coreItemIds = Set.copyOf(coreItemIds);
    }

    /** 이미 산 코어 아이템 개수. 곧 이번 추천이 몇 번째 구매인지(purchase step)를 뜻한다. */
    public int purchasedItemCount() {
        return purchasedItemIds.size();
    }
}
