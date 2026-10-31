package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.player.Player;
import dfgg.domain.player.PlayerRepository;
import dfgg.infrastructure.external.dto.LeagueEntryResponse;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(LeagueEntryPersistenceService.class)
class LeagueEntryPersistenceServiceTest {

    @Autowired
    private LeagueEntryPersistenceService persistenceService;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 리그_엔트리에서_플레이어를_저장한다() {
        Instant collectedAt = Instant.parse("2026-08-06T08:00:00Z");

        persistenceService.persist(
                "KR",
                List.of(leagueEntry("puuid-1", "PLATINUM", "I", 73, 120, 100)),
                collectedAt
        );
        entityManager.flush();
        entityManager.clear();

        Player player = playerRepository.findById("puuid-1").orElseThrow();
        assertThat(player.getPlatform()).isEqualTo("KR");
        assertThat(player.getFirstSeenAt()).isEqualTo(collectedAt);
        assertThat(player.getLastSeenAt()).isEqualTo(collectedAt);
    }

    @Test
    void 같은_플레이어를_다시_수집해도_중복되지_않는다() {
        Instant collectedAt = Instant.parse("2026-08-06T08:00:00Z");
        LeagueEntryResponse entry = leagueEntry("puuid-1", "PLATINUM", "II", 42, 80, 70);

        persistenceService.persist("KR", List.of(entry), collectedAt);
        persistenceService.persist("KR", List.of(entry), collectedAt);
        entityManager.flush();
        entityManager.clear();

        assertThat(playerRepository.count()).isEqualTo(1);
    }

    @Test
    void 다시_수집하면_플레이어의_마지막_관측_시각을_갱신한다() {
        Instant firstCollectedAt = Instant.parse("2026-08-06T08:00:00Z");
        Instant lastCollectedAt = Instant.parse("2026-08-06T09:00:00Z");

        persistenceService.persist(
                "KR",
                List.of(leagueEntry("puuid-1", "PLATINUM", "III", 10, 50, 45)),
                firstCollectedAt
        );
        persistenceService.persist(
                "KR",
                List.of(leagueEntry("puuid-1", "PLATINUM", "II", 20, 55, 47)),
                lastCollectedAt
        );
        entityManager.flush();
        entityManager.clear();

        Player player = playerRepository.findById("puuid-1").orElseThrow();
        assertThat(player.getFirstSeenAt()).isEqualTo(firstCollectedAt);
        assertThat(player.getLastSeenAt()).isEqualTo(lastCollectedAt);
        assertThat(playerRepository.count()).isEqualTo(1);
    }

    @Test
    void 수집_실행이_시간순으로_처리되지_않아도_최초와_마지막_관측_시각을_보존한다() {
        Instant earlierCollectedAt = Instant.parse("2026-08-06T08:00:00Z");
        Instant laterCollectedAt = Instant.parse("2026-08-06T10:00:00Z");

        persistenceService.persist(
                "KR",
                List.of(leagueEntry("puuid-1", "PLATINUM", "I", 50, 20, 10)),
                laterCollectedAt
        );
        persistenceService.persist(
                "KR",
                List.of(leagueEntry("puuid-1", "PLATINUM", "II", 30, 18, 9)),
                earlierCollectedAt
        );
        entityManager.flush();
        entityManager.clear();

        Player player = playerRepository.findById("puuid-1").orElseThrow();
        assertThat(player.getFirstSeenAt()).isEqualTo(earlierCollectedAt);
        assertThat(player.getLastSeenAt()).isEqualTo(laterCollectedAt);
    }

    private LeagueEntryResponse leagueEntry(
            String puuid,
            String tier,
            String division,
            int leaguePoints,
            int wins,
            int losses
    ) {
        return new LeagueEntryResponse(
                puuid,
                "RANKED_SOLO_5x5",
                tier,
                division,
                leaguePoints,
                wins,
                losses
        );
    }
}
