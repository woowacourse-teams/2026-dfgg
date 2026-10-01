package dfgg.application;

import dfgg.infrastructure.external.client.RiotClient;
import dfgg.infrastructure.external.dto.LeagueEntryResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RiotPlayerSyncService {

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

    public void syncLeagueEntries(
            String queue,
            String tier,
            String division,
            int page
    ) {
        List<LeagueEntryResponse> entries = riotClient.getLeagueEntries(
                queue,
                tier,
                division,
                page
        );

        persistenceService.persist(
                PLATFORM,
                entries,
                Instant.now()
        );
    }
}
