package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dfgg.domain.player.PlayerCohort;
import dfgg.domain.player.PlayerCohortRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlayerCohortRepositoryTest {

    @Autowired
    private PlayerCohortRepository playerCohortRepository;

    @Test
    void 매치_수집에_사용할_PUUID별_코호트를_조회한다() {
        playerCohortRepository.saveAll(List.of(
                cohort("p-1", "PLATINUM"),
                cohort("p-2", "GOLD")
        ));

        assertThat(playerCohortRepository.findTargetsByPuuidsAndQueueType(
                List.of("p-1", "p-2"),
                "RANKED_SOLO_5x5"
        ))
                .extracting(
                        PlayerCohortRepository.Target::getPuuid,
                        PlayerCohortRepository.Target::getTier
                )
                .containsExactly(
                        tuple("p-1", "PLATINUM"),
                        tuple("p-2", "GOLD")
                );
    }

    private PlayerCohort cohort(String puuid, String tier) {
        return new PlayerCohort(
                puuid,
                "RANKED_SOLO_5x5",
                tier,
                "I",
                Instant.parse("2026-08-06T08:00:00Z")
        );
    }
}
