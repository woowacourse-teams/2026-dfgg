package dfgg.domain.score;

import static dfgg.domain.score.ScoreFeature.CHAMPION_DAMAGE;
import static dfgg.domain.score.ScoreFeature.DAMAGE_TAKEN_PER_LIFE;
import static dfgg.domain.score.ScoreFeature.GOLD;
import static dfgg.domain.score.ScoreFeature.TURRET_DAMAGE;

import java.util.List;

/**
 * 칼바람 채점기. 포지션 없이 10명 평균과 견주고, 항목 가중치는 모두 같다. 데스 페널티와 표준화는 없다.
 */
public class AramScorer implements MatchScorer {

    private static final int QUEUE_ID = 450;
    private static final int MINIMUM_DURATION_SECONDS = 480;
    private static final List<ScoreFeature> FEATURES = List.of(CHAMPION_DAMAGE, GOLD, DAMAGE_TAKEN_PER_LIFE, TURRET_DAMAGE);

    @Override
    public boolean supports(int queueId) {
        return queueId == QUEUE_ID;
    }

    @Override
    public boolean isLongEnough(GameDuration duration) {
        return duration.isAtLeast(MINIMUM_DURATION_SECONDS);
    }

    /** 칼바람은 포지션이 없어 공통 검증 외에 따질 것이 없다. */
    @Override
    public void validate(ScoringParticipants participants) {
    }

    @Override
    public List<DfScore> score(ScoringParticipants participants, GameDuration duration) {
        return participants.mapEach(participant -> new DfScore(BaseScore.of(
                individualPerformance(participant, participants),
                participants.teamContributionOf(participant).score())));
    }

    private double individualPerformance(ScoringParticipant participant, ScoringParticipants match) {
        return FEATURES.stream()
                .mapToDouble(feature -> ScoreCurve.compare(participant.statOf(feature), match.averageOf(feature)))
                .average()
                .orElseThrow();
    }
}
