package dfgg.domain.embedding;

import java.util.List;

public record ParticipantBuild(
        String championToken,
        List<String> itemTokens
) {

}