package dfgg.presentation.dto.response;

import dfgg.domain.score.MatchScores;
import dfgg.domain.score.ParticipantScore;
import dfgg.presentation.dto.ParticipantScoreDto;
import java.util.List;
import java.util.stream.IntStream;

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

    /** 점수는 요청 참가자 순서와 같다. */
    public static DfScoreResponse scored(List<String> puuids, MatchScores matchScores) {
        List<ParticipantScore> results = matchScores.results();
        List<ParticipantScoreDto> participants = IntStream.range(0, puuids.size())
                .mapToObj(index -> ParticipantScoreDto.scored(puuids.get(index), results.get(index)))
                .toList();
        return new DfScoreResponse(participants);
    }
}
