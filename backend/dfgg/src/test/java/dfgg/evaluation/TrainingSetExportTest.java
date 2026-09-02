package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.item.ItemService;
import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateTopK;
import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.GeneratorResult;
import dfgg.application.recommend.v3.HardValidityFilter;
import dfgg.application.recommend.v3.feature.CandidateFeatures;
import dfgg.application.recommend.v3.feature.FeatureExtractionPipeline;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.item.Item;
import dfgg.domain.itemstats.ChampionItemStats;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.PatchVersion;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * LTR 학습 데이터를 JSONL로 내보낸다.
 * <p>
 * feature 계산은 서빙과 같은 {@link FeatureExtractionPipeline}을 통과한다 —
 * Python은 여기서 나온 벡터를 그대로 학습에 쓰고 자체 계산을 하지 않는다.
 * 두 벌의 구현이 생기면 학습과 서빙이 서서히 어긋나고, 그 어긋남은 오프라인 지표로 드러나지 않는다.
 * <p>
 * 실행:
 * {@code ./gradlew evaluationTest --tests '*TrainingSetExportTest' -Devaluation.queries=300000}
 */
@SpringBootTest
@ActiveProfiles("evaluation")
@Tag("evaluation")
class TrainingSetExportTest {

    /** 목표 query 수. 실제 학습은 {@code -Devaluation.queries=300000} 권장. */
    private static final int TARGET_QUERIES = Integer.getInteger("evaluation.queries", 30_000);
    private static final int PARTICIPANTS_PER_MATCH = 3;
    private static final int RECENT_PATCH_WINDOW = 3;
    private static final double PLAUSIBLE_ALTERNATIVE_THRESHOLD = 0.05;
    private static final long MINIMUM_EXPECTED_PARTICIPANTS = 100_000L;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;
    @Autowired
    private ChampionItemStatsRepository championItemStatsRepository;
    @Autowired
    private ItemStatsAggregationService aggregationService;
    @Autowired
    private ItemService itemService;
    @Autowired
    private List<CandidateGenerator> generators;
    @Autowired
    private CandidateTopK candidateTopK;
    @Autowired
    private HardValidityFilter hardValidityFilter;
    @Autowired
    private FeatureExtractionPipeline featurePipeline;

    private final SnapshotQueryBuilder snapshotQueryBuilder = new SnapshotQueryBuilder();
    private final ParticipantSampler participantSampler = new ParticipantSampler();
    private final GameSplit gameSplit = new GameSplit(0.8);
    private final RelevanceLabeler labeler = new RelevanceLabeler(PLAUSIBLE_ALTERNATIVE_THRESHOLD);

    @Test
    @DisplayName("학습 데이터를 JSONL로 내보내고 라벨 분포·제외율·표본 대표성을 보고한다")
    void exportTrainingSet() throws IOException {
        assertThat(participantRepository.count())
                .as("실 매치 데이터가 있는 DB를 가리켜야 한다. 참가자 수")
                .isGreaterThan(MINIMUM_EXPECTED_PARTICIPANTS);

        aggregationService.aggregate(RECENT_PATCH_WINDOW);
        String latestPatch = latestPatch();
        Map<Long, Item> itemById = itemService.findItemsByIds(
                        itemService.findCoreItemIds().stream().map(Long::valueOf).toList()).stream()
                .collect(Collectors.toMap(Item::getItemId, Function.identity(), (first, second) -> first));

        Path outputPath = Path.of(System.getProperty("ltr.export.path", "../ml/data/train.jsonl"));
        Files.createDirectories(outputPath.toAbsolutePath().getParent());

        ExportStats stats = new ExportStats();
        long startedAt = System.currentTimeMillis();
        try (Writer out = Files.newBufferedWriter(outputPath);
             TrainingRowWriter writer = new TrainingRowWriter(out)) {
            exportQueries(writer, stats, itemById, latestPatch);
        }
        long durationMillis = System.currentTimeMillis() - startedAt;

        String report = stats.render(outputPath, durationMillis, latestPatch);
        System.out.println(report);
        Files.writeString(Path.of("../tasks/eval-training-set.md"), report);

        assertThat(stats.exportedQueries).as("내보낸 query 수").isPositive();
        assertThat(stats.rowsWithGroundTruth)
                .as("query마다 정답(등급 3)이 정확히 하나여야 한다")
                .isEqualTo(stats.exportedQueries);
    }

    private void exportQueries(
            TrainingRowWriter writer, ExportStats stats, Map<Long, Item> itemById, String latestPatch
    ) {
        int page = 0;
        while (stats.exportedQueries < TARGET_QUERIES) {
            // 해시 순서로 뽑는다. match_id 순은 곧 시간순이라 앞에서 자르면 오래된 패치만 남는다.
            List<String> matchIds = participantRepository.findSampledMatchIds(PageRequest.of(page++, 500));
            if (matchIds.isEmpty()) {
                break;
            }
            for (String matchId : matchIds) {
                if (stats.exportedQueries >= TARGET_QUERIES) {
                    return;
                }
                exportMatch(matchId, writer, stats, itemById, latestPatch);
            }
        }
    }

    private void exportMatch(
            String matchId, TrainingRowWriter writer, ExportStats stats,
            Map<Long, Item> itemById, String latestPatch
    ) {
        List<NormalizedMatchParticipant> matchParticipants = participantRepository.findByMatchId(matchId);
        int used = 0;
        for (NormalizedMatchParticipant participant
                : participantSampler.sample(matchParticipants, matchId, matchParticipants.size())) {
            if (used >= PARTICIPANTS_PER_MATCH) {
                return;
            }
            List<SnapshotQuery> snapshots = snapshotQueryBuilder.build(
                    matchParticipants, participant.getPuuid(), participant.getPatch());
            if (snapshots.isEmpty()) {
                continue;
            }
            used++;
            Map<Long, Double> baseRates = baseRatesOf(participant);
            for (SnapshotQuery snapshot : snapshots) {
                exportSnapshot(snapshot, snapshots, writer, stats, itemById, baseRates, latestPatch);
            }
        }
    }

    private void exportSnapshot(
            SnapshotQuery snapshot, List<SnapshotQuery> allSnapshots, TrainingRowWriter writer,
            ExportStats stats, Map<Long, Item> itemById, Map<Long, Double> baseRates, String latestPatch
    ) {
        stats.attemptedQueries++;

        List<GeneratorResult> results = new ArrayList<>();
        for (CandidateGenerator generator : generators) {
            results.add(generator.generate(snapshot.query(), candidateTopK.of(generator.source())));
        }
        CandidateUnion valid = hardValidityFilter.filter(
                CandidateUnion.merge(results), snapshot.query().purchasedItemIds(), itemById);

        boolean groundTruthSurvived = valid.candidates().stream()
                .anyMatch(candidate -> candidate.itemId() == snapshot.groundTruthItemId());
        if (!groundTruthSurvived) {
            // 정답이 후보에 없으면 이 query로는 랭킹을 학습할 수 없다. 버리고 비율을 남긴다.
            stats.droppedQueries++;
            return;
        }

        List<Long> laterPurchases = allSnapshots.stream()
                .filter(other -> other.purchaseStep() > snapshot.purchaseStep())
                .map(SnapshotQuery::groundTruthItemId)
                .toList();
        RelevanceLabeler.Context context = new RelevanceLabeler.Context(
                snapshot.groundTruthItemId(), laterPurchases, baseRates);

        String qid = snapshot.matchId() + "#" + snapshot.query().myChampionId() + "#" + snapshot.purchaseStep();
        String splitGame = gameSplit.isTrain(snapshot.matchId()) ? "train" : "test";
        String splitPatch = snapshot.query().patch().equals(latestPatch) ? "test" : "train";

        for (CandidateFeatures features : featurePipeline.extract(valid, snapshot.query())) {
            int label = labeler.label(features.itemId(), context);
            stats.countLabel(label);
            writer.write(new TrainingRow(
                    qid, label, features.itemId(), features.vector(),
                    snapshot.matchId(), snapshot.query().patch(),
                    Math.toIntExact(snapshot.query().myChampionId()),
                    snapshot.query().position().name(), snapshot.purchaseStep(),
                    splitGame, splitPatch));
        }
        stats.exportedQueries++;
        stats.rowsWithGroundTruth++;
        stats.countCoverage(snapshot.query().position(), snapshot.query().myChampionId(),
                snapshot.query().patch());
    }

    private Map<Long, Double> baseRatesOf(NormalizedMatchParticipant participant) {
        ChampionPosition position = new dfgg.application.ChampionPositionNormalizer()
                .normalize(participant.getPosition()).orElseThrow();
        List<ChampionItemStats> stats = championItemStatsRepository
                .findByChampionIdAndPosition(participant.getChampionId(), position);
        int gameCount = stats.stream().mapToInt(ChampionItemStats::getChampionGameCountAll).max().orElse(0);
        if (gameCount == 0) {
            return Map.of();
        }
        Map<Long, Double> baseRates = new HashMap<>();
        stats.forEach(stat -> baseRates.put(
                stat.getItemId(), (double) stat.getPurchaseCountAll() / gameCount));
        return baseRates;
    }

    private String latestPatch() {
        return participantRepository.findDistinctPatches().stream()
                .map(PatchVersion::of)
                .max(Comparator.naturalOrder())
                .orElseThrow()
                .value();
    }

    /** 표본이 전체를 대표하는지, 무엇을 얼마나 버렸는지를 남긴다. */
    private static final class ExportStats {

        private int attemptedQueries;
        private int exportedQueries;
        private int droppedQueries;
        private int rowsWithGroundTruth;
        private final Map<Integer, Long> labelCounts = new LinkedHashMap<>();
        private final Map<ChampionPosition, Long> positionCounts = new LinkedHashMap<>();
        private final Map<Long, Long> championCounts = new HashMap<>();
        private final Map<String, Long> patchCounts = new HashMap<>();

        private void countLabel(int label) {
            labelCounts.merge(label, 1L, Long::sum);
        }

        private void countCoverage(ChampionPosition position, long championId, String patch) {
            positionCounts.merge(position, 1L, Long::sum);
            championCounts.merge(championId, 1L, Long::sum);
            patchCounts.merge(patch, 1L, Long::sum);
        }

        private String render(Path outputPath, long durationMillis, String latestPatch) {
            long totalRows = labelCounts.values().stream().mapToLong(Long::longValue).sum();
            StringBuilder report = new StringBuilder();
            report.append("# LTR 학습 데이터 export 결과\n\n");
            report.append("| 항목 | 값 |\n|---|---|\n");
            report.append("| 출력 | `").append(outputPath).append("` |\n");
            report.append("| 시도한 query | ").append(attemptedQueries).append(" |\n");
            report.append("| 내보낸 query | ").append(exportedQueries).append(" |\n");
            report.append("| 제외된 query | ").append(droppedQueries).append(" (")
                    .append(String.format("%.1f%%", 100.0 * droppedQueries / Math.max(attemptedQueries, 1)))
                    .append(" — 정답이 후보에 없어 랭킹을 학습할 수 없는 경우) |\n");
            report.append("| 총 행 수 | ").append(totalRows).append(" |\n");
            report.append("| query당 평균 후보 | ")
                    .append(String.format("%.1f", (double) totalRows / Math.max(exportedQueries, 1))).append(" |\n");
            report.append("| 최신 패치(patch split의 test) | ").append(latestPatch).append(" |\n");
            report.append("| 소요 시간 | ").append(durationMillis / 1000).append("초 |\n");
            report.append("| 처리 속도 | ")
                    .append(String.format("%.1f query/s", 1000.0 * exportedQueries / Math.max(durationMillis, 1)))
                    .append(" |\n\n");

            report.append("## 라벨 분포\n\n| 등급 | 의미 | 행 수 | 비중 |\n|---|---|---|---|\n");
            Map<Integer, String> meanings = Map.of(
                    3, "실제 다음 구매", 2, "같은 게임에서 이후 구매",
                    1, "그럴듯한 대안", 0, "나머지");
            for (int label = 3; label >= 0; label--) {
                long count = labelCounts.getOrDefault(label, 0L);
                report.append("| ").append(label).append(" | ").append(meanings.get(label))
                        .append(" | ").append(count).append(" | ")
                        .append(String.format("%.1f%%", 100.0 * count / Math.max(totalRows, 1))).append(" |\n");
            }

            report.append("\n## 표본 대표성\n\n| 포지션 | query | 비중 |\n|---|---|---|\n");
            for (ChampionPosition position : ChampionPosition.values()) {
                long count = positionCounts.getOrDefault(position, 0L);
                report.append("| ").append(position).append(" | ").append(count).append(" | ")
                        .append(String.format("%.1f%%", 100.0 * count / Math.max(exportedQueries, 1)))
                        .append(" |\n");
            }
            report.append("\n고유 챔피언 수: **").append(championCounts.size()).append("종**\n");

            report.append("\n### 패치 분포 (상위 10)\n\n| 패치 | query |\n|---|---|\n");
            patchCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> report.append("| ").append(entry.getKey())
                            .append(" | ").append(entry.getValue()).append(" |\n"));
            long latestPatchQueries = patchCounts.getOrDefault(latestPatch, 0L);
            report.append("\n최신 패치(").append(latestPatch).append(") query: **")
                    .append(latestPatchQueries).append("건** — patch split의 test 세트\n");
            return report.toString();
        }
    }
}
