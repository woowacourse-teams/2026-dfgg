package dfgg.domain.score;

import dfgg.common.exception.InvalidDfScoreRequestException;
import java.util.List;
import java.util.Set;

/**
 * 소환사의 협곡 채점기. 포지션별 상수로 기준을 잡으므로 두 팀이 포지션 5개를 하나씩 맡아야 한다.
 */
public class SummonersRiftScorer implements MatchScorer {

    /** 교차 선택(400), 솔로 랭크(420), 비공개 선택(430), 자유 랭크(440), 신속 대전(480), 빠른 대전(490). */
    private static final Set<Integer> QUEUE_IDS = Set.of(400, 420, 430, 440, 480, 490);

    private static final double INDIVIDUAL_WEIGHT = 0.6;
    private static final double TEAM_WEIGHT = 0.4;
    private static final double BASE_SCORE_CENTER = 50;
    private static final double BASE_SCORE_SPREAD = 150;
    /** 분당 허용 데스는 포지션과 관계없이 하나다. 분당 데스의 75번째 백분위(30분이면 8.4데스). */
    private static final DeathPenalty DEATH_PENALTY = new DeathPenalty(0.278);

    @Override
    public boolean supports(int queueId) {
        return QUEUE_IDS.contains(queueId);
    }

    @Override
    public void validate(ScoringParticipants participants) {
        if (!participants.everyoneHasPosition()) {
            throw new InvalidDfScoreRequestException("협곡 경기는 참가자마다 포지션이 필요합니다");
        }
        if (!participants.eachTeamHasDistinctPositions()) {
            throw new InvalidDfScoreRequestException("한 팀 안에 같은 포지션이 둘 이상입니다");
        }
    }

    @Override
    public List<DfScore> score(ScoringParticipants participants, GameDuration duration) {
        validate(participants);
        return participants.mapEach(participant -> scoreOf(participant, participants, duration));
    }

    /** 기본점수 = 50 + 150 × (0.6 × A + 0.4 × B − 0.5) − 데스 페널티를 포지션별로 표준화한다. */
    private DfScore scoreOf(ScoringParticipant participant, ScoringParticipants match, GameDuration duration) {
        PositionScoreProfile profile = PositionScoreProfile.of(participant.position());
        double performance = INDIVIDUAL_WEIGHT * profile.individualPerformance(participant, match)
                + TEAM_WEIGHT * match.teamContributionOf(participant).score();
        double baseScore = BASE_SCORE_CENTER + BASE_SCORE_SPREAD * (performance - 0.5)
                - DEATH_PENALTY.of(participant, duration);
        return profile.standardize(baseScore);
    }
}
