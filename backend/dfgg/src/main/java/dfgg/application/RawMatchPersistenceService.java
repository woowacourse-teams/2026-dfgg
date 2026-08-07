package dfgg.application;

import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RawMatchPersistenceService {

    private final RawMatchRepository rawMatchRepository;

    public RawMatchPersistenceService(RawMatchRepository rawMatchRepository) {
        this.rawMatchRepository = rawMatchRepository;
    }

    @Transactional
    public boolean persist(RawMatch rawMatch) {
        Objects.requireNonNull(rawMatch, "rawMatch must not be null");

        return rawMatchRepository.insertIfAbsent(
                rawMatch.getMatchId(),
                rawMatch.getRawData()
        ) == 1;
    }
}
