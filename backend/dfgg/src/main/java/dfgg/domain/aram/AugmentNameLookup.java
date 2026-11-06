package dfgg.domain.aram;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 화면에서 읽은 이름을 카탈로그의 증강과 못 찾은 이름으로 나눈다. 찾은 증강은 받은 순서를 유지한다. */
public final class AugmentNameLookup {

    private final List<Augment> augments;
    private final List<String> unmatchedNames;

    private AugmentNameLookup(List<Augment> augments, List<String> unmatchedNames) {
        this.augments = augments;
        this.unmatchedNames = unmatchedNames;
    }

    public static AugmentNameLookup of(List<Augment> catalog, List<String> names) {
        List<Augment> augments = new ArrayList<>();
        List<String> unmatchedNames = new ArrayList<>();
        for (String name : names) {
            find(catalog, name).ifPresentOrElse(augments::add, () -> unmatchedNames.add(name));
        }
        return new AugmentNameLookup(List.copyOf(augments), List.copyOf(unmatchedNames));
    }

    private static Optional<Augment> find(List<Augment> catalog, String name) {
        return catalog.stream()
                .filter(augment -> augment.isNamed(name))
                .findFirst();
    }

    public List<Augment> augments() {
        return augments;
    }

    public List<String> unmatchedNames() {
        return unmatchedNames;
    }
}
