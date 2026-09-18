package dfgg.application.match;

import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.infrastructure.external.client.RiotClient;
import java.util.Collection;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RawMatchService {

    private final RiotClient riotClient;
    private final RawMatchRepository rawMatchRepository;

    public RawMatchService(
            RiotClient riotClient,
            RawMatchRepository rawMatchRepository
    ) {
        this.riotClient = riotClient;
        this.rawMatchRepository = rawMatchRepository;
    }

    public Set<String> findExistingMatchIds(Collection<String> matchIds) {
        return rawMatchRepository.findExistingMatchIds(matchIds);
    }

    public boolean collectRawMatch(String matchId) {
        String rawData = riotClient.getRawMatch(matchId);
        RawMatch rawMatch = new RawMatch(matchId, rawData);

        return rawMatchRepository.insertIfAbsent(
                rawMatch.getMatchId(),
                rawMatch.getRawData()
        ) == 1;
    }
}
