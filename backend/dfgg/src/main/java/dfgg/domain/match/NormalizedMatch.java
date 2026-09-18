package dfgg.domain.match;

import java.util.List;
import java.util.Objects;

public record NormalizedMatch(
        String matchId,
        String patch,
        Integer queueId,
        List<NormalizedMatchParticipant> participants
) {

    public NormalizedMatch {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
        if (patch == null || patch.isBlank()) {
            throw new IllegalArgumentException("patch must not be blank");
        }
        Objects.requireNonNull(queueId, "queueId must not be null");
        participants = List.copyOf(Objects.requireNonNull(participants, "participants must not be null"));
        participants.forEach(participant -> participant.attachMatchContext(matchId, patch, queueId));
    }
}
