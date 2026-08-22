package dfgg.application.mining;

import dfgg.application.sequence.PrefixSpanMiner;
import dfgg.domain.match.MatchParticipantCohort;
import dfgg.domain.match.MatchParticipantCohortRepository;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.sequence.SequentialPattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SequentialPatternMiningBatchService {

    private final NormalizedMatchParticipantRepository participantRepository;
    private final MatchParticipantCohortRepository cohortRepository;
    private final PrefixSpanMiner prefixSpanMiner;

    public SequentialPatternMiningBatchService(
            NormalizedMatchParticipantRepository participantRepository,
            MatchParticipantCohortRepository cohortRepository,
            PrefixSpanMiner prefixSpanMiner
    ) {
        this.participantRepository = participantRepository;
        this.cohortRepository = cohortRepository;
        this.prefixSpanMiner = prefixSpanMiner;
    }

    @Transactional(readOnly = true)
    public Map<MiningScope, List<SequentialPattern>> mineFromMatchData(int minSupport) {
        List<NormalizedMatchParticipant> participants = participantRepository.findAll();
        Map<String, String> tierByMatchAndPuuid = tierByMatchAndPuuid(participants);

        Map<MiningScope, List<List<Long>>> sequencesByScope = new LinkedHashMap<>();
        for (NormalizedMatchParticipant participant : participants) {
            String tier = tierByMatchAndPuuid.get(cohortLookupKey(participant.getMatchId(), participant.getPuuid()));
            if (tier == null) {
                continue;
            }
            MiningScope scope = new MiningScope(
                    Long.valueOf(participant.getChampionId()),
                    participant.getPosition(),
                    tier,
                    participant.getPatch()
            );
            List<Long> sequence = participant.getCoreItemPurchaseOrder().stream()
                    .map(Long::valueOf)
                    .toList();
            sequencesByScope.computeIfAbsent(scope, ignored -> new ArrayList<>()).add(sequence);
        }

        Map<MiningScope, List<SequentialPattern>> patternsByScope = new LinkedHashMap<>();
        for (Map.Entry<MiningScope, List<List<Long>>> entry : sequencesByScope.entrySet()) {
            patternsByScope.put(entry.getKey(), prefixSpanMiner.mine(entry.getValue(), minSupport));
        }
        return patternsByScope;
    }

    private Map<String, String> tierByMatchAndPuuid(List<NormalizedMatchParticipant> participants) {
        Set<String> matchIds = participants.stream()
                .map(NormalizedMatchParticipant::getMatchId)
                .collect(Collectors.toSet());
        if (matchIds.isEmpty()) {
            return Map.of();
        }
        return cohortRepository.findByMatchIdIn(matchIds).stream()
                .collect(Collectors.toMap(
                        cohort -> cohortLookupKey(cohort.getMatchId(), cohort.getPuuid()),
                        MatchParticipantCohort::getTier,
                        (first, second) -> first
                ));
    }

    private String cohortLookupKey(String matchId, String puuid) {
        return matchId + "|" + puuid;
    }
}
