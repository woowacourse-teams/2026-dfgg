package dfgg.presentation.dto.response;

import dfgg.presentation.dto.ParticipantScoreDto;
import java.util.List;

public record DfScoreResponse(
        List<ParticipantScoreDto> participants
) {

    /** 채점 대상이 아닌 경기. 요청 순서대로 모든 참가자의 점수·배지를 비운다. */
    public static DfScoreResponse unscored(List<String> puuids) {
        List<ParticipantScoreDto> participants = puuids.stream()
                .map(ParticipantScoreDto::unscored)
                .toList();
        return new DfScoreResponse(participants);
    }
}
