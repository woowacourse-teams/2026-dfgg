package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchInfo(
        String gameVersion,
        Integer queueId,
        List<MatchParticipant> participants
) {

    /**
     * Keeps callers that only need the participant list source-compatible.
     * Normalization still requires gameVersion and queueId from Match-v5.
     */
    public MatchInfo(List<MatchParticipant> participants) {
        this(null, null, participants);
    }
}
