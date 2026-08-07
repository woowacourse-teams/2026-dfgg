package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.NormalizedParticipant;
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
@Import(NormalizedMatchPersistenceService.class)
class NormalizedMatchPersistenceServiceTest {

    @Autowired
    private NormalizedMatchPersistenceService persistenceService;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;

    @Test
    void 같은_매치를_재정규화하면_참가자_데이터를_교체한다() {
        NormalizedMatch first = match(List.of(participant("p-1", List.of(3071))));
        NormalizedMatch second = match(List.of(
                participant("p-1", List.of(3071, 6610)),
                participant("p-2", List.of(3071))
        ));

        persistenceService.replace(first);
        persistenceService.replace(second);

        assertThat(participantRepository.findByMatchId("KR_1"))
                .extracting(NormalizedMatchParticipant::getPuuid)
                .containsExactlyInAnyOrder("p-1", "p-2");
        assertThat(participantRepository.findByMatchId("KR_1").stream()
                .filter(participant -> participant.getPuuid().equals("p-1"))
                .findFirst()
                .orElseThrow()
                .getFinalCoreItemIds())
                .containsExactly(3071, 6610);
    }

    private NormalizedMatch match(List<NormalizedParticipant> participants) {
        return new NormalizedMatch("KR_1", "16.15", 420, participants);
    }

    private NormalizedParticipant participant(String puuid, List<Integer> itemIds) {
        return new NormalizedParticipant(
                puuid,
                1,
                1,
                100,
                "TOP",
                true,
                itemIds,
                itemIds,
                true
        );
    }
}
