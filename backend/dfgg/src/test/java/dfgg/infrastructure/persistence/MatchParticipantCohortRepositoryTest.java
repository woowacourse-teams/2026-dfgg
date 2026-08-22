package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dfgg.domain.match.MatchParticipantCohort;
import dfgg.domain.match.MatchParticipantCohortRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("같은 매치와 puuid라도 요청한 큐 타입의 코호트만 매칭한다")
    void findPuuidsByMatchIdAndQueueTypeAndTier_WhenSameMatchAndPuuidHasMultipleQueueTypes_OnlyMatchesRequestedQueueType() {
        // given & when
        List<String> soloGoldPuuids = cohortRepository.findPuuidsByMatchIdAndQueueTypeAndTier(
                "KR_M1", "RANKED_SOLO_5x5", "GOLD"
        );

        // then
        assertThat(soloGoldPuuids).containsExactly("puuid-1");
    }

    @Test
    @DisplayName("요청한 티어와 일치하는 코호트가 없으면 빈 목록을 반환한다")
    void findPuuidsByMatchIdAndQueueTypeAndTier_WhenTierDoesNotMatch_ReturnsEmpty() {
        // given & when
        List<String> soloPlatinumPuuids = cohortRepository.findPuuidsByMatchIdAndQueueTypeAndTier(
                "KR_M1", "RANKED_SOLO_5x5", "PLATINUM"
        );

        // then
        assertThat(soloPlatinumPuuids).isEmpty();
    }

    @Test
    @DisplayName("매치에 여러 큐 타입의 코호트가 있으면 큐 타입 구분 없이 전부 조회한다")
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
    @DisplayName("요청하지 않은 matchId의 코호트는 결과에서 제외한다")
    void findByMatchIdIn_WhenMatchIdNotRequested_ExcludesItsCohorts() {
        // given & when
        List<MatchParticipantCohort> cohorts = cohortRepository.findByMatchIdIn(List.of("KR_M1"));

        // then
        assertThat(cohorts).extracting(MatchParticipantCohort::getMatchId).doesNotContain("KR_M2");
    }
}
