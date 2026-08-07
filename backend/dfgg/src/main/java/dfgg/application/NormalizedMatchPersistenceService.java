package dfgg.application;

import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NormalizedMatchPersistenceService {

    private final NormalizedMatchParticipantRepository participantRepository;

    public NormalizedMatchPersistenceService(NormalizedMatchParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Transactional
    public void replace(NormalizedMatch match) {
        Objects.requireNonNull(match, "match must not be null");

        participantRepository.deleteByMatchId(match.matchId());
        participantRepository.flush();
        participantRepository.saveAll(match.participants().stream()
                .map(participant -> new NormalizedMatchParticipant(match, participant))
                .toList());
    }
}
