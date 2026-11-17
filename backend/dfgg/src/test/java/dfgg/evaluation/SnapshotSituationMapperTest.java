package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.recommend.v3.GameSituation;
import dfgg.application.recommend.v3.MyState;
import dfgg.application.recommend.v3.PlayerState;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.CoreItemChange;
import dfgg.domain.match.MatchState;
import dfgg.domain.match.MatchStateTimeline;
import dfgg.domain.match.NormalizedMatchParticipant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnapshotSituationMapperTest {

    private static final long ANY_TIME_AFTER_EVERYTHING = 1_000_000L;

    private final SnapshotSituationMapper mapper = new SnapshotSituationMapper();

    private final NormalizedMatchParticipant me = participant("me", 1, 157, 100, "MIDDLE");
    private final List<NormalizedMatchParticipant> match = List.of(
            me,
            participant("ally1", 2, 222, 100, "BOTTOM"),
            participant("ally2", 3, 412, 100, "UTILITY"),
            participant("enemy1", 6, 103, 200, "MIDDLE"),
            participant("enemy2", 7, 33, 200, "TOP")
    );

    private static NormalizedMatchParticipant participant(
            String puuid, int participantId, int championId, int teamId, String position
    ) {
        return new NormalizedMatchParticipant(
                puuid, participantId, championId, teamId, position, "EMERALD", true, List.of(), List.of(), true);
    }

    private static MatchState stateOf(MatchStateTimeline.Event... events) {
        return new MatchStateTimeline(List.of(events)).before(ANY_TIME_AFTER_EVERYTHING);
    }

    private static MatchStateTimeline.Kill kill(int killerId, int victimId, Integer... assistingParticipantIds) {
        return new MatchStateTimeline.Kill(1_000L, killerId, victimId, List.of(assistingParticipantIds));
    }

    private static MatchStateTimeline.CoreItemChanged bought(int participantId, int itemId) {
        return new MatchStateTimeline.CoreItemChanged(2_000L, participantId, new CoreItemChange(2_000L, null, itemId));
    }

    @Test
    @DisplayName("같은 팀은 아군으로, 다른 팀은 상대로 옮기고 나는 아군에 넣지 않는다")
    void situationOf_WhenParticipantsAreOnTwoTeams_SplitsByTeamAndExcludesMe() {
        // when
        GameSituation.Known situation = mapper.situationOf(match, me, stateOf());

        // then
        assertThat(situation.allies()).extracting(PlayerState::championId).containsExactly(222L, 412L);
        assertThat(situation.enemies()).extracting(PlayerState::championId).containsExactly(103L, 33L);
    }

    @Test
    @DisplayName("내 레벨·킬·데스·어시스트는 내 참가자 번호의 상태에서 옮긴다")
    void situationOf_WhenMyStateIsRecorded_ReadsItByMyParticipantId() {
        // given: 내(1번)가 6번을 잡았고, 6번이 나를 잡았고, 2번이 6번을 잡을 때 내가 도왔다. 나는 5레벨이다
        MatchState state = stateOf(
                kill(1, 6),
                kill(6, 1),
                kill(2, 6, 1),
                new MatchStateTimeline.LevelUp(3_000L, 1, 5));

        // when
        GameSituation.Known situation = mapper.situationOf(match, me, state);

        // then
        assertThat(situation.me()).isEqualTo(new MyState(5, 1, 1, 1));
    }

    @Test
    @DisplayName("아군·상대의 상태는 각자의 참가자 번호로 찾는다 — 타임라인은 참가자 번호만 안다")
    void situationOf_WhenOtherPlayersStateIsRecorded_ReadsEachByParticipantId() {
        // given: 상대 6번(아리)이 나를 두 번 잡았고 3레벨이며 3089를 샀다. 아군 2번(징크스)이 6번을 한 번 잡았다
        MatchState state = stateOf(
                kill(6, 1),
                kill(6, 1),
                kill(2, 6, 3),
                new MatchStateTimeline.LevelUp(3_000L, 6, 3),
                bought(6, 3089));

        // when
        GameSituation.Known situation = mapper.situationOf(match, me, state);

        // then
        assertThat(situation.enemies().get(0))
                .isEqualTo(new PlayerState(103L, ChampionPosition.MID, 3, 2, 0, List.of(3089L)));
        assertThat(situation.allies().get(0))
                .isEqualTo(new PlayerState(222L, ChampionPosition.BOTTOM, 1, 1, 0, List.of()));
        assertThat(situation.allies().get(1).assists()).isEqualTo(1);
    }

    @Test
    @DisplayName("포지션을 알 수 없는 선수는 포지션을 비워 옮긴다 — 그 선수 때문에 상황 전체를 버리지 않는다")
    void situationOf_WhenPlayerPositionUnknown_LeavesPositionEmpty() {
        // given
        List<NormalizedMatchParticipant> withUnknownPosition = List.of(
                me, participant("enemy", 6, 103, 200, ""));

        // when
        GameSituation.Known situation = mapper.situationOf(withUnknownPosition, me, stateOf());

        // then
        PlayerState enemy = situation.enemies().get(0);
        assertThat(enemy.position()).isNull();
        assertThat(enemy.playsAt(ChampionPosition.MID)).isFalse();
    }
}
