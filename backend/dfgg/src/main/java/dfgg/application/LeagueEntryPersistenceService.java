package dfgg.application;

import dfgg.domain.player.PlayerRepository;
import dfgg.domain.player.PlayerCohort;
import dfgg.infrastructure.external.dto.LeagueEntryResponse;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class LeagueEntryPersistenceService {

    private final PlayerRepository playerRepository;
    private final PlayerCohortPersistenceService cohortPersistenceService;

    public LeagueEntryPersistenceService(
            PlayerRepository playerRepository,
            PlayerCohortPersistenceService cohortPersistenceService
    ) {
        this.playerRepository = playerRepository;
        this.cohortPersistenceService = cohortPersistenceService;
    }

    @Transactional
    public void persist(
            String platform,
            List<LeagueEntryResponse> entries,
            Instant collectedAt
    ) {
        // 이 부분 나중에 검토 예정
        Assert.hasText(platform, "platform must not be blank");
        Objects.requireNonNull(entries, "entries must not be null");
        Objects.requireNonNull(collectedAt, "collectedAt must not be null");

        entries.forEach(entry -> persistEntry(platform, entry, collectedAt));
    }

    private void persistEntry(
            String platform,
            LeagueEntryResponse entry,
            Instant collectedAt
    ) {
        Objects.requireNonNull(entry, "entry must not be null");
        Assert.hasText(entry.puuid(), "puuid must not be blank");

        playerRepository.upsert(entry.puuid(), platform, collectedAt);
        cohortPersistenceService.persist(new PlayerCohort(
                entry.puuid(),
                entry.queueType(),
                entry.tier(),
                entry.rank(),
                collectedAt
        ));
    }
}
