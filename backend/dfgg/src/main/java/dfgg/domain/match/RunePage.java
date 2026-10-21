package dfgg.domain.match;

import java.util.List;

/**
 * 한 참가자가 경기에 들고 간 룬 페이지. 룬은 게임 화면의 선택 순서대로 담는다.
 * 주 계열은 룬 4개를 고르고 그중 첫 번째가 핵심 룬이다. 보조 계열은 주 계열과 다른 계열에서 룬 2개를 고른다.
 */
public record RunePage(
        int primaryStyleId,
        List<Integer> primaryPerkIds,
        int subStyleId,
        List<Integer> subPerkIds,
        StatShards statShards
) {

    private static final int PRIMARY_PERK_COUNT = 4;
    private static final int SUB_PERK_COUNT = 2;

    public RunePage {
        primaryPerkIds = List.copyOf(primaryPerkIds);
        subPerkIds = List.copyOf(subPerkIds);
        if (primaryPerkIds.size() != PRIMARY_PERK_COUNT) {
            throw new IllegalArgumentException("주 계열 룬은 4개여야 합니다: " + primaryPerkIds);
        }
        if (subPerkIds.size() != SUB_PERK_COUNT) {
            throw new IllegalArgumentException("보조 계열 룬은 2개여야 합니다: " + subPerkIds);
        }
        if (primaryStyleId == subStyleId) {
            throw new IllegalArgumentException("주 계열과 보조 계열은 달라야 합니다: " + primaryStyleId);
        }
        validatePositive(primaryStyleId, subStyleId, primaryPerkIds, subPerkIds);
    }

    public int keystoneId() {
        return primaryPerkIds.getFirst();
    }

    private static void validatePositive(int primaryStyleId, int subStyleId,
                                         List<Integer> primaryPerkIds, List<Integer> subPerkIds) {
        boolean hasNonPositivePerk = primaryPerkIds.stream().anyMatch(id -> id <= 0)
                || subPerkIds.stream().anyMatch(id -> id <= 0);
        if (primaryStyleId <= 0 || subStyleId <= 0 || hasNonPositivePerk) {
            throw new IllegalArgumentException("룬과 계열 ID는 양수여야 합니다.");
        }
    }
}
