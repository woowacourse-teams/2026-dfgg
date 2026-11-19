package dfgg.domain.score;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchScoresTest {

    private static final List<ChampionPosition> POSITIONS = List.of(ChampionPosition.values());

    private ScoringParticipant participant(int teamId, ChampionPosition position, int kills, int deaths, int assists) {
        return new ScoringParticipant(teamId, position, teamId == 100,
                kills, deaths, assists, 20000, 18000, 11000, 20, 3000, 5000);
    }

    /** 블루팀이 이긴 경기. 모두 2킬 4데스 3어시스트라 팀 킬은 10, KP는 0.5로 같다. */
    private List<ScoringParticipant> evenGame() {
        List<ScoringParticipant> participants = new ArrayList<>();
        for (int teamId : List.of(100, 200)) {
            for (ChampionPosition position : POSITIONS) {
                participants.add(participant(teamId, position, 2, 4, 3));
            }
        }
        return participants;
    }

    private List<DfScore> scores(double... values) {
        List<DfScore> scores = new ArrayList<>();
        for (double value : values) {
            scores.add(new DfScore(value));
        }
        return scores;
    }

    private List<Optional<Badge>> badges(MatchScores matchScores) {
        return matchScores.results().stream()
                .map(ParticipantScore::badge)
                .toList();
    }

    @Test
    @DisplayName("이긴 팀 최고점은 MVP, 진 팀 최고점은 ACE이고 나머지는 배지가 없다")
    void results_WhenScored_GiveMvpToWinnersTopAndAceToLosersTop() {
        // given
        ScoringParticipants participants = new ScoringParticipants(evenGame());

        // when
        MatchScores matchScores = new MatchScores(participants,
                scores(50, 60, 70, 40, 30, 20, 55, 45, 35, 25));

        // then
        assertThat(badges(matchScores)).containsExactly(
                Optional.empty(), Optional.empty(), Optional.of(Badge.MVP), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(Badge.ACE), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    @DisplayName("진 팀 최고점이 이긴 팀 최고점보다 높아도 ACE다 — 배지는 팀마다 하나다")
    void results_WhenLosersTopBeatsWinnersTop_StillAce() {
        // given
        ScoringParticipants participants = new ScoringParticipants(evenGame());

        // when
        MatchScores matchScores = new MatchScores(participants,
                scores(50, 60, 70, 40, 30, 90, 55, 45, 35, 25));

        // then
        assertThat(badges(matchScores).get(2)).contains(Badge.MVP);
        assertThat(badges(matchScores).get(5)).contains(Badge.ACE);
    }

    @Test
    @DisplayName("반올림한 점수가 같으면 반올림 전 점수가 높은 사람이 받는다")
    void results_WhenRoundedScoresTie_UseExactScore() {
        // given
        ScoringParticipants participants = new ScoringParticipants(evenGame());

        // when
        MatchScores matchScores = new MatchScores(participants,
                scores(70.21, 70.24, 50, 40, 30, 20, 30, 40, 50, 25));

        // then
        assertThat(badges(matchScores).get(1)).contains(Badge.MVP);
        assertThat(badges(matchScores).get(0)).isEmpty();
    }

    @Test
    @DisplayName("반올림 전 점수까지 같으면 KP가 높은 사람이 받는다")
    void results_WhenExactScoresTie_UseKillParticipation() {
        // given
        List<ScoringParticipant> game = evenGame();
        game.set(3, participant(100, ChampionPosition.BOTTOM, 4, 4, 3));
        ScoringParticipants participants = new ScoringParticipants(game);

        // when
        MatchScores matchScores = new MatchScores(participants,
                scores(70, 50, 40, 70, 30, 20, 30, 40, 50, 25));

        // then
        assertThat(badges(matchScores).get(3)).contains(Badge.MVP);
        assertThat(badges(matchScores).get(0)).isEmpty();
    }

    @Test
    @DisplayName("KP까지 같으면 데스가 적은 사람이 받는다")
    void results_WhenKillParticipationTies_UseFewerDeaths() {
        // given
        List<ScoringParticipant> game = evenGame();
        game.set(4, participant(100, ChampionPosition.SUPPORT, 2, 1, 3));
        ScoringParticipants participants = new ScoringParticipants(game);

        // when
        MatchScores matchScores = new MatchScores(participants,
                scores(70, 50, 40, 30, 70, 20, 30, 40, 50, 25));

        // then
        assertThat(badges(matchScores).get(4)).contains(Badge.MVP);
        assertThat(badges(matchScores).get(0)).isEmpty();
    }
}
