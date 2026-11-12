package dfgg.domain.recommendation;

public record BuildCandidate(
        BuildDirection direction,
        CoreBuildCluster cluster,
        double suitabilityScore
) {
    public BuildCandidate {
        if (!Double.isFinite(suitabilityScore)) {
            throw new IllegalArgumentException("빌드 적합도는 유한한 숫자여야 합니다.");
        }
    }
}
