package dfgg.application.recommend.component;

import dfgg.application.recommend.context.CompositionFeatures;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.ItemInventory;
import java.util.List;
import java.util.Objects;

/**
 * Core가 정한 목표 목록과 동일 시점의 현재 상태를 Component 추천에 전달한다.
 * 평가 입력의 포지션 결측은 UNKNOWN 피처로 유지한다.
 * championId가 없는 기존 평가 입력은 정책 조회표를 사용하지 않을 때만 서비스에서 허용한다.
 */
public record ComponentRecommendationQuery(
        Integer championId,
        List<Integer> targetItemIds,
        ItemInventory inventory,
        long availableGold,
        long gameTimeMs,
        ChampionPosition position,
        String patch,
        CompositionFeatures composition
) {
    public ComponentRecommendationQuery {
        Objects.requireNonNull(composition, "경기 조합은 필수입니다.");
        if (championId != null && championId <= 0) {
            throw new IllegalArgumentException("Champion ID는 양수여야 합니다.");
        }
        if (targetItemIds == null || targetItemIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("목표 목록과 양수인 목표 아이템 ID가 필요합니다.");
        }
        if (inventory == null || availableGold < 0 || gameTimeMs < 0) {
            throw new IllegalArgumentException("현재 Inventory, 예산, 시간이 필요합니다.");
        }
        if (patch == null || !patch.matches("\\d+\\.\\d+")) {
            throw new IllegalArgumentException("패치는 major.minor 형식이어야 합니다.");
        }
        targetItemIds = List.copyOf(targetItemIds);
    }

    public String positionName() {
        return position == null ? "UNKNOWN" : position.name();
    }
}
