package dfgg.evaluation;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.recommend.v3.GameSituation;
import dfgg.application.recommend.v3.MyState;
import dfgg.application.recommend.v3.PlayerState;
import dfgg.domain.match.MatchState;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.ParticipantState;
import java.util.List;
import java.util.Objects;

/**
 * 한 경기의 참가자들과 어느 시각 직전의 경기 상태를, 한 참가자 입장의 상황으로 옮긴다.
 *
 * <p>타임라인에서 복원한 상태는 참가자 번호만 안다. 누가 같은 팀인지, 어느 챔피언이고 어느 포지션인지는
 * 참가자 행에서 읽어 맞춘다. 서빙에서는 요청이 같은 일을 하고, 학습 데이터에서는 이 객체가 한다.
 */
public final class SnapshotSituationMapper {

    private final ChampionPositionNormalizer positionNormalizer = new ChampionPositionNormalizer();

    public GameSituation.Known situationOf(
            List<NormalizedMatchParticipant> matchParticipants, NormalizedMatchParticipant me, MatchState state
    ) {
        ParticipantState mine = state.of(me.getParticipantId());
        return GameSituation.of(
                new MyState(mine.level(), mine.kills(), mine.deaths(), mine.assists()),
                playersOf(matchParticipants, me, true, state),
                playersOf(matchParticipants, me, false, state));
    }

    private List<PlayerState> playersOf(
            List<NormalizedMatchParticipant> matchParticipants, NormalizedMatchParticipant me,
            boolean sameTeam, MatchState state
    ) {
        return matchParticipants.stream()
                .filter(participant -> !Objects.equals(participant.getPuuid(), me.getPuuid()))
                .filter(participant -> Objects.equals(participant.getTeamId(), me.getTeamId()) == sameTeam)
                .map(participant -> playerOf(participant, state.of(participant.getParticipantId())))
                .toList();
    }

    /** 포지션을 알 수 없으면 비워 둔다. 그 선수는 맞라인 상대로 잡히지 않을 뿐이고 나머지 값에는 그대로 들어간다. */
    private PlayerState playerOf(NormalizedMatchParticipant participant, ParticipantState state) {
        return new PlayerState(
                Long.valueOf(participant.getChampionId()),
                positionNormalizer.normalize(participant.getPosition()).orElse(null),
                state.level(), state.kills(), state.assists(),
                state.coreItemIds().stream().map(Long::valueOf).toList());
    }
}
