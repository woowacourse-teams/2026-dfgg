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

class ScoringParticipantsTest {

    private static final List<ChampionPosition> POSITIONS = List.of(ChampionPosition.values());

    private ScoringParticipant participant(int teamId, ChampionPosition position, int kills, int assists) {
        return new ScoringParticipant(teamId, position, teamId == 100,
                kills, 4, assists, 20000, 18000, 11000, 20, 3000, 5000);
    }

    /** 팀마다 포지션 5개가 하나씩, 각자 킬 2·어시스트 3인 정상 경기. 팀 킬은 10이다. */
    private List<ScoringParticipant> validGame() {
        List<ScoringParticipant> participants = new ArrayList<>();
        for (int teamId : List.of(100, 200)) {
            for (ChampionPosition position : POSITIONS) {
                participants.add(participant(teamId, position, 2, 3));
            }
        }
        return participants;
    }

    @Test
    @DisplayName("두 팀이 포지션 5개를 하나씩 맡으면 만들어진다")
    void constructor_WhenTwoTeamsHaveEveryPositionOnce_Created() {
        // when & then
        assertThatCode(() -> new ScoringParticipants(validGame()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("한 팀이 5명이 아니면 예외가 발생한다")
    void constructor_WhenTeamIsNotFivePlayers_ThrowInvalidDfScoreRequestException() {
        // given
        List<ScoringParticipant> participants = validGame();
        participants.set(5, participant(100, ChampionPosition.TOP, 2, 3));

        // when & then
        assertThatThrownBy(() -> new ScoringParticipants(participants))
                .isInstanceOf(InvalidDfScoreRequestException.class)
                .hasMessageContaining("5명");
    }

    @Test
    @DisplayName("팀이 둘이 아니면 예외가 발생한다")
    void constructor_WhenNotTwoTeams_ThrowInvalidDfScoreRequestException() {
        // given
        List<ScoringParticipant> participants = validGame();
        for (int index = 5; index < 10; index++) {
            participants.set(index, participant(300, POSITIONS.get(index - 5), 2, 3));
        }
        participants.set(9, participant(400, ChampionPosition.SUPPORT, 2, 3));

        // when & then
        assertThatThrownBy(() -> new ScoringParticipants(participants))
                .isInstanceOf(InvalidDfScoreRequestException.class)
                .hasMessageContaining("두 팀");
    }

    @Test
    @DisplayName("한 팀 안에 같은 포지션이 둘이어도 만들어진다 — 포지션 규칙은 협곡 채점기가 따진다")
    void constructor_WhenPositionIsDuplicatedInTeam_Created() {
        // given
        List<ScoringParticipant> participants = validGame();
        participants.set(4, participant(100, ChampionPosition.MID, 2, 3));

        // when & then
        assertThatCode(() -> new ScoringParticipants(participants))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("킬과 어시스트의 합이 팀 킬보다 크면 예외가 발생한다")
    void constructor_WhenKillsAndAssistsExceedTeamKills_ThrowInvalidDfScoreRequestException() {
        // given
        List<ScoringParticipant> participants = validGame();
        participants.set(0, participant(100, ChampionPosition.TOP, 2, 9));

        // when & then
        assertThatThrownBy(() -> new ScoringParticipants(participants))
                .isInstanceOf(InvalidDfScoreRequestException.class)
                .hasMessageContaining("팀 킬");
    }

    @Test
    @DisplayName("킬과 어시스트의 합이 팀 킬과 같으면 만들어진다 — 모든 팀 킬에 관여한 경우다")
    void constructor_WhenKillsAndAssistsEqualTeamKills_Created() {
        // given
        List<ScoringParticipant> participants = validGame();
        participants.set(0, participant(100, ChampionPosition.TOP, 2, 8));

        // when & then
        assertThatCode(() -> new ScoringParticipants(participants))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("항목의 10명 평균을 구한다")
    void averageOf_WhenFeatureGiven_ReturnMeanOfTenParticipants() {
        // given
        List<ScoringParticipant> participants = validGame();
        participants.set(0, new ScoringParticipant(100, ChampionPosition.TOP, true,
                2, 4, 3, 30000, 18000, 11000, 20, 3000, 5000));
        ScoringParticipants match = new ScoringParticipants(participants);

        // when
        double average = match.averageOf(ScoreFeature.CHAMPION_DAMAGE);

        // then
        assertThat(average).isEqualTo(21000.0);
    }
}
