package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchParticipant(
        String puuid,
        Integer championId,
        Integer teamId,
        String teamPosition,
        Integer item0,
        Integer item1,
        Integer item2,
        Integer item3,
        Integer item4,
        Integer item5,
        Boolean win,
        Integer participantId
) {

    /**
     * Keeps the pre-Timeline DTO construction form source-compatible.
     */
    public MatchParticipant(
            String puuid,
            Integer championId,
            Integer teamId,
            String teamPosition,
            Integer item0,
            Integer item1,
            Integer item2,
            Integer item3,
            Integer item4,
            Integer item5,
            Boolean win
    ) {
        this(
                puuid,
                championId,
                teamId,
                teamPosition,
                item0,
                item1,
                item2,
                item3,
                item4,
                item5,
                win,
                null
        );
    }
}
