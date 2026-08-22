package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dfgg.domain.match.MatchParticipantCohort;
import dfgg.domain.match.MatchParticipantCohortRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/match-participant-cohort-repository-test-data.sql")
class MatchParticipantCohortRepositoryTest {

    @Autowired
    private MatchParticipantCohortRepository cohortRepository;

    @Test
    void findPuuidsByMatchIdAndQueueTypeAndTier_WhenSameMatchAndPuuidHasMultipleQueueTypes_OnlyMatchesRequestedQueueType() {
        // given & when
        List<String> soloGoldPuuids = cohortRepository.findPuuidsByMatchIdAndQueueTypeAndTier(
                "KR_M1", "RANKED_SOLO_5x5", "GOLD"
        );

        // then
        assertThat(soloGoldPuuids).containsExactly("puuid-1");
    }

    @Test
    void findPuuidsByMatchIdAndQueueTypeAndTier_WhenTierDoesNotMatch_ReturnsEmpty() {
        // given & when
        List<String> soloPlatinumPuuids = cohortRepository.findPuuidsByMatchIdAndQueueTypeAndTier(
                "KR_M1", "RANKED_SOLO_5x5", "PLATINUM"
        );

        // then
        assertThat(soloPlatinumPuuids).isEmpty();
    }

    @Test
    void findByMatchIdIn_WhenMatchHasCohortsAcrossQueueTypes_ReturnsEveryQueueTypeForThatMatchAndPuuid() {
        // given & when
        List<MatchParticipantCohort> cohorts = cohortRepository.findByMatchIdIn(List.of("KR_M1"));

        // then
        assertThat(cohorts)
                .extracting(MatchParticipantCohort::getPuuid, MatchParticipantCohort::getQueueType)
                .containsExactlyInAnyOrder(
                        tuple("puuid-1", "RANKED_SOLO_5x5"),
                        tuple("puuid-2", "RANKED_SOLO_5x5"),
                        tuple("puuid-1", "RANKED_FLEX_SR")
                );
    }

    @Test
    void findByMatchIdIn_WhenMatchIdNotRequested_ExcludesItsCohorts() {
        // given & when
        List<MatchParticipantCohort> cohorts = cohortRepository.findByMatchIdIn(List.of("KR_M1"));

        // then
        assertThat(cohorts).extracting(MatchParticipantCohort::getMatchId).doesNotContain("KR_M2");
    }
}
