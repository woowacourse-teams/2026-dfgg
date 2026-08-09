package dfgg.application;

import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.MatchParticipantCohortRepository;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChampionBuildStatsRebuildService {

    private static final Logger log = LoggerFactory.getLogger(ChampionBuildStatsRebuildService.class);

    private final RawMatchRepository rawMatchRepository;
    private final RawMatchTimelineRepository rawMatchTimelineRepository;
    private final ItemRepository itemRepository;
    private final MatchNormalizer matchNormalizer;
    private final NormalizedMatchPersistenceService normalizedPersistenceService;
    private final ChampionBuildStatsAggregationService aggregationService;
    private final MatchParticipantCohortRepository cohortRepository;

    public ChampionBuildStatsRebuildService(
            RawMatchRepository rawMatchRepository,
            RawMatchTimelineRepository rawMatchTimelineRepository,
            ItemRepository itemRepository,
            MatchNormalizer matchNormalizer,
            NormalizedMatchPersistenceService normalizedPersistenceService,
            ChampionBuildStatsAggregationService aggregationService,
            MatchParticipantCohortRepository cohortRepository
    ) {
        this.rawMatchRepository = rawMatchRepository;
        this.rawMatchTimelineRepository = rawMatchTimelineRepository;
        this.itemRepository = itemRepository;
        this.matchNormalizer = matchNormalizer;
        this.normalizedPersistenceService = normalizedPersistenceService;
        this.aggregationService = aggregationService;
        this.cohortRepository = cohortRepository;
    }

    @Transactional
    public int rebuildAll(String tier) {
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        Set<Integer> coreItemIds = coreItemIds();
        int recordedSamples = 0;
        int skippedMatches = 0;
        for (RawMatch rawMatch : rawMatchRepository.findAll()) {
            RawMatchTimeline timeline = rawMatchTimelineRepository.findById(rawMatch.getMatchId()).orElse(null);
            if (timeline == null) {
                skippedMatches++;
                log.warn("Skipping match {} because its raw timeline is missing", rawMatch.getMatchId());
                continue;
            }
            recordedSamples += rebuildOne(
                    rawMatch,
                    timeline,
                    tier,
                    cohortRepository.findPuuidsByMatchIdAndQueueTypeAndTier(
                            rawMatch.getMatchId(),
                            "RANKED_SOLO_5x5",
                            tier
                    ),
                    coreItemIds
            );
        }
        logSkippedMatches(skippedMatches);
        return recordedSamples;
    }

    @Transactional
    public int rebuildAll(String tier, Collection<String> cohortPuuids) {
        Objects.requireNonNull(cohortPuuids, "cohortPuuids must not be null");
        Set<Integer> coreItemIds = coreItemIds();
        int recordedSamples = 0;
        int skippedMatches = 0;
        for (RawMatch rawMatch : rawMatchRepository.findAll()) {
            RawMatchTimeline timeline = rawMatchTimelineRepository.findById(rawMatch.getMatchId()).orElse(null);
            if (timeline == null) {
                skippedMatches++;
                log.warn("Skipping match {} because its raw timeline is missing", rawMatch.getMatchId());
                continue;
            }
            recordedSamples += rebuildOne(
                    rawMatch,
                    timeline,
                    tier,
                    cohortPuuids,
                    coreItemIds
            );
        }
        logSkippedMatches(skippedMatches);
        return recordedSamples;
    }

    @Transactional
    public int rebuildOne(String matchId, String tier, Collection<String> cohortPuuids) {
        Objects.requireNonNull(matchId, "matchId must not be null");
        Set<Integer> coreItemIds = coreItemIds();
        RawMatch rawMatch = rawMatchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("raw match not found: " + matchId));
        RawMatchTimeline timeline = rawMatchTimelineRepository.findById(matchId)
                .orElseThrow(() -> new IllegalStateException("raw match timeline not found: " + matchId));
        return rebuildOne(rawMatch, timeline, tier, cohortPuuids, coreItemIds);
    }

    private int rebuildOne(
            RawMatch rawMatch,
            RawMatchTimeline timeline,
            String tier,
            Collection<String> cohortPuuids,
            Set<Integer> coreItemIds
    ) {
        var normalized = matchNormalizer.normalize(
                rawMatch.getMatchId(),
                rawMatch.getRawData(),
                timeline.getRawData(),
                coreItemIds
        );
        normalizedPersistenceService.replace(normalized);
        return aggregationService.aggregate(normalized, tier, cohortPuuids);
    }

    private Set<Integer> coreItemIds() {
        return itemRepository.findAll().stream()
                .map(Item::getItemId)
                .map(Math::toIntExact)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void logSkippedMatches(int skippedMatches) {
        if (skippedMatches > 0) {
            log.warn("Skipped {} matches during ChampionBuildStats rebuild because timelines are missing", skippedMatches);
        }
    }
}
