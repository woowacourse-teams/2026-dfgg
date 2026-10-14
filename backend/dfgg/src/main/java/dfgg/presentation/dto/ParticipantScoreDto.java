package dfgg.presentation.dto;

import dfgg.domain.score.Badge;
import dfgg.domain.score.ParticipantScore;

/**
 * DF 점수 응답의 참가자 한 명. 채점 대상이 아닌 경기면 점수와 배지가 null이다.
 */
public record ParticipantScoreDto(
        String puuid,
        Double score,
        String badge
) {

    public static ParticipantScoreDto unscored(String puuid) {
        return new ParticipantScoreDto(puuid, null, null);
    }

    public static ParticipantScoreDto scored(String puuid, ParticipantScore result) {
        String badge = result.badge()
                .map(Badge::name)
                .orElse(null);
        return new ParticipantScoreDto(puuid, result.score().value(), badge);
    }
}
