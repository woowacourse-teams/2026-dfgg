package dfgg.domain.aram;

import dfgg.common.exception.AugmentDataRejectedException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 한 패치의 증강 묶음. OCR 조회가 이름으로 증강을 찾으므로 패치 안에서 이름이 겹치면 받지 않는다. */
public final class AugmentCatalog {

    private final List<Augment> augments;
    private final Set<Integer> augmentIds;

    private AugmentCatalog(List<Augment> augments) {
        this.augments = augments;
        this.augmentIds = augments.stream().map(Augment::augmentId).collect(Collectors.toUnmodifiableSet());
    }

    public static AugmentCatalog of(List<Augment> augments) {
        if (augments.isEmpty()) {
            throw new AugmentDataRejectedException("연결된 증강이 하나도 없습니다.");
        }
        for (int index = 0; index < augments.size(); index++) {
            rejectDuplicateName(augments.get(index), augments.subList(index + 1, augments.size()));
        }
        return new AugmentCatalog(List.copyOf(augments));
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

    public boolean contains(int augmentId) {
        return augmentIds.contains(augmentId);
    }

    public List<Augment> augments() {
        return augments;
    }
}
