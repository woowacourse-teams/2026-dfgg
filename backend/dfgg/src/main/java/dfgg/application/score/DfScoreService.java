package dfgg.application.score;

import dfgg.domain.score.MatchScorer;
import dfgg.domain.score.MatchScorers;
import dfgg.domain.score.ScoringParticipants;
import dfgg.domain.score.SummonersRiftScorer;
import dfgg.presentation.dto.request.DfScoreRequest;
import dfgg.presentation.dto.response.DfScoreResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 요청 값만으로 경기 점수를 매긴다. Riot API와 DB는 쓰지 않는다. 큐에 맞는 채점기를 골라 맡긴다.
 */
@Service
public class DfScoreService {

    private final MatchScorers matchScorers = new MatchScorers(List.of(new SummonersRiftScorer()));

    public DfScoreResponse score(DfScoreRequest request) {
        Optional<MatchScorer> scorer = matchScorers.find(request.queueId());
        if (scorer.isEmpty() || !request.toScoringMatch().isScorable()) {
            return DfScoreResponse.unscored(request.puuids());
        }
        ScoringParticipants participants = request.toScoringParticipants();
        scorer.get().validate(participants);
        // 점수 계산은 아직 없다 — 검증까지만 하고 점수 없이 응답한다.
        return DfScoreResponse.unscored(request.puuids());
    }
}
