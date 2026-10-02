package dfgg.domain.recommendation;

import java.util.Objects;

/**
 * 최종 선택된 빌드 후보와 추천 표시 여부를 함께 보관한다.
 */
public record SelectedBuildCandidate(
        BuildCandidate candidate,
        boolean recommended
) {
    public SelectedBuildCandidate {
        Objects.requireNonNull(candidate, "선택된 빌드 후보는 null일 수 없습니다.");
    }
}
