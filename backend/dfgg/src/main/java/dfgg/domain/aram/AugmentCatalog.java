package dfgg.domain.aram;

import dfgg.common.exception.AugmentDataRejectedException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 한 패치의 증강 묶음. 저장하면 그 패치의 기존 데이터를 교체하므로, 일부만 남은 묶음은 받지 않는다.
 * <p>
 * OCR 조회가 이름으로 증강을 찾으므로 패치 안에서 이름이 겹쳐도 받지 않는다.
 */
public final class AugmentCatalog {

    private static final int MINIMUM_COVERAGE_PERCENT = 95;

    private final List<Augment> augments;
    private final Set<Integer> augmentIds;
    private final int expectedCount;

    private AugmentCatalog(List<Augment> augments, int expectedCount) {
        this.augments = augments;
        this.augmentIds = augments.stream().map(Augment::augmentId).collect(Collectors.toUnmodifiableSet());
        this.expectedCount = expectedCount;
    }

    /** @param expectedCount 픽률 데이터에 있는 증강 수. 연결률의 분모다 */
    public static AugmentCatalog of(List<Augment> augments, int expectedCount) {
        if (augments.isEmpty()) {
            throw new AugmentDataRejectedException("연결된 증강이 하나도 없습니다.");
        }
        rejectLowCoverage(augments.size(), expectedCount);
        rejectMissingRarity(augments);
        for (int index = 0; index < augments.size(); index++) {
            rejectDuplicateName(augments.get(index), augments.subList(index + 1, augments.size()));
        }
        return new AugmentCatalog(List.copyOf(augments), expectedCount);
    }

    private static void rejectLowCoverage(int linkedCount, int expectedCount) {
        if (linkedCount * 100L < expectedCount * (long) MINIMUM_COVERAGE_PERCENT) {
            throw new AugmentDataRejectedException("증강 연결률이 낮습니다: " + linkedCount + "/" + expectedCount);
        }
    }

    private static void rejectMissingRarity(List<Augment> augments) {
        Set<AugmentRarity> rarities = augments.stream().map(Augment::rarity).collect(Collectors.toSet());
        if (rarities.size() < AugmentRarity.values().length) {
            throw new AugmentDataRejectedException("없는 증강 등급이 있습니다: " + rarities);
        }
    }

    private static void rejectDuplicateName(Augment augment, List<Augment> others) {
        others.stream()
                .filter(augment::sharesNameWith)
                .findFirst()
                .ifPresent(other -> {
                    throw new AugmentDataRejectedException(
                            "증강 이름이 겹칩니다: " + augment.augmentId() + ", " + other.augmentId());
                });
    }

    /** 빼고 남은 묶음도 같은 기준을 지켜야 한다. */
    public AugmentCatalog excluding(Set<Integer> excludedIds) {
        List<Augment> remaining = augments.stream()
                .filter(augment -> !excludedIds.contains(augment.augmentId()))
                .toList();
        return of(remaining, expectedCount);
    }

    public boolean contains(int augmentId) {
        return augmentIds.contains(augmentId);
    }

    public List<Augment> augments() {
        return augments;
    }
}
