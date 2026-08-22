package dfgg.application.mining;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.sequence.PrefixSpanMiner;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.MatchParticipantCohort;
import dfgg.domain.match.MatchParticipantCohortRepository;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.sequence.MinedSequentialPattern;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import dfgg.domain.sequence.SequentialPattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class SequentialPatternMiningBatchService {

    private final NormalizedMatchParticipantRepository participantRepository;
    private final MatchParticipantCohortRepository cohortRepository;
    private final MinedSequentialPatternRepository minedSequentialPatternRepository;
    private final PrefixSpanMiner prefixSpanMiner;
    private final ChampionPositionNormalizer positionNormalizer;

    public SequentialPatternMiningBatchService(
            NormalizedMatchParticipantRepository participantRepository,
            MatchParticipantCohortRepository cohortRepository,
            MinedSequentialPatternRepository minedSequentialPatternRepository,
            PrefixSpanMiner prefixSpanMiner,
            ChampionPositionNormalizer positionNormalizer
    ) {
        this.participantRepository = participantRepository;
        this.cohortRepository = cohortRepository;
        this.minedSequentialPatternRepository = minedSequentialPatternRepository;
        this.prefixSpanMiner = prefixSpanMiner;
        this.positionNormalizer = positionNormalizer;
    }

    @Transactional
    public Map<MiningScope, List<SequentialPattern>> mineFromMatchData(
            String queueType, int minSupport, String algorithmVersion
    ) {
        Assert.hasText(algorithmVersion, "algorithmVersion must not be blank");

        List<NormalizedMatchParticipant> participants = participantRepository.findAll();
        Map<String, String> tierByMatchAndPuuid = tierByMatchAndPuuid(participants, queueType);

        Map<MiningScope, List<ParticipantSequence>> sequencesByScope = new LinkedHashMap<>();
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
            sequencesByScope.computeIfAbsent(scope, ignored -> new ArrayList<>())
                    .add(new ParticipantSequence(sequence, participant.getWin()));
        }

        Map<MiningScope, List<SequentialPattern>> patternsByScope = new LinkedHashMap<>();
        List<MinedSequentialPattern> toSave = new ArrayList<>();
        for (Map.Entry<MiningScope, List<ParticipantSequence>> entry : sequencesByScope.entrySet()) {
            MiningScope scope = entry.getKey();
            List<ParticipantSequence> scopedSequences = entry.getValue();
            List<SequentialPattern> patterns = prefixSpanMiner.mine(
                    scopedSequences.stream().map(ParticipantSequence::items).toList(), minSupport
            );
            patternsByScope.put(scope, patterns);
            toSave.addAll(toMinedSequentialPatterns(scope, scopedSequences, patterns, algorithmVersion));
        }

        minedSequentialPatternRepository.deleteByAlgorithmVersion(algorithmVersion);
        minedSequentialPatternRepository.flush();
        minedSequentialPatternRepository.saveAll(toSave);

        return patternsByScope;
    }

    private List<MinedSequentialPattern> toMinedSequentialPatterns(
            MiningScope scope,
            List<ParticipantSequence> scopedSequences,
            List<SequentialPattern> patterns,
            String algorithmVersion
    ) {
        Optional<ChampionPosition> position = positionNormalizer.normalize(scope.position());
        if (position.isEmpty()) {
            return List.of();
        }

        int scopeTotalCount = scopedSequences.size();
        List<MinedSequentialPattern> minedPatterns = new ArrayList<>();
        for (SequentialPattern pattern : patterns) {
            int winCount = (int) scopedSequences.stream()
                    .filter(ParticipantSequence::win)
                    .filter(sequence -> prefixSpanMiner.matches(sequence.items(), pattern.items()))
                    .count();
            minedPatterns.add(new MinedSequentialPattern(
                    scope.championId(),
                    position.get(),
                    scope.tier(),
                    scope.patch(),
                    pattern.items(),
                    pattern.support(),
                    scopeTotalCount,
                    winCount,
                    algorithmVersion
            ));
        }
        return minedPatterns;
    }

    private Map<String, String> tierByMatchAndPuuid(List<NormalizedMatchParticipant> participants, String queueType) {
        Set<String> matchIds = participants.stream()
                .map(NormalizedMatchParticipant::getMatchId)
                .collect(Collectors.toSet());
        if (matchIds.isEmpty()) {
            return Map.of();
        }
        return cohortRepository.findByMatchIdInAndQueueType(matchIds, queueType).stream()
                .collect(Collectors.toMap(
                        cohort -> cohortLookupKey(cohort.getMatchId(), cohort.getPuuid()),
                        MatchParticipantCohort::getTier
                ));
    }

    private String cohortLookupKey(String matchId, String puuid) {
        return matchId + "|" + puuid;
    }

    private record ParticipantSequence(List<Long> items, boolean win) {
    }
}
