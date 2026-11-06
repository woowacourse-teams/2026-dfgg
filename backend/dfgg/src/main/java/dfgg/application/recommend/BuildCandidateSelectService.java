package dfgg.application.recommend;

import dfgg.domain.champion.ChampionTag;
import dfgg.domain.recommendation.BuildCandidate;
import dfgg.domain.recommendation.SelectedBuildCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

/**
 * 태그별 빌드 후보를 중복 제거하고 최종 선택 결과를 만든다.
 */
@Service
public final class BuildCandidateSelectService {

    public static final int MAX_SELECTED_CANDIDATES = 3;

    /**
     * 실제 군집이 서로 다르고 방향도 서로 다른 후보를 최대 3개 선택한다.
     */
    public List<SelectedBuildCandidate> select(List<BuildCandidate> candidates) {
        return select(candidates, ignored -> true);
    }

    /**
     * 우선 후보를 먼저 배치하고, 각 그룹 안에서는 적합도 점수가 높은 순서로 선택한다.
     */
    public List<SelectedBuildCandidate> select(
            List<BuildCandidate> candidates,
            Predicate<BuildCandidate> preferredCandidate
    ) {
        Objects.requireNonNull(candidates, "빌드 후보 목록은 null일 수 없습니다.");
        Objects.requireNonNull(preferredCandidate, "우선 후보 조건은 null일 수 없습니다.");

        List<BuildCandidate> deduplicatedCandidates = deduplicateByCluster(
                candidates,
                preferredCandidate
        );
        Comparator<BuildCandidate> ranking = Comparator
                .comparing((BuildCandidate candidate) -> preferredCandidate.test(candidate))
                .reversed()
                .thenComparing(
                        Comparator.comparingDouble(BuildCandidate::suitabilityScore)
                                .reversed()
                );
        List<BuildCandidate> rankedCandidates = deduplicatedCandidates.stream()
                .sorted(ranking)
                .toList();

        List<BuildCandidate> selectedCandidates = selectDistinctDirections(rankedCandidates);

        int recommendedIndex = selectedCandidates.isEmpty() ? -1 : 0;
        List<SelectedBuildCandidate> result = new ArrayList<>(selectedCandidates.size());
        for (int index = 0; index < selectedCandidates.size(); index++) {
            result.add(new SelectedBuildCandidate(
                    selectedCandidates.get(index),
                    index == recommendedIndex
            ));
        }
        return List.copyOf(result);
    }

    private List<BuildCandidate> deduplicateByCluster(
            List<BuildCandidate> candidates,
            Predicate<BuildCandidate> preferredCandidate
    ) {
        Map<List<Long>, BuildCandidate> candidatesByCluster = new LinkedHashMap<>();
        for (BuildCandidate candidate : candidates) {
            Objects.requireNonNull(candidate, "빌드 후보는 null일 수 없습니다.");

            List<Long> clusterKey = candidate.cluster().getClusterKey();
            BuildCandidate current = candidatesByCluster.get(clusterKey);
            if (current == null || isBetter(candidate, current, preferredCandidate)) {
                candidatesByCluster.put(clusterKey, candidate);
            }
        }
        return List.copyOf(candidatesByCluster.values());
    }

    private boolean isBetter(
            BuildCandidate candidate,
            BuildCandidate current,
            Predicate<BuildCandidate> preferredCandidate
    ) {
        boolean candidatePreferred = preferredCandidate.test(candidate);
        boolean currentPreferred = preferredCandidate.test(current);
        if (candidatePreferred != currentPreferred) {
            return candidatePreferred;
        }
        return candidate.suitabilityScore() > current.suitabilityScore();
    }

    private List<BuildCandidate> selectDistinctDirections(List<BuildCandidate> rankedCandidates) {
        List<BuildCandidate> selectedCandidates = new ArrayList<>();
        Set<DirectionKey> selectedDirections = new HashSet<>();

        for (BuildCandidate candidate : rankedCandidates) {
            if (selectedCandidates.size() == MAX_SELECTED_CANDIDATES) {
                break;
            }

            DirectionKey direction = DirectionKey.from(candidate);
            if (selectedDirections.add(direction)) {
                selectedCandidates.add(candidate);
            }
        }
        return selectedCandidates;
    }

    private record DirectionKey(ChampionTag championTag, String code) {
        private static DirectionKey from(BuildCandidate candidate) {
            return new DirectionKey(
                    candidate.direction().championTag(),
                    candidate.direction().code()
            );
        }
    }
}
