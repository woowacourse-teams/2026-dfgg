package dfgg.application.mining;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.sequence.PrefixSpanMiner;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.sequence.MinedSequentialPattern;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import dfgg.domain.sequence.SequentialPattern;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class SequentialPatternMiningBatchService {

    private final NormalizedMatchParticipantRepository participantRepository;
    private final MinedSequentialPatternRepository minedSequentialPatternRepository;
    private final PrefixSpanMiner prefixSpanMiner;
    private final ChampionPositionNormalizer positionNormalizer;
    private final EntityManager entityManager;
    private final int matchPageSize;

    public SequentialPatternMiningBatchService(
            NormalizedMatchParticipantRepository participantRepository,
            MinedSequentialPatternRepository minedSequentialPatternRepository,
            PrefixSpanMiner prefixSpanMiner,
            ChampionPositionNormalizer positionNormalizer,
            EntityManager entityManager,
            @Value("${mining.batch.match-page-size}") int matchPageSize
    ) {
        this.participantRepository = participantRepository;
        this.minedSequentialPatternRepository = minedSequentialPatternRepository;
        this.prefixSpanMiner = prefixSpanMiner;
        this.positionNormalizer = positionNormalizer;
        this.entityManager = entityManager;
        this.matchPageSize = matchPageSize;
    }

    @Transactional
    public Map<MiningScope, List<SequentialPattern>> mineFromMatchData(int minSupport, String algorithmVersion) {
        Assert.hasText(algorithmVersion, "algorithmVersion must not be blank");

        Map<MiningScope, List<ParticipantSequence>> sequencesByScope = new LinkedHashMap<>();

        int page = 0;
        Slice<String> matchIdPage;
        do {
            matchIdPage = participantRepository.findDistinctMatchIds(PageRequest.of(page, matchPageSize));
            List<String> matchIds = matchIdPage.getContent();
            if (!matchIds.isEmpty()) {
                List<NormalizedMatchParticipant> participants = participantRepository.findByMatchIdIn(matchIds);
                for (NormalizedMatchParticipant participant : participants) {
                    String tier = participant.getTier();
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
                entityManager.clear();
            }
            page++;
        } while (matchIdPage.hasNext());

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

    private record ParticipantSequence(List<Long> items, boolean win) {
    }
}
