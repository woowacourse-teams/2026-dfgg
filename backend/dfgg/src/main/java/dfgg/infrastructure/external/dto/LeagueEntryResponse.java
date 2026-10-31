package dfgg.infrastructure.external.dto;

public record LeagueEntryResponse(
        String puuid,
        String queueType,
        String tier,
        String rank,
        Integer leaguePoints,
        Integer wins,
        Integer losses
) {
}
