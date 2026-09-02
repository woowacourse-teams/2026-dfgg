package dfgg.application.recommend.v3;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * 4개 generator의 결과를 아이템 단위로 합친 후보 집합.
 *
 * <p>합치는 규칙은 하나뿐이다 — 같은 아이템은 하나로 모으되 어느 generator가 몇 위로 몇 점을
 * 줬는지는 전부 남긴다. 여기서 점수를 섞거나 가중합하지 않는다. 그 trade-off는 LTR이 학습한다.
 *
 * <p>후보 순서는 아이템 ID 오름차순으로 고정한다. generator 실행 순서나 map 순회 순서가
 * 결과에 새어나오면 같은 입력에 다른 추천이 나올 수 있다.
 */
public record CandidateUnion(List<ItemCandidate> candidates) {

    public CandidateUnion {
        candidates = List.copyOf(candidates);
    }

    public static CandidateUnion merge(List<GeneratorResult> generatorResults) {
        validateNoDuplicateSource(generatorResults);

        Map<Long, Map<CandidateSource, SourceEvidence>> evidenceByItemId = new HashMap<>();
        for (GeneratorResult generatorResult : generatorResults) {
            collectEvidence(generatorResult, evidenceByItemId);
        }

        List<ItemCandidate> candidates = evidenceByItemId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ItemCandidate(entry.getKey(), entry.getValue()))
                .toList();
        return new CandidateUnion(candidates);
    }

    public ItemCandidate candidateOf(long itemId) {
        return candidates.stream()
                .filter(candidate -> candidate.itemId() == itemId)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("union에 없는 아이템입니다: " + itemId));
    }

    public boolean isEmpty() {
        return candidates.isEmpty();
    }

    public int size() {
        return candidates.size();
    }

    /**
     * 같은 source의 결과가 두 번 들어오면 뒤엣것이 앞엣것을 덮어써 후보가 조용히 사라진다.
     * generator 배선 실수는 여기서 터뜨려야 추천 결과를 보고 역추적하는 일이 없다.
     */
    private static void validateNoDuplicateSource(List<GeneratorResult> generatorResults) {
        Set<CandidateSource> seen = EnumSet.noneOf(CandidateSource.class);
        for (GeneratorResult generatorResult : generatorResults) {
            if (!seen.add(generatorResult.source())) {
                throw new IllegalArgumentException(
                        "같은 source의 generator 결과가 중복으로 들어왔습니다: " + generatorResult.source());
            }
        }
    }

    private static void collectEvidence(
            GeneratorResult generatorResult,
            Map<Long, Map<CandidateSource, SourceEvidence>> evidenceByItemId
    ) {
        List<ScoredItem> rankedItems = generatorResult.rankedItems();
        for (int index = 0; index < rankedItems.size(); index++) {
            ScoredItem scoredItem = rankedItems.get(index);
            evidenceByItemId
                    .computeIfAbsent(scoredItem.itemId(), itemId -> new EnumMap<>(CandidateSource.class))
                    .put(generatorResult.source(),
                            new SourceEvidence(scoredItem.score(), index + 1, generatorResult.backoffLevel()));
        }
    }
}
