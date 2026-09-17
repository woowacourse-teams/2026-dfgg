package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationResult;
import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.Item;
import dfgg.domain.item.trait.ItemTraitCatalog;
import dfgg.domain.item.trait.Synergy;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.TierScope;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 응답의 {@code description}이 실제로 얼마나 채워지는지 실 데이터로 잰다.
 * <p>
 * 후보 생성 단계의 source 깃발로 추정하지 않고 {@link NextItemRecommendationService}를
 * 그대로 호출한다 — 재려는 것이 "generator가 후보를 냈는가"가 아니라
 * "클라이언트가 받는 응답에 근거 단어가 들어 있는가"이기 때문이다.
 * 특히 {@code CounterEvidence}의 {@code lift > 1.0} 조건은 JSONL에 적별 lift가 없어
 * 오프라인 추정으로는 반영할 수 없다.
 * <p>
 * 실행:
 * {@code ./gradlew evaluationTest --tests '*DescriptionFillRateEvaluationTest'}
 */
@SpringBootTest
@ActiveProfiles("evaluation")
@Tag("evaluation")
class DescriptionFillRateEvaluationTest {

    private static final int SAMPLE_MATCHES = Integer.getInteger("evaluation.matches", 300);
    private static final int PARTICIPANTS_PER_MATCH = 2;
    private static final int RECENT_PATCH_WINDOW = 3;
    private static final long MINIMUM_EXPECTED_PARTICIPANTS = 100_000L;

    @Autowired private NextItemRecommendationService recommendationService;
    @Autowired private NormalizedMatchParticipantRepository participantRepository;
    @Autowired private ChampionRepository championRepository;
    @Autowired private ItemStatsAggregationService aggregationService;
    @Autowired private TierScope tierScope;
    @Autowired private ItemTraitCatalog itemTraitCatalog;

    @Value("${recommendation.counter.minimum-base-rate}")
    private double counterMinimumBaseRate;

    private final SnapshotQueryBuilder snapshotQueryBuilder = new SnapshotQueryBuilder();
    private final GameSplit gameSplit = new GameSplit(0.8);
    private ParticipantSampler participantSampler;

    @BeforeEach
    void prepareSampler() {
        participantSampler = new ParticipantSampler(tierScope);
    }

    @Test
    @DisplayName("description의 counter/ally/traits가 채워지는 비율을 측정한다")
    void measureDescriptionFillRate() {
        assertThat(participantRepository.count())
                .as("실 매치 데이터가 있는 DB를 가리켜야 한다. 참가자 수")
                .isGreaterThan(MINIMUM_EXPECTED_PARTICIPANTS);

        ItemStatsAggregationResult aggregation = aggregationService.aggregate(RECENT_PATCH_WINDOW);
        Map<Long, String> championNames = championRepository.findAll().stream()
                .collect(Collectors.toMap(Champion::getChampionId, champion -> champion.getName().get("ko-KR"), (a, b) -> a));

        FillTally tally = new FillTally(itemTraitCatalog);
        long startedAt = System.currentTimeMillis();
        for (SnapshotQuery snapshot : collectTestSnapshots()) {
            NextItemRecommendationRequest request = toRequest(snapshot, championNames);
            if (request == null) {
                tally.skippedQueries++;
                continue;
            }
            try {
                tally.record(snapshot.query().position().name(),
                        recommendationService.recommendNextItem(request));
            } catch (RuntimeException exception) {
                tally.failedQueries++;
            }
        }

        String report = tally.render(aggregation, System.currentTimeMillis() - startedAt,
                tierScope.values(), counterMinimumBaseRate);
        System.out.println(report);
        new EvaluationReportWriter().write(Path.of("../tasks/eval-description-fill.md"), report);

        assertThat(tally.slots).as("측정한 추천 칸").isPositive();
    }

    /** 이름을 모르는 챔피언이 하나라도 있으면 요청을 만들 수 없다. */
    private NextItemRecommendationRequest toRequest(
            SnapshotQuery snapshot, Map<Long, String> championNames) {
        List<ChampionDto> allies = championDtos(snapshot.query().allyChampionIds(), championNames);
        List<ChampionDto> enemies = championDtos(snapshot.query().enemyChampionIds(), championNames);
        String myName = championNames.get(snapshot.query().myChampionId());
        if (myName == null || allies == null || enemies == null) {
            return null;
        }
        return new NextItemRecommendationRequest(
                new ChampionDto(myName, snapshot.query().position().name()),
                snapshot.query().purchasedItemIds(), allies, enemies,
                snapshot.query().tier(), snapshot.query().patch());
    }

    /** 포지션은 서비스가 쓰지 않지만(이름으로만 id를 찾는다) DTO가 요구하므로 채운다. */
    private List<ChampionDto> championDtos(List<Long> championIds, Map<Long, String> names) {
        List<ChampionDto> dtos = new ArrayList<>();
        for (Long championId : championIds) {
            String name = names.get(championId);
            if (name == null) {
                return null;
            }
            dtos.add(new ChampionDto(name, "MID"));
        }
        return dtos;
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
                    if (used >= PARTICIPANTS_PER_MATCH) {
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

    /**
     * 칸(추천 아이템 하나) 단위로 센다. query 단위로 세면 "5칸 중 1칸만 채워짐"과
     * "5칸 모두 채워짐"이 같은 값이 되어 실제 노출량을 못 본다.
     */
    private static final class FillTally {

        private final ItemTraitCatalog itemTraitCatalog;
        private int queries;
        private int slots;
        private int skippedQueries;
        private int failedQueries;
        private int counterFilled;
        private int allyFilled;
        private int traitsFilled;
        /** ally가 채워진 칸 중 synergy가 ALLY인 아이템. AllyEvidence에 synergy 필터를 걸었을 때의 충전량이다. */
        private int allyOnAllyItems;
        private final Map<String, int[]> byPosition = new HashMap<>();
        private final Map<String, Integer> allySlotsByItem = new HashMap<>();

        FillTally(ItemTraitCatalog itemTraitCatalog) {
            this.itemTraitCatalog = itemTraitCatalog;
        }

        void record(String position, NextItemRecommendationResponse response) {
            queries++;
            int[] counts = byPosition.computeIfAbsent(position, key -> new int[5]);
            for (RecommendedItemDto item : response.recommendedItems()) {
                slots++;
                counts[0]++;
                if (!item.description().counter().isEmpty()) { counterFilled++; counts[1]++; }
                if (!item.description().ally().isEmpty()) {
                    allyFilled++;
                    counts[2]++;
                    Synergy synergy = itemTraitCatalog.synergyOf(new Item(item.id(), item.name()));
                    if (synergy == Synergy.ALLY) {
                        allyOnAllyItems++;
                        counts[4]++;
                    }
                    allySlotsByItem.merge(item.name() + " | " + synergy, 1, Integer::sum);
                }
                if (!item.description().traits().isEmpty()) { traitsFilled++; counts[3]++; }
            }
        }

        String render(ItemStatsAggregationResult aggregation, long durationMillis,
                      List<String> tiers, double counterMinimumBaseRate) {
            StringBuilder out = new StringBuilder("# description 충전율 측정 (실 DB)\n\n");
            out.append("| 항목 | 값 |\n|---|---|\n");
            out.append(String.format("| 티어 범위 | %s |%n", tiers));
            out.append(String.format("| counter base rate 하한 | %.4f |%n", counterMinimumBaseRate));
            out.append(String.format("| 집계 최근 패치 | %s |%n", aggregation.recentPatches()));
            out.append(String.format("| 측정 query | %,d |%n", queries));
            out.append(String.format("| 추천 칸 | %,d |%n", slots));
            out.append(String.format("| 건너뜀(이름 미상) | %,d |%n", skippedQueries));
            out.append(String.format("| 실패(추천 없음 등) | %,d |%n", failedQueries));
            out.append(String.format("| 소요 | %d초 |%n%n", durationMillis / 1000));

            out.append("## 칸 기준 충전율\n\n| 키 | 채워진 칸 | 비율 |\n|---|---|---|\n");
            out.append(row("counter", counterFilled));
            out.append(row("ally", allyFilled));
            out.append(row("ally — ALLY 아이템만 (synergy 필터 후)", allyOnAllyItems));
            out.append(row("traits", traitsFilled));

            out.append("\n## 포지션별\n\n| 포지션 | 칸 | counter | ally | ally (필터 후) | traits |\n|---|---|---|---|---|---|\n");
            byPosition.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                int[] c = entry.getValue();
                out.append(String.format("| %s | %,d | %.1f%% | %.1f%% | %.1f%% | %.1f%% |%n",
                        entry.getKey(), c[0], percent(c[1], c[0]), percent(c[2], c[0]),
                        percent(c[4], c[0]), percent(c[3], c[0])));
            });

            out.append("\n## ally가 채워진 칸 — 아이템별 상위 25\n\n| 아이템 | synergy | 칸 |\n|---|---|---|\n");
            allySlotsByItem.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(25)
                    .forEach(entry -> {
                        String[] parts = entry.getKey().split(" \\| ");
                        out.append(String.format("| %s | %s | %,d |%n", parts[0], parts[1], entry.getValue()));
                    });
            return out.toString();
        }

        private String row(String label, int count) {
            return String.format("| %s | %,d | %.2f%% |%n", label, count, percent(count, slots));
        }

        private double percent(int part, int total) {
            return total == 0 ? 0.0 : 100.0 * part / total;
        }
    }
}
