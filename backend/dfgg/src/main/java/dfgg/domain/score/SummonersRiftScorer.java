package dfgg.domain.score;

import dfgg.common.exception.InvalidDfScoreRequestException;
import java.util.Set;

/**
 * 소환사의 협곡 채점기. 포지션별 상수로 기준을 잡으므로 두 팀이 포지션 5개를 하나씩 맡아야 한다.
 */
public class SummonersRiftScorer implements MatchScorer {

    /** 교차 선택(400), 솔로 랭크(420), 비공개 선택(430), 자유 랭크(440), 신속 대전(480), 빠른 대전(490). */
    private static final Set<Integer> QUEUE_IDS = Set.of(400, 420, 430, 440, 480, 490);

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
}
