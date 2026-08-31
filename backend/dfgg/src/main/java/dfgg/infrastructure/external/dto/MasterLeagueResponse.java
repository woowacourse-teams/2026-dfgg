package dfgg.infrastructure.external.dto;

import java.util.List;
import java.util.Objects;

public record MasterLeagueResponse(
        String tier,
        String queue,
        List<LeagueEntryResponse> entries
) {

    public MasterLeagueResponse {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
    }
}
