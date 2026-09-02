package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationResult;
import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.GeneratorResult;
import dfgg.application.recommend.v3.ScoredItem;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Generator별 Recall@K와 Union Recall을 실 매치 데이터로 측정한다.
 *
 * <p><b>Union recall이 이 프로젝트의 천장이다.</b> 후보에 없는 아이템은 아무리 좋은 랭커도
 * 순위를 매길 수 없으므로, 이 수치가 낮으면 LTR을 학습해도 소용이 없다. Phase 3 진입 전
 * 확인해야 하는 이유다.
 *
 * <p>기본 {@code test} 태스크에서 제외된다. 실행:
 * {@code ./gradlew evaluationTest --tests '*RecallEvaluationTest'}
 */
@SpringBootTest
@ActiveProfiles("evaluation")
@Tag("evaluation")
class RecallEvaluationTest {

    /** 평가에 쓸 매치 수. {@code -Devaluation.matches=N}으로 조정한다. */
    private static final int SAMPLE_MATCHES = Integer.getInteger("evaluation.matches", 150);

    /** 매치당 평가할 참가자 수. 10명 전부 쓰면 한 게임에 치우친다. */
    private static final int PARTICIPANTS_PER_MATCH = 2;

    /**
     * 아이템 카탈로그가 159종뿐이고 챔피언·포지션당 실제 구매 아이템은 평균 26.8종이다.
     * Top-K를 100으로 잡으면 generator가 그 챔피언이 산 아이템을 전부 반환해 recall이 자명하게
     * 100%가 된다(실측으로 확인). 후보 수를 실제로 좁히는 구간에서 측정해야 K 선택에 근거가 된다.
     */
    private static final int RETRIEVAL_TOP_K = 50;
    private static final List<Integer> REPORTED_K = List.of(1, 3, 5, 10, 20, 30, 50);

    /** 실 데이터가 아닌 DB를 가리키면 즉시 실패시키기 위한 하한. */
    private static final long MINIMUM_EXPECTED_PARTICIPANTS = 100_000L;

    private static final int RECENT_PATCH_WINDOW = 3;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;
    @Autowired
    private ItemStatsAggregationService aggregationService;
    @Autowired
    private List<CandidateGenerator> generators;

    private final SnapshotQueryBuilder snapshotQueryBuilder = new SnapshotQueryBuilder();
    private final ParticipantSampler participantSampler = new ParticipantSampler();
    private final GameSplit gameSplit = new GameSplit(0.8);

    @Test
    @DisplayName("Generator별 Recall@20/50/100과 Union Recall을 측정한다")
    void measureRecall() throws IOException {
        long participantCount = participantRepository.count();
        assertThat(participantCount)
                .as("실 매치 데이터가 있는 DB를 가리켜야 한다 (dfgg_backtest). 참가자 수")
                .isGreaterThan(MINIMUM_EXPECTED_PARTICIPANTS);

        ItemStatsAggregationResult aggregation = aggregationService.aggregate(RECENT_PATCH_WINDOW);
        List<SnapshotQuery> snapshots = collectTestSnapshots();

        RecallTally tally = new RecallTally();
        long startedAt = System.currentTimeMillis();
        for (SnapshotQuery snapshot : snapshots) {
            Map<CandidateSource, List<Long>> rankedBySource = new EnumMap<>(CandidateSource.class);
            for (CandidateGenerator generator : generators) {
                GeneratorResult result = generator.generate(snapshot.query(), RETRIEVAL_TOP_K);
                rankedBySource.put(generator.source(),
                        result.rankedItems().stream().map(ScoredItem::itemId).toList());
            }
            tally.record(snapshot.query().position(), snapshot.purchaseStep(),
                    rankedBySource, snapshot.groundTruthItemId());
        }
        long durationMillis = System.currentTimeMillis() - startedAt;

        String report = renderReport(tally, aggregation, participantCount, snapshots, durationMillis);
        System.out.println(report);
        writeReport(report);

        assertThat(tally.queryCount()).as("평가할 스냅샷이 있어야 한다").isPositive();
    }

    /**
     * test 분할에 속한 매치에서만 스냅샷을 만든다. 통계 집계는 전체 데이터로 하므로 엄밀히는
     * train 정보가 통계에 섞여 있다 — 이 단계의 목적은 후보 생성 능력의 상한(recall) 측정이고,
     * 누수 없는 평가는 T14의 최종 랭킹 평가에서 학습/평가를 분리해 수행한다.
     */
    private List<SnapshotQuery> collectTestSnapshots() {
        List<SnapshotQuery> snapshots = new ArrayList<>();
        int page = 0;
        int sampledMatches = 0;

        while (sampledMatches < SAMPLE_MATCHES) {
            // 해시 순서. match_id 순은 시간순이라 앞에서 자르면 오래된 패치만 표본에 들어간다.
            List<String> matchIds = participantRepository.findSampledMatchIds(PageRequest.of(page++, 500));
            if (matchIds.isEmpty()) {
                break;
            }
            for (String matchId : matchIds) {
                if (sampledMatches >= SAMPLE_MATCHES) {
                    break;
                }
                if (gameSplit.isTrain(matchId)) {
                    continue;
                }
                List<SnapshotQuery> fromMatch = snapshotsOf(matchId);
                if (!fromMatch.isEmpty()) {
                    snapshots.addAll(fromMatch);
                    sampledMatches++;
                }
            }
        }
        return snapshots;
    }

    /**
     * 매치에서 평가할 참가자를 고른다. 앞에서부터 자르면 TOP·JUNGLE만 뽑히므로
     * ({@link ParticipantSampler} 참고) 매치 ID 시드로 셔플한 뒤 앞에서 가져온다.
     */
    private List<SnapshotQuery> snapshotsOf(String matchId) {
        List<NormalizedMatchParticipant> matchParticipants = participantRepository.findByMatchId(matchId);
        List<SnapshotQuery> snapshots = new ArrayList<>();
        int used = 0;
        for (NormalizedMatchParticipant participant
                : participantSampler.sample(matchParticipants, matchId, matchParticipants.size())) {
            if (used >= PARTICIPANTS_PER_MATCH) {
                break;
            }
            List<SnapshotQuery> built =
                    snapshotQueryBuilder.build(matchParticipants, participant.getPuuid(), participant.getPatch());
            if (!built.isEmpty()) {
                snapshots.addAll(built);
                used++;
            }
        }
        return snapshots;
    }

    private String renderReport(
            RecallTally tally, ItemStatsAggregationResult aggregation,
            long participantCount, List<SnapshotQuery> snapshots, long durationMillis
    ) {
        StringBuilder report = new StringBuilder();
        report.append("# Generator Recall 평가 결과\n\n");
        report.append("## 대상\n\n");
        report.append("| 항목 | 값 |\n|---|---|\n");
        report.append("| 참가자 수(DB 전체) | ").append(participantCount).append(" |\n");
        report.append("| 표본 매치 수 | ").append(SAMPLE_MATCHES).append(" (game split test 쪽) |\n");
        report.append("| 평가 스냅샷(query) 수 | ").append(tally.queryCount()).append(" |\n");
        report.append("| generator별 retrieval Top-K | ").append(RETRIEVAL_TOP_K).append(" |\n");
        report.append("| 아이템 카탈로그 크기 | 159종 |\n");
        report.append("| 챔피언×포지션당 평균 구매 아이템 | 26.8종 |\n");
        report.append("| 최근 패치 윈도 | ").append(RECENT_PATCH_WINDOW)
                .append(" → ").append(aggregation.recentPatches()).append(" |\n");
        report.append("| 소요 시간 | ").append(durationMillis).append("ms |\n\n");

        report.append("## Generator별 Recall@K\n\n");
        report.append("| Generator |");
        REPORTED_K.forEach(k -> report.append(" R@").append(k).append(" |"));
        report.append("\n|---|");
        REPORTED_K.forEach(k -> report.append("---|"));
        report.append("\n");
        for (CandidateSource source : CandidateSource.values()) {
            report.append("| ").append(source).append(" |");
            for (int k : REPORTED_K) {
                report.append(' ').append(percent(tally.recallAt(source, k))).append(" |");
            }
            report.append("\n");
        }

        report.append("\n## 고유 기여 — 그 generator만 정답을 찾은 query 비율\n\n");
        report.append("0이면 다른 generator로 완전히 대체 가능하다는 뜻이다. ");
        report.append("K가 커질수록 모두가 모두를 찾으므로 작은 K에서 봐야 의미가 있다.\n\n");
        report.append("| Generator |");
        REPORTED_K.forEach(k -> report.append(" @").append(k).append(" |"));
        report.append("\n|---|");
        REPORTED_K.forEach(k -> report.append("---|"));
        report.append("\n");
        for (CandidateSource source : CandidateSource.values()) {
            report.append("| ").append(source).append(" |");
            for (int k : REPORTED_K) {
                report.append(' ').append(percent(tally.uniqueContributionOf(source, k))).append(" |");
            }
            report.append("\n");
        }

        report.append("\n## Union Recall — LTR이 넘을 수 없는 천장\n\n");
        report.append("| K | Union Recall |\n|---|---|\n");
        for (int k : REPORTED_K) {
            report.append("| ").append(k).append(" | ").append(percent(tally.unionRecallAt(k))).append(" |\n");
        }
        appendCoverage(report, snapshots);
        appendPerPositionBreakdown(report, tally);
        appendPerStepBreakdown(report, tally);
        return report.toString();
    }

    /**
     * 어떤 챔피언·포지션을 실제로 평가했는지. 이게 없으면 표본이 한쪽으로 쏠려도 알 수 없다 —
     * 실제로 participantId 앞에서부터 자르다가 TOP·JUNGLE만 평가한 적이 있다.
     */
    private void appendCoverage(StringBuilder report, List<SnapshotQuery> snapshots) {
        report.append("\n## 평가 커버리지 — 어떤 챔피언·포지션을 봤는가\n\n");

        Map<dfgg.domain.champion.ChampionPosition, Long> byPosition = snapshots.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        snapshot -> snapshot.query().position(),
                        java.util.stream.Collectors.counting()));
        report.append("| 포지션 | 스냅샷 수 | 비중 |\n|---|---|---|\n");
        long total = snapshots.size();
        for (dfgg.domain.champion.ChampionPosition position : dfgg.domain.champion.ChampionPosition.values()) {
            long count = byPosition.getOrDefault(position, 0L);
            report.append("| ").append(position).append(" | ").append(count)
                    .append(" | ").append(percent(total == 0 ? 0 : (double) count / total)).append(" |\n");
        }

        long distinctChampions = snapshots.stream()
                .map(snapshot -> snapshot.query().myChampionId())
                .distinct()
                .count();
        report.append("\n고유 챔피언 수: **").append(distinctChampions).append("종**");
        report.append(" (DB 전체 173종)\n");
    }

    /**
     * 포지션별 분해. Ally-Synergy는 "서포터는 아군 ADC에 따라 빌드가 갈린다"를 전제로 존재하므로
     * 전체 평균만으로는 그 전제를 검증할 수 없다. 서포터에서만 유효한 신호가 다른 포지션에
     * 묻히는지 여기서 드러난다.
     */
    private void appendPerPositionBreakdown(StringBuilder report, RecallTally tally) {
        for (int k : List.of(5, 10)) {
            report.append("\n## 포지션별 Recall@").append(k).append("\n\n");
            report.append("| 포지션 | 표본 |");
            for (CandidateSource source : CandidateSource.values()) {
                report.append(' ').append(source).append(" |");
            }
            report.append(" **Union** |\n|---|---|");
            for (int i = 0; i < CandidateSource.values().length; i++) {
                report.append("---|");
            }
            report.append("---|\n");
            for (dfgg.domain.champion.ChampionPosition position
                    : dfgg.domain.champion.ChampionPosition.values()) {
                report.append("| ").append(position).append(" | ")
                        .append(tally.queryCountAtPosition(position)).append(" |");
                for (CandidateSource source : CandidateSource.values()) {
                    report.append(' ').append(percent(tally.recallAtPosition(source, k, position))).append(" |");
                }
                report.append(" **").append(percent(tally.unionRecallAtPosition(k, position))).append("** |\n");
            }
        }

        report.append("\n## 포지션별 고유 기여@5 — Ally-Synergy의 전제 검증\n\n");
        report.append("| 포지션 | 표본 |");
        for (CandidateSource source : CandidateSource.values()) {
            report.append(' ').append(source).append(" |");
        }
        report.append("\n|---|---|");
        for (int i = 0; i < CandidateSource.values().length; i++) {
            report.append("---|");
        }
        report.append("\n");
        for (dfgg.domain.champion.ChampionPosition position
                : dfgg.domain.champion.ChampionPosition.values()) {
            report.append("| ").append(position).append(" | ")
                    .append(tally.queryCountAtPosition(position)).append(" |");
            for (CandidateSource source : CandidateSource.values()) {
                report.append(' ')
                        .append(percent(tally.uniqueContributionAtPosition(source, 5, position))).append(" |");
            }
            report.append("\n");
        }
    }

    /**
     * 구매 단계별 분해. 이게 없으면 표본이 많은 얕은 단계(0~1코어가 전체의 58%)가 지표를 지배한다.
     * 0코어에서 Build의 "정확 prefix"는 곧 그 챔피언의 1코어 분포라 거의 결정적이지만,
     * 4~5코어에선 정확히 일치하는 표본이 급감해 다른 generator가 일할 여지가 생긴다.
     */
    private void appendPerStepBreakdown(StringBuilder report, RecallTally tally) {
        report.append("\n## 구매 단계별 분해\n\n");
        report.append("0코어(아직 아무것도 안 산 시점)와 4코어는 사실상 다른 문제다. ");
        report.append("얕은 단계일수록 표본이 많아 전체 평균을 지배하므로 나눠서 봐야 한다.\n");

        for (int k : List.of(5, 10, 20)) {
            report.append("\n### Recall@").append(k).append(" — 구매 단계별\n\n");
            report.append("| 코어 시점 | 표본 |");
            for (CandidateSource source : CandidateSource.values()) {
                report.append(' ').append(source).append(" |");
            }
            report.append(" **Union** |\n|---|---|");
            for (int i = 0; i < CandidateSource.values().length; i++) {
                report.append("---|");
            }
            report.append("---|\n");

            for (int step : tally.observedSteps()) {
                report.append("| ").append(step).append("코어 | ")
                        .append(tally.queryCountAtStep(step)).append(" |");
                for (CandidateSource source : CandidateSource.values()) {
                    report.append(' ').append(percent(tally.recallAtStep(source, k, step))).append(" |");
                }
                report.append(" **").append(percent(tally.unionRecallAtStep(k, step))).append("** |\n");
            }
        }

        report.append("\n### 고유 기여@10 — 구매 단계별\n\n");
        report.append("그 generator만 정답을 찾은 비율. Build가 표본을 못 찾는 깊은 단계에서 ");
        report.append("나머지가 실제로 일하는지 보는 지표다.\n\n");
        report.append("| 코어 시점 | 표본 |");
        for (CandidateSource source : CandidateSource.values()) {
            report.append(' ').append(source).append(" |");
        }
        report.append("\n|---|---|");
        for (int i = 0; i < CandidateSource.values().length; i++) {
            report.append("---|");
        }
        report.append("\n");
        for (int step : tally.observedSteps()) {
            report.append("| ").append(step).append("코어 | ")
                    .append(tally.queryCountAtStep(step)).append(" |");
            for (CandidateSource source : CandidateSource.values()) {
                report.append(' ').append(percent(tally.uniqueContributionAtStep(source, 10, step))).append(" |");
            }
            report.append("\n");
        }
    }

    private String percent(double ratio) {
        return String.format("%.1f%%", ratio * 100);
    }

    private void writeReport(String report) throws IOException {
        Path path = Path.of(System.getProperty("evaluation.report.path", "../tasks/eval-recall.md"));
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, report);
        System.out.println("리포트 저장: " + path.toAbsolutePath());
    }
}
