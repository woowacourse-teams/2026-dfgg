package dfgg.application.recommend.v3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * generator 하나가 낸 후보 목록. 점수 내림차순으로 정렬돼 있어야 하며,
 * rank는 그 순서에서 1부터 파생한다.
 *
 * <p>정렬을 여기서 대신 해주지 않고 어긋나면 거부한다. 조용히 재정렬하면 generator가
 * 잘못된 순서를 내보내도 드러나지 않는데, 그 순서는 곧 LTR의 rank feature가 된다.
 */
public record GeneratorResult(CandidateSource source, List<ScoredItem> rankedItems) {

    public GeneratorResult {
        validateDescendingByScore(rankedItems);
        validateNoDuplicateItem(rankedItems);
        rankedItems = List.copyOf(rankedItems);
    }

    public static GeneratorResult of(CandidateSource source, List<ScoredItem> rankedItems) {
        return new GeneratorResult(source, rankedItems);
    }

    /** 1부터 시작하는 rank. 목록에 없는 아이템이면 예외. */
    public int rankOf(long itemId) {
        for (int index = 0; index < rankedItems.size(); index++) {
            if (rankedItems.get(index).itemId() == itemId) {
                return index + 1;
            }
        }
        throw new IllegalArgumentException("후보 목록에 없는 아이템입니다: " + itemId);
    }

    public boolean isEmpty() {
        return rankedItems.isEmpty();
    }

    private static void validateDescendingByScore(List<ScoredItem> rankedItems) {
        for (int index = 1; index < rankedItems.size(); index++) {
            double previous = rankedItems.get(index - 1).score();
            double current = rankedItems.get(index).score();
            if (current > previous) {
                throw new IllegalArgumentException(
                        "후보는 점수 내림차순이어야 합니다: " + previous + " 뒤에 " + current);
            }
        }
    }

    private static void validateNoDuplicateItem(List<ScoredItem> rankedItems) {
        Set<Long> seen = new HashSet<>();
        for (ScoredItem item : rankedItems) {
            if (!seen.add(item.itemId())) {
                throw new IllegalArgumentException("한 generator 결과에 중복된 아이템이 있습니다: " + item.itemId());
            }
        }
    }
}
