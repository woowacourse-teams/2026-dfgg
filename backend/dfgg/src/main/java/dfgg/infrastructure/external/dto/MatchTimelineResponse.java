package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchTimelineResponse(
        Metadata metadata,
        Info info
) {

    public Optional<String> puuidForParticipantId(Integer participantId) {
        if (participantId == null || participantId < 1
                || metadata == null
                || metadata.participants() == null
                || participantId > metadata.participants().size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(metadata.participants().get(participantId - 1));
    }

    public Optional<Integer> participantIdForPuuid(String puuid) {
        if (puuid == null || puuid.isBlank()
                || metadata == null
                || metadata.participants() == null) {
            return Optional.empty();
        }
        for (int index = 0; index < metadata.participants().size(); index++) {
            if (puuid.equals(metadata.participants().get(index))) {
                return Optional.of(index + 1);
            }
        }
        return Optional.empty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            List<String> participants
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Info(
            Long frameInterval,
            List<Frame> frames
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Frame(
            List<Event> events
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            Long timestamp,
            String type,
            Integer participantId,
            Integer itemId,
            Integer beforeId,
            Integer afterId
    ) {
    }
}
