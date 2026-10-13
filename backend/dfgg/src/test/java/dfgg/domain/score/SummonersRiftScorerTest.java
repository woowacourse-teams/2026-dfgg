package dfgg.domain.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.common.exception.InvalidDfScoreRequestException;
import dfgg.domain.champion.ChampionPosition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SummonersRiftScorerTest {

    private final SummonersRiftScorer scorer = new SummonersRiftScorer();

    private ScoringParticipant participant(int teamId, ChampionPosition position) {
        return new ScoringParticipant(teamId, position, teamId == 100,
                2, 4, 3, 20000, 18000, 11000, 20, 3000, 5000);
    }

    /** 팀마다 포지션 5개가 하나씩인 정상 경기. */
    private List<ScoringParticipant> validGame() {
        List<ScoringParticipant> participants = new ArrayList<>();
        for (int teamId : List.of(100, 200)) {
            for (ChampionPosition position : ChampionPosition.values()) {
                participants.add(participant(teamId, position));
            }
        }
        return participants;
    }

    @ParameterizedTest(name = "큐 {0}")
    @ValueSource(ints = {400, 420, 430, 440, 480, 490})
    @DisplayName("소환사의 협곡 일반·랭크 큐를 맡는다")
    void supports_WhenSummonersRiftQueue_ReturnTrue(int queueId) {
        // when
        boolean supported = scorer.supports(queueId);

        // then
        assertThat(supported).isTrue();
    }

    @ParameterizedTest(name = "큐 {0}")
    @ValueSource(ints = {450, 900, 1700, 0})
    @DisplayName("칼바람·URF·아레나 같은 다른 모드는 맡지 않는다")
    void supports_WhenOtherQueue_ReturnFalse(int queueId) {
        // when
        boolean supported = scorer.supports(queueId);

        // then
        assertThat(supported).isFalse();
    }

    @Test
    @DisplayName("두 팀이 포지션 5개를 하나씩 맡으면 통과한다")
    void validate_WhenEveryTeamHasEveryPositionOnce_Pass() {
        // given
        ScoringParticipants participants = new ScoringParticipants(validGame());

        // when & then
        assertThatCode(() -> scorer.validate(participants))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("포지션이 없는 참가자가 있으면 예외가 발생한다 — 협곡은 포지션별 기준으로 채점한다")
    void validate_WhenPositionIsMissing_ThrowInvalidDfScoreRequestException() {
        // given
        List<ScoringParticipant> game = validGame();
        game.set(0, participant(100, null));
        ScoringParticipants participants = new ScoringParticipants(game);

        // when & then
        assertThatThrownBy(() -> scorer.validate(participants))
                .isInstanceOf(InvalidDfScoreRequestException.class)
                .hasMessageContaining("포지션");
    }

    @Test
    @DisplayName("한 팀 안에 같은 포지션이 둘이면 예외가 발생한다")
    void validate_WhenPositionIsDuplicatedInTeam_ThrowInvalidDfScoreRequestException() {
        // given
        List<ScoringParticipant> game = validGame();
        game.set(4, participant(100, ChampionPosition.MID));
        ScoringParticipants participants = new ScoringParticipants(game);

        // when & then
        assertThatThrownBy(() -> scorer.validate(participants))
                .isInstanceOf(InvalidDfScoreRequestException.class)
                .hasMessageContaining("같은 포지션");
    }
}
