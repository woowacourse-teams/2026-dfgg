package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.ScoredItem;
import dfgg.application.recommend.v3.generator.ChampionBaselineReader;
import dfgg.application.recommend.v3.generator.AllySynergyCandidateGenerator;
import dfgg.application.recommend.v3.generator.PairLiftCalculator;
import dfgg.application.recommend.v3.generator.PairSynergyRetriever;
import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.ChampionItemStats;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.TierScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ally를 lift로 바꾼 뒤 분모가 바닥이면 값이 폭발해 "한 판짜리 우연"이 상위를 점령하는 문제가 생겼는지 본다.
 */
@SpringBootTest
@ActiveProfiles("evaluation")
@Tag("evaluation")
class AllyLiftQualityProbeTest {

    private static final int SAMPLE_MATCHES = Integer.getInteger("evaluation.matches", 120);
    private static final int SERVING_TOP_K = 5;

    @Autowired private List<CandidateGenerator> generators;
    @Autowired private ChampionPairItemStatsRepository pairRepository;
    @Autowired private ChampionItemRollupRepository championItemRollupRepository;
    @Autowired private PairLiftCalculator pairLiftCalculator;
    @Autowired private NormalizedMatchParticipantRepository participantRepository;
    @Autowired private ChampionItemStatsRepository championItemStatsRepository;
    @Autowired private ItemStatsAggregationService aggregationService;
    @Autowired private TierScope tierScope;

    private final SnapshotQueryBuilder snapshotQueryBuilder = new SnapshotQueryBuilder();
    private final GameSplit gameSplit = new GameSplit(0.8);
    private ParticipantSampler participantSampler;

    @BeforeEach
    void prepareSampler() {
        participantSampler = new ParticipantSampler(tierScope);
    }

    private static final List<Double> FLOORS =
            List.of(0.0, 0.005, 0.010, 0.020, 0.030, 0.050, 0.080);

    @Test
    @DisplayName("base rate 하한별로 ally 상위 5 후보의 품질과 정답 포함률을 본다")
    void probeAllyCandidateQuality() {
        aggregationService.aggregate(3);
        List<SnapshotQuery> snapshots = collectTestSnapshots();

        System.out.printf("%n%-8s%10s%12s%12s%13s%12s%14s%n",
                "하한", "칸", "lift중앙", "구매율중앙", "Recall@5", "BUILD겹침", "고유기여");
        System.out.println("-".repeat(84));
        for (double floor : FLOORS) {
            AllySynergyCandidateGenerator ally = new AllySynergyCandidateGenerator(
                    new PairSynergyRetriever(pairRepository, pairLiftCalculator, 5, floor),
                    championItemStatsRepository, championItemRollupRepository,
                    new WilsonScoreCalculator(), new ChampionBaselineReader(championItemStatsRepository, championItemRollupRepository));
            measure(ally, snapshots, floor);
        }
        assertThat(snapshots).isNotEmpty();
    }

    private void measure(AllySynergyCandidateGenerator ally,
                         List<SnapshotQuery> snapshots, double floor) {
        CandidateGenerator build = generators.stream()
                .filter(generator -> generator.source() == CandidateSource.BUILD)
                .findFirst().orElseThrow();
        List<Double> lifts = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        int slots = 0;
        int queries = 0;
        int hits = 0;
        int uniqueHits = 0;
        int overlapping = 0;

        for (SnapshotQuery snapshot : snapshots) {
            Map<Long, ChampionItemStats> statsByItem = championItemStatsRepository
                    .findByChampionIdAndPosition(
                            Math.toIntExact(snapshot.query().myChampionId()), snapshot.query().position())
                    .stream()
                    .collect(Collectors.toMap(ChampionItemStats::getItemId, stats -> stats, (a, b) -> a));
            int games = statsByItem.values().stream()
                    .mapToInt(ChampionItemStats::getChampionGameCountAll).max().orElse(0);
            if (games == 0) {
                continue;
            }
            queries++;
            java.util.Set<Long> buildItems = build.generate(snapshot.query(), SERVING_TOP_K)
                    .rankedItems().stream().map(ScoredItem::itemId)
                    .collect(java.util.stream.Collectors.toSet());
            boolean hit = false;
            boolean uniqueHit = false;
            for (ScoredItem item : ally.generate(snapshot.query(), SERVING_TOP_K).rankedItems()) {
                slots++;
                lifts.add(item.score());
                ChampionItemStats stats = statsByItem.get(item.itemId());
                rates.add(stats == null ? 0.0 : (double) stats.getPurchaseCountAll() / games);
                if (buildItems.contains(item.itemId())) {
                    overlapping++;
                }
                if (item.itemId().equals(snapshot.groundTruthItemId())) {
                    hit = true;
                    uniqueHit |= !buildItems.contains(item.itemId());
                }
            }
            if (hit) {
                hits++;
            }
            if (uniqueHit) {
                uniqueHits++;
            }
        }

        System.out.printf("%-8.3f%10d%12.2f%12.4f%12.2f%%%11.1f%%%13.2f%%%n",
                floor, slots, percentile(lifts, 0.50), percentile(rates, 0.50),
                100.0 * hits / Math.max(queries, 1),
                100.0 * overlapping / Math.max(slots, 1),
                100.0 * uniqueHits / Math.max(queries, 1));
    }

    private double percentile(List<Double> values, double q) {
        List<Double> sorted = values.stream().sorted().toList();
        if (sorted.isEmpty()) {
            return 0.0;
        }
        return sorted.get(Math.min(sorted.size() - 1, (int) (q * (sorted.size() - 1))));
    }

    private List<SnapshotQuery> collectTestSnapshots() {
        List<SnapshotQuery> snapshots = new ArrayList<>();
        int page = 0;
        int sampled = 0;
        while (sampled < SAMPLE_MATCHES) {
            List<String> matchIds = participantRepository
                    .findSampledMatchIds(tierScope.values(), PageRequest.of(page++, 500));
            if (matchIds.isEmpty()) {
                break;
            }
            for (String matchId : matchIds) {
                if (sampled >= SAMPLE_MATCHES) {
                    break;
                }
                if (gameSplit.isTrain(matchId)) {
                    continue;
                }
                List<NormalizedMatchParticipant> participants = participantRepository.findByMatchId(matchId);
                int used = 0;
                for (NormalizedMatchParticipant participant
                        : participantSampler.sample(participants, matchId, participants.size())) {
                    if (used >= 2) {
                        break;
                    }
                    List<SnapshotQuery> built = snapshotQueryBuilder.build(
                            participants, participant.getPuuid(), participant.getPatch());
                    if (!built.isEmpty()) {
                        used++;
                        snapshots.addAll(built);
                    }
                }
                if (used > 0) {
                    sampled++;
                }
            }
        }
        return snapshots;
    }
}
