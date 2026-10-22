package dfgg.domain.score.summonersrift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.common.exception.InvalidDfScoreRequestException;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.score.DfScore;
import dfgg.domain.score.GameDuration;
import dfgg.domain.score.ScoringParticipant;
import dfgg.domain.score.ScoringParticipants;
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

    @Test
    @DisplayName("참조 경기의 10명 점수는 참조 구현과 같고, 참가자 순서를 지킨다")
    void score_WhenReferenceMatch_EqualReferenceScoresInOrder() {
        // given
        ScoringParticipants match = new ScoringParticipants(List.of(
                new ScoringParticipant(100, ChampionPosition.TOP, true, 5, 3, 4, 25000, 30000, 13000, 18, 6000, 7000),
                new ScoringParticipant(100, ChampionPosition.JUNGLE, true, 6, 4, 10, 18000, 35000, 12000, 40, 1000, 25000),
                new ScoringParticipant(100, ChampionPosition.MID, true, 7, 2, 6, 30000, 15000, 13500, 22, 4000, 5000),
                new ScoringParticipant(100, ChampionPosition.BOTTOM, true, 8, 3, 5, 32000, 14000, 14500, 15, 5000, 9000),
                new ScoringParticipant(100, ChampionPosition.SUPPORT, true, 1, 4, 18, 9000, 16000, 8500, 80, 500, 800),
                new ScoringParticipant(200, ChampionPosition.TOP, false, 3, 6, 3, 20000, 32000, 11000, 15, 3000, 3500),
                new ScoringParticipant(200, ChampionPosition.JUNGLE, false, 4, 7, 6, 15000, 33000, 10000, 35, 0, 18000),
                new ScoringParticipant(200, ChampionPosition.MID, false, 5, 6, 4, 24000, 18000, 11500, 20, 2000, 2500),
                new ScoringParticipant(200, ChampionPosition.BOTTOM, false, 4, 7, 3, 22000, 15000, 11000, 12, 1500, 4000),
                new ScoringParticipant(200, ChampionPosition.SUPPORT, false, 0, 6, 10, 7000, 17000, 7000, 70, 0, 300)));

        // when
        List<DfScore> scores = scorer.score(match, new GameDuration(1800));

        // then
        assertThat(scores).extracting(DfScore::value)
                .containsExactly(66.7, 66.1, 74.5, 66.1, 67.8, 47.2, 49.3, 54.9, 40.2, 46.4);
    }

    @Test
    @DisplayName("15분(900초) 이상이면 채점한다")
    void isLongEnough_WhenFifteenMinutes_ReturnTrue() {
        // when
        boolean longEnough = scorer.isLongEnough(new GameDuration(900));

        // then
        assertThat(longEnough).isTrue();
    }

    @Test
    @DisplayName("15분 미만이면 채점하지 않는다")
    void isLongEnough_WhenShorterThanFifteenMinutes_ReturnFalse() {
        // when
        boolean longEnough = scorer.isLongEnough(new GameDuration(899));

        // then
        assertThat(longEnough).isFalse();
    }
}
