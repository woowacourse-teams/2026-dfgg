package dfgg.application;

import dfgg.infrastructure.external.client.RiotClient;
import dfgg.infrastructure.external.dto.LeagueEntryResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RiotPlayerSyncService {

    private static final Logger log = LoggerFactory.getLogger(RiotPlayerSyncService.class);
    private static final String PLATFORM = "KR";

    private final RiotClient riotClient;
    private final LeagueEntryPersistenceService persistenceService;

    public RiotPlayerSyncService(
            RiotClient riotClient,
            LeagueEntryPersistenceService persistenceService
    ) {
        this.riotClient = riotClient;
        this.persistenceService = persistenceService;
    }

    public int syncLeagueEntries(
            String queue,
            String tier,
            String division,
            int page
    ) {
        long startedAtNanos = System.nanoTime();
        log.info(
                "Riot player sync started: queue={}, tier={}, division={}, page={}",
                queue,
                tier,
                division,
                page
        );
        List<LeagueEntryResponse> entries = riotClient.getLeagueEntries(
                queue,
                tier,
                division,
                page
        );

        int newPlayers = persistenceService.persist(
                PLATFORM,
                entries,
                Instant.now()
        );
        log.info(
                "Riot player sync completed: queue={}, tier={}, division={}, page={}, "
                        + "fetchedEntries={}, newPlayers={}, durationMs={}",
                queue,
                tier,
                division,
                page,
                entries.size(),
                newPlayers,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
        );
        return newPlayers;
    }
}
