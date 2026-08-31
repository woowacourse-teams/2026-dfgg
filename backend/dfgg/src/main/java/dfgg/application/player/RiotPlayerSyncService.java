package dfgg.application.player;

import dfgg.domain.player.Player;
import dfgg.domain.player.PlayerRepository;
import dfgg.infrastructure.external.client.RiotClient;
import dfgg.infrastructure.external.dto.LeagueEntryResponse;
import dfgg.infrastructure.external.dto.MasterLeagueResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class RiotPlayerSyncService {

    private static final String PLATFORM = "KR";
    private static final String SOLO_QUEUE_TYPE = "RANKED_SOLO_5x5";
    private static final String MASTER_TIER = "MASTER";
    private static final String UNRANKED_TIER = "UNRANKED";
    private static final String UNRANKED_DIVISION = "NONE";

    private final RiotClient riotClient;
    private final PlayerRepository playerRepository;

    public RiotPlayerSyncService(
            RiotClient riotClient,
            PlayerRepository playerRepository
    ) {
        this.riotClient = riotClient;
        this.playerRepository = playerRepository;
    }

    public SyncResult syncLeagueEntries(
            String queue,
            String tier,
            String division,
            int page
    ) {
        List<LeagueEntryResponse> entries = MASTER_TIER.equals(tier)
                ? getMasterLeagueEntries(queue)
                : riotClient.getLeagueEntries(queue, tier, division, page);

        Instant collectedAt = Instant.now();
        int newPlayers = entries.stream()
                .mapToInt(entry -> savePlayer(
                        entry.puuid(),
                        entry.tier(),
                        entry.rank(),
                        collectedAt
                ))
                .sum();
        return new SyncResult(
                newPlayers,
                entries.stream().map(LeagueEntryResponse::puuid).distinct().toList()
        );
    }

    private List<LeagueEntryResponse> getMasterLeagueEntries(String queue) {
        MasterLeagueResponse league = riotClient.getMasterLeague(queue);
        return league.entries().stream()
                .map(entry -> new LeagueEntryResponse(
                        entry.puuid(),
                        league.queue(),
                        league.tier(),
                        entry.rank(),
                        entry.leaguePoints(),
                        entry.wins(),
                        entry.losses()
                ))
                .toList();
    }

    public Map<String, String> syncPlayerTiers(Collection<String> puuids) {
        Instant collectedAt = Instant.now();
        Map<String, String> tiersByPuuid = new LinkedHashMap<>();

        for (String puuid : new LinkedHashSet<>(puuids)) {
            LeagueEntryResponse soloRank = findSoloRank(puuid);
            String tier = soloRank == null ? UNRANKED_TIER : soloRank.tier();
            String division = soloRank == null ? UNRANKED_DIVISION : soloRank.rank();
            savePlayer(puuid, tier, division, collectedAt);
            tiersByPuuid.put(puuid, tier);
        }
        return Map.copyOf(tiersByPuuid);
    }

    private LeagueEntryResponse findSoloRank(String puuid) {
        return riotClient.getLeagueEntriesByPuuid(puuid).stream()
                .filter(entry -> SOLO_QUEUE_TYPE.equals(entry.queueType()))
                .findFirst()
                .orElse(null);
    }

    private int savePlayer(
            String puuid,
            String tier,
            String division,
            Instant observedAt
    ) {
        Player player = playerRepository.findById(puuid).orElse(null);
        if (player == null) {
            playerRepository.save(new Player(
                    puuid,
                    PLATFORM,
                    tier,
                    division,
                    observedAt
            ));
            return 1;
        }

        player.updateRank(tier, division, observedAt);
        playerRepository.save(player);
        return 0;
    }

    public record SyncResult(int newPlayers, List<String> puuids) {

        public SyncResult {
            puuids = List.copyOf(Objects.requireNonNull(puuids, "puuids must not be null"));
        }
    }
}
