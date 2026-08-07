package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.player.PlayerRepository;
import dfgg.domain.player.PlayerCohortRepository;
import dfgg.infrastructure.external.dto.LeagueEntryResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({LeagueEntryPersistenceService.class, PlayerCohortPersistenceService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LeagueEntryPersistenceConcurrencyTest {

    @Autowired
    private LeagueEntryPersistenceService persistenceService;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerCohortRepository playerCohortRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        playerCohortRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void 같은_플레이어를_동시에_저장해도_한_건만_남는다() throws Exception {
        Instant collectedAt = Instant.parse("2026-08-06T08:00:00Z");
        List<LeagueEntryResponse> entries = List.of(new LeagueEntryResponse(
                "puuid-1",
                "RANKED_SOLO_5x5",
                "PLATINUM",
                "I",
                50,
                20,
                10
        ));
        CountDownLatch startSignal = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> persistAfterSignal(entries, collectedAt, startSignal));
            Future<?> second = executor.submit(() -> persistAfterSignal(entries, collectedAt, startSignal));

            startSignal.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertThat(playerRepository.count()).isEqualTo(1);
        assertThat(playerCohortRepository.count()).isEqualTo(1);
    }

    private void persistAfterSignal(
            List<LeagueEntryResponse> entries,
            Instant collectedAt,
            CountDownLatch startSignal
    ) {
        try {
            startSignal.await();
            persistenceService.persist("KR", entries, collectedAt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent persistence test was interrupted", exception);
        }
    }
}
