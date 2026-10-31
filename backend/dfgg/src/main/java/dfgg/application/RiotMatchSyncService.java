package dfgg.application;

import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.player.PlayerRepository;
import dfgg.infrastructure.external.client.RiotClient;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class RiotMatchSyncService {

    private static final String PLATFORM = "KR";

    private final RiotClient riotClient;
    private final PlayerRepository playerRepository;
    private final RawMatchRepository rawMatchRepository;
    private final RawMatchPersistenceService persistenceService;

    public RiotMatchSyncService(
            RiotClient riotClient,
            PlayerRepository playerRepository,
            RawMatchRepository rawMatchRepository,
            RawMatchPersistenceService persistenceService
    ) {
        this.riotClient = riotClient;
        this.playerRepository = playerRepository;
        this.rawMatchRepository = rawMatchRepository;
        this.persistenceService = persistenceService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void syncMatches(
            int playerPage,
            int playerCount,
            int matchStart,
            int matchCount
    ) {
        Assert.isTrue(playerPage >= 0, "playerPage must not be negative");
        Assert.isTrue(
                playerCount > 0 && playerCount <= 100,
                "playerCount must be between 1 and 100"
        );
        Assert.isTrue(matchStart >= 0, "start must not be negative");
        Assert.isTrue(
                matchCount > 0 && matchCount <= 100,
                "count must be between 1 and 100"
        );

        LinkedHashSet<String> matchIds = collectMatchIds(
                playerPage,
                playerCount,
                matchStart,
                matchCount
        );
        if (matchIds.isEmpty()) {
            return;
        }

        Set<String> existingMatchIds = rawMatchRepository.findExistingMatchIds(matchIds);
        matchIds.removeAll(existingMatchIds);

        matchIds.forEach(this::fetchAndPersist);
    }

    private LinkedHashSet<String> collectMatchIds(
            int playerPage,
            int playerCount,
            int matchStart,
            int matchCount
    ) {
        List<String> puuids = playerRepository.findPuuidsByPlatform(
                PLATFORM,
                PageRequest.of(playerPage, playerCount)
        );
        LinkedHashSet<String> matchIds = new LinkedHashSet<>();

        puuids.forEach(puuid ->
                matchIds.addAll(riotClient.getMatchIds(puuid, matchStart, matchCount))
        );
        return matchIds;
    }

    private void fetchAndPersist(String matchId) {
        String rawData = riotClient.getRawMatch(matchId);
        persistenceService.persist(new RawMatch(matchId, rawData));
    }
}
