package dfgg.application.score;

import dfgg.domain.score.AramScorer;
import dfgg.domain.score.GameDuration;
import dfgg.domain.score.MatchScorer;
import dfgg.domain.score.MatchScorers;
import dfgg.domain.score.MatchScores;
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

    private final MatchScorers matchScorers = new MatchScorers(List.of(new SummonersRiftScorer(), new AramScorer()));

    public DfScoreResponse score(DfScoreRequest request) {
        Optional<MatchScorer> scorer = matchScorers.find(request.queueId());
        GameDuration duration = request.toGameDuration();
        if (!isScorable(scorer, request, duration)) {
            return DfScoreResponse.unscored(request.puuids());
        }
        ScoringParticipants participants = request.toScoringParticipants();
        MatchScores matchScores = new MatchScores(participants, scorer.get().score(participants, duration));
        return DfScoreResponse.scored(request.puuids(), matchScores);
    }

    /** 큐를 맡는 채점기가 있고, 10명이 한 경기이며, 그 모드의 최소 게임 시간을 넘어야 채점한다. */
    private boolean isScorable(Optional<MatchScorer> scorer, DfScoreRequest request, GameDuration duration) {
        if (scorer.isEmpty() || !request.toScoringMatch().isFullGame()) {
            return false;
        }
        return scorer.get().isLongEnough(duration);
    }
}
