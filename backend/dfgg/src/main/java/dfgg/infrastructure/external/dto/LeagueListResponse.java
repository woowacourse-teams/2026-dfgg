package dfgg.infrastructure.external.dto;

import java.util.List;
import java.util.Objects;

public record LeagueListResponse(
        String tier,
        String queue,
        List<LeagueEntryResponse> entries
) {

    public LeagueListResponse {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
    }
}
