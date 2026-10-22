package dfgg.domain.score;

import java.util.List;
import java.util.Optional;

/**
 * 등록된 채점기들. 요청의 큐를 맡는 채점기가 없으면 그 경기는 채점 대상이 아니다.
 */
public class MatchScorers {

    private final List<MatchScorer> scorers;

    public MatchScorers(List<MatchScorer> scorers) {
        this.scorers = List.copyOf(scorers);
    }

    public Optional<MatchScorer> find(int queueId) {
        return scorers.stream()
                .filter(scorer -> scorer.supports(queueId))
                .findFirst();
    }
}
