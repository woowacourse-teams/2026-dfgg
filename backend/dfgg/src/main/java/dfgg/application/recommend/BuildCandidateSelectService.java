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
        Objects.requireNonNull(candidates, "빌드 후보 목록은 null일 수 없습니다.");

        List<BuildCandidate> deduplicatedCandidates = deduplicateByCluster(candidates);
        List<BuildCandidate> rankedCandidates = deduplicatedCandidates.stream()
                .sorted(Comparator.comparingDouble(BuildCandidate::suitabilityScore).reversed())
                .toList();

        List<BuildCandidate> selectedCandidates = selectDistinctDirections(rankedCandidates);

        int recommendedIndex = findRecommendedIndex(selectedCandidates);
        List<SelectedBuildCandidate> result = new ArrayList<>(selectedCandidates.size());
        for (int index = 0; index < selectedCandidates.size(); index++) {
            result.add(new SelectedBuildCandidate(
                    selectedCandidates.get(index),
                    index == recommendedIndex
            ));
        }
        return List.copyOf(result);
    }

    private List<BuildCandidate> deduplicateByCluster(List<BuildCandidate> candidates) {
        Map<List<Long>, BuildCandidate> candidatesByCluster = new LinkedHashMap<>();
        for (BuildCandidate candidate : candidates) {
            Objects.requireNonNull(candidate, "빌드 후보는 null일 수 없습니다.");

            List<Long> clusterKey = candidate.cluster().getClusterKey();
            BuildCandidate current = candidatesByCluster.get(clusterKey);
            if (current == null || candidate.suitabilityScore() > current.suitabilityScore()) {
                candidatesByCluster.put(clusterKey, candidate);
            }
        }
        return List.copyOf(candidatesByCluster.values());
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

    private int findRecommendedIndex(List<BuildCandidate> selectedCandidates) {
        if (selectedCandidates.isEmpty()) {
            return -1;
        }

        int recommendedIndex = 0;
        for (int index = 1; index < selectedCandidates.size(); index++) {
            if (selectedCandidates.get(index).suitabilityScore()
                    > selectedCandidates.get(recommendedIndex).suitabilityScore()) {
                recommendedIndex = index;
            }
        }
        return recommendedIndex;
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
