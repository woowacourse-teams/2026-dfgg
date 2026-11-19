package dfgg.application.recommend.v3;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * union된 후보 아이템 하나. 이 아이템을 발견한 generator마다 근거를 따로 들고 있다.
 *
 * <p>발견하지 못한 generator는 map에 키가 없다. 0.0을 채워넣지 않는 이유는
 * "counter가 0점으로 평가한 아이템"과 "counter가 아예 보지 않은 아이템"을 LTR이
 * 구분할 수 있어야 하기 때문이다.
 */
public record ItemCandidate(Long itemId, Map<CandidateSource, SourceEvidence> evidenceBySource) {

    public ItemCandidate {
        evidenceBySource = Map.copyOf(evidenceBySource);
    }

    public Optional<SourceEvidence> evidenceOf(CandidateSource source) {
        return Optional.ofNullable(evidenceBySource.get(source));
    }

    public boolean hasSource(CandidateSource source) {
        return evidenceBySource.containsKey(source);
    }

    public Set<CandidateSource> sources() {
        return evidenceBySource.keySet();
    }
}
