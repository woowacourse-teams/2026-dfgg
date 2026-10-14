package dfgg.presentation.dto.request;

import dfgg.domain.score.GameDuration;
import dfgg.domain.score.ScoringMatch;
import dfgg.domain.score.ScoringParticipants;
import dfgg.presentation.dto.ParticipantStatsDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * 끝난 경기 한 판의 원시 값. 비율이나 KDA 같은 가공 값은 받지 않는다 — 서버가 계산해야 상수를 측정할 때와 정의가 같다.
 */
public record DfScoreRequest(
        @NotNull(message = "큐 ID는 필수입니다")
        Integer queueId,

        @NotNull(message = "게임 시간은 필수입니다")
        @PositiveOrZero(message = "게임 시간은 0 이상이어야 합니다")
        Integer gameDurationSec,

        @NotNull(message = "참가자 목록은 필수입니다")
        List<@NotNull(message = "참가자에 null이 들어갈 수 없습니다") @Valid ParticipantStatsDto> participants
) {

    public ScoringMatch toScoringMatch() {
        return new ScoringMatch(gameDurationSec, participants.size());
    }

    public GameDuration toGameDuration() {
        return new GameDuration(gameDurationSec);
    }

    public ScoringParticipants toScoringParticipants() {
        return new ScoringParticipants(participants.stream()
                .map(ParticipantStatsDto::toScoringParticipant)
                .toList());
    }

    public List<String> puuids() {
        return participants.stream()
                .map(ParticipantStatsDto::puuid)
                .toList();
    }
}
