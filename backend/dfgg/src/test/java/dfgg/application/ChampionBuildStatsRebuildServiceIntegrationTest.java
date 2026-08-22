package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import dfgg.application.item.ItemService;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.MatchParticipantCohort;
import dfgg.domain.match.MatchParticipantCohortRepository;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.NormalizedParticipant;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CompositionStatsSample;
import dfgg.domain.stats.CompositionStatsSampleRepository;
import dfgg.domain.stats.StatsAggregationCompletionRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "stats.rebuild.batch-size=1")
@Import({
        ChampionBuildStatsRebuildService.class,
        ChampionBuildStatsMatchProcessor.class,
        ChampionBuildStatsAggregationService.class,
        ItemService.class,
        NormalizedMatchPersistenceService.class,
        MatchNormalizer.class,
        CoreItemPurchaseOrderCalculator.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChampionBuildStatsRebuildServiceIntegrationTest {

    @Autowired
    private ChampionBuildStatsRebuildService rebuildService;

    @Autowired
    private RawMatchRepository rawMatchRepository;

    @Autowired
    private RawMatchTimelineRepository rawMatchTimelineRepository;

    @Autowired
    private ChampionRepository championRepository;

    @Autowired
    private ItemRepository itemRepository;

    @MockitoBean
    private DataDragonClient dataDragonClient;

    @Autowired
    private MatchParticipantCohortRepository cohortRepository;

    @Autowired
    private NormalizedMatchParticipantRepository normalizedRepository;

    @Autowired
    private ChampionBuildStatsRepository statsRepository;

    @Autowired
    private CompositionStatsSampleRepository sampleRepository;

    @Autowired
    private StatsAggregationCompletionRepository completionRepository;

    @Autowired
    private ChampionBuildStatsMatchProcessor matchProcessor;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private ChampionBuildStatsAggregationService aggregationService;

    @BeforeEach
    void cleanUp() {
        completionRepository.deleteAllInBatch();
        sampleRepository.deleteAllInBatch();
        statsRepository.deleteAll();
        normalizedRepository.deleteAllInBatch();
        cohortRepository.deleteAllInBatch();
        rawMatchTimelineRepository.deleteAllInBatch();
        rawMatchRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        championRepository.deleteAllInBatch();
    }

    @Test
    void 특정_티어를_집계해도_기존의_다른_티어_파생_데이터를_삭제하지_않는다() {
        Champion champion = championRepository.save(new Champion(
                1L,
                "Aatrox",
                "아트록스",
                List.of(ChampionTag.FIGHTER)
        ));
        Item item = itemRepository.save(new Item(3071L, "아이템 A"));
        NormalizedMatch existingMatch = new NormalizedMatch(
                "KR_EXISTING",
                "16.15",
                420,
                List.of(new NormalizedParticipant(
                        "p-existing",
                        1,
                        1,
                        100,
                        "TOP",
                        true,
                        List.of(3071),
                        List.of(3071),
                        true
                ))
        );
        normalizedRepository.saveAndFlush(new NormalizedMatchParticipant(
                existingMatch,
                existingMatch.participants().getFirst()
        ));
        ChampionBuildStats existingStats = statsRepository.saveAndFlush(new ChampionBuildStats(
                "16.15",
                420,
                champion,
                ChampionPosition.TOP,
                null,
                null,
                null,
                null,
                null,
                "GOLD",
                "3071",
                List.of(item),
                1,
                1
        ));
        sampleRepository.saveAndFlush(new CompositionStatsSample(
                existingStats,
                "KR_EXISTING",
                "p-existing"
        ));
        Long existingStatsId = existingStats.getId();

        ChampionBuildStatsRebuildResult result = rebuildService.rebuildAll("PLATINUM");

        assertThat(result).isEqualTo(new ChampionBuildStatsRebuildResult(0, 0, 0, 0));
        assertThat(normalizedRepository.findByMatchId("KR_EXISTING")).hasSize(1);
        assertThat(statsRepository.findById(existingStatsId))
                .get()
                .satisfies(stats -> {
                    assertThat(stats.getTier()).isEqualTo("GOLD");
                    assertThat(stats.getGameCount()).isEqualTo(1);
                    assertThat(stats.getWinCount()).isEqualTo(1);
                });
        assertThat(sampleRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_raw_데이터를_반복_집계해도_정규화와_통계가_중복되지_않는다() {
        championRepository.saveAll(List.of(
                new Champion(1L, "Aatrox", "아트록스", List.of(ChampionTag.FIGHTER)),
                new Champion(2L, "Ally", "아군", List.of(ChampionTag.FIGHTER)),
                new Champion(3L, "Enemy", "적군", List.of(ChampionTag.TANK))
        ));
        itemRepository.saveAll(List.of(
                new Item(3071L, "아이템 A"),
                new Item(6610L, "아이템 B")
        ));
        cohortRepository.save(new MatchParticipantCohort(
                "KR_1",
                "p-focal",
                "RANKED_SOLO_5x5",
                "PLATINUM",
                "I",
                java.time.Instant.parse("2026-08-06T08:00:00Z")
        ));
        rawMatchRepository.save(new RawMatch("KR_1", """
                {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                  {"puuid":"p-focal","participantId":1,"championId":1,"teamId":100,
                   "teamPosition":"TOP","item0":3071,"item1":6610,"win":true},
                  {"puuid":"p-ally","participantId":2,"championId":2,"teamId":100,"win":false},
                  {"puuid":"p-enemy","participantId":3,"championId":3,"teamId":200,"win":false}
                ]}}
                """));
        rawMatchTimelineRepository.save(new RawMatchTimeline("KR_1", """
                {"metadata":{"participants":["p-focal","p-ally","p-enemy"]},"info":{"frames":[
                  {"events":[
                    {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071},
                    {"timestamp":200,"type":"ITEM_PURCHASED","participantId":1,"itemId":6610}
                  ]}
                ]}}
                """));

        ChampionBuildStatsRebuildResult firstResult = rebuildService.rebuildAll("PLATINUM");
        assertThat(firstResult).isEqualTo(new ChampionBuildStatsRebuildResult(1, 1, 0, 32));
        assertThat(normalizedRepository.count()).isEqualTo(3);
        assertThat(statsRepository.count()).isEqualTo(32);
        assertThat(sampleRepository.count()).isEqualTo(32);

        ChampionBuildStatsRebuildResult secondResult = rebuildService.rebuildAll("PLATINUM");
        assertThat(secondResult).isEqualTo(new ChampionBuildStatsRebuildResult(0, 0, 0, 0));
        assertThat(normalizedRepository.count()).isEqualTo(3);
        assertThat(statsRepository.count()).isEqualTo(32);
        assertThat(sampleRepository.count()).isEqualTo(32);
        assertThat(completionRepository.count()).isEqualTo(1);
        assertThat(statsRepository.findAll())
                .allSatisfy(stats -> {
                    assertThat(stats.getGameCount()).isEqualTo(1);
                    assertThat(stats.getWinCount()).isEqualTo(1);
                });
    }

    @Test
    void 한_매치가_실패해도_성공한_매치의_통계는_독립적으로_커밋된다() {
        championRepository.saveAll(List.of(
                new Champion(1L, "Aatrox", "아트록스", List.of(ChampionTag.FIGHTER)),
                new Champion(2L, "Ally", "아군", List.of(ChampionTag.FIGHTER)),
                new Champion(3L, "Enemy", "적군", List.of(ChampionTag.TANK))
        ));
        itemRepository.saveAll(List.of(
                new Item(3071L, "아이템 A"),
                new Item(6610L, "아이템 B")
        ));
        cohortRepository.save(new MatchParticipantCohort(
                "KR_VALID",
                "p-focal",
                "RANKED_SOLO_5x5",
                "PLATINUM",
                "I",
                java.time.Instant.parse("2026-08-06T08:00:00Z")
        ));
        cohortRepository.save(new MatchParticipantCohort(
                "KR_INVALID",
                "p-focal",
                "RANKED_SOLO_5x5",
                "PLATINUM",
                "I",
                java.time.Instant.parse("2026-08-06T08:00:00Z")
        ));
        String rawMatchData = """
                {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                  {"puuid":"p-focal","participantId":1,"championId":1,"teamId":100,
                   "teamPosition":"TOP","item0":3071,"item1":6610,"win":true},
                  {"puuid":"p-ally","participantId":2,"championId":2,"teamId":100,"win":false},
                  {"puuid":"p-enemy","participantId":3,"championId":3,"teamId":200,"win":false}
                ]}}
                """;
        String timelineData = """
                {"metadata":{"participants":["p-focal","p-ally","p-enemy"]},"info":{"frames":[
                  {"events":[
                    {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071},
                    {"timestamp":200,"type":"ITEM_PURCHASED","participantId":1,"itemId":6610}
                  ]}
                ]}}
                """;
        rawMatchRepository.saveAll(List.of(
                new RawMatch("KR_VALID", rawMatchData),
                new RawMatch("KR_INVALID", rawMatchData)
        ));
        rawMatchTimelineRepository.saveAll(List.of(
                new RawMatchTimeline("KR_VALID", timelineData),
                new RawMatchTimeline("KR_INVALID", timelineData)
        ));
        AtomicBoolean failInvalidOnce = new AtomicBoolean(true);
        doAnswer(invocation -> {
            NormalizedMatch match = invocation.getArgument(0);
            if (match.matchId().equals("KR_INVALID") && failInvalidOnce.getAndSet(false)) {
                throw new IllegalStateException("forced aggregation failure");
            }
            return invocation.callRealMethod();
        }).when(aggregationService).aggregate(any(NormalizedMatch.class), anyString(), anyCollection());

        ChampionBuildStatsRebuildResult result = rebuildService.rebuildAll("PLATINUM");

        assertThat(result.totalMatches()).isEqualTo(2);
        assertThat(result.processedMatches()).isEqualTo(1);
        assertThat(result.skippedMissingTimeline()).isZero();
        assertThat(result.failedMatches()).isEqualTo(1);
        assertThat(result.recordedSamples()).isEqualTo(32);
        assertThat(result.failures())
                .singleElement()
                .satisfies(failure -> {
                    assertThat(failure.matchId()).isEqualTo("KR_INVALID");
                    assertThat(failure.reason())
                            .isEqualTo("IllegalStateException: forced aggregation failure");
                });
        assertThat(normalizedRepository.findByMatchId("KR_VALID")).hasSize(3);
        assertThat(normalizedRepository.findByMatchId("KR_INVALID")).isEmpty();
        assertThat(statsRepository.count()).isEqualTo(32);
        assertThat(sampleRepository.count()).isEqualTo(32);
        assertThat(completionRepository.count()).isEqualTo(1);

        ChampionBuildStatsRebuildResult retryResult = rebuildService.rebuildAll("PLATINUM");

        assertThat(retryResult).isEqualTo(new ChampionBuildStatsRebuildResult(1, 1, 0, 32));
        assertThat(normalizedRepository.findByMatchId("KR_INVALID")).hasSize(3);
        assertThat(sampleRepository.count()).isEqualTo(64);
        assertThat(completionRepository.count()).isEqualTo(2);
    }

    @Test
    void sample이_없는_정상_대상도_완료로_기록해서_재처리하지_않는다() {
        saveCohort("KR_EMPTY", "p-empty");
        rawMatchRepository.save(new RawMatch("KR_EMPTY", """
                {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                  {"puuid":"p-empty","participantId":1,"championId":1,"teamId":100,
                   "teamPosition":"TOP","win":false}
                ]}}
                """));
        rawMatchTimelineRepository.save(new RawMatchTimeline("KR_EMPTY", """
                {"metadata":{"participants":["p-empty"]},"info":{"frames":[{"events":[]}]}}
                """));

        ChampionBuildStatsRebuildResult firstResult = rebuildService.rebuildAll("PLATINUM");
        ChampionBuildStatsRebuildResult secondResult = rebuildService.rebuildAll("PLATINUM");

        assertThat(firstResult).isEqualTo(new ChampionBuildStatsRebuildResult(1, 1, 0, 0));
        assertThat(secondResult).isEqualTo(new ChampionBuildStatsRebuildResult(0, 0, 0, 0));
        assertThat(completionRepository.count()).isEqualTo(1);
        assertThat(sampleRepository.count()).isZero();
    }

    @Test
    void batch_경계에_같은_매치의_참가자가_걸려도_한_매치로_모두_처리한다() {
        prepareReferenceData();
        saveCompleteMatch("KR_BATCH");
        saveCohort("KR_BATCH", "p-focal");
        saveCohort("KR_BATCH", "p-ally");

        ChampionBuildStatsRebuildResult result = rebuildService.rebuildAll("PLATINUM");

        assertThat(result).isEqualTo(new ChampionBuildStatsRebuildResult(1, 1, 0, 64));
        assertThat(completionRepository.count()).isEqualTo(2);
        assertThat(sampleRepository.count()).isEqualTo(64);
        assertThat(normalizedRepository.findByMatchId("KR_BATCH")).hasSize(3);
    }

    @Test
    void 완료된_매치에_새_cohort_참가자가_추가되면_그_참가자만_집계한다() {
        prepareReferenceData();
        saveCompleteMatch("KR_NEW_COHORT");
        saveCohort("KR_NEW_COHORT", "p-focal");

        ChampionBuildStatsRebuildResult firstResult = rebuildService.rebuildAll("PLATINUM");
        saveCohort("KR_NEW_COHORT", "p-ally");
        ChampionBuildStatsRebuildResult secondResult = rebuildService.rebuildAll("PLATINUM");

        assertThat(firstResult).isEqualTo(new ChampionBuildStatsRebuildResult(1, 1, 0, 32));
        assertThat(secondResult).isEqualTo(new ChampionBuildStatsRebuildResult(1, 1, 0, 32));
        assertThat(completionRepository.count()).isEqualTo(2);
        assertThat(sampleRepository.count()).isEqualTo(64);
        assertThat(statsRepository.findAll())
                .allSatisfy(stats -> assertThat(stats.getGameCount()).isEqualTo(1));
    }

    @Test
    void 기존_sample이_일부만_있으면_중복_count_없이_나머지를_채우고_완료로_기록한다() {
        prepareReferenceData();
        saveCompleteMatch("KR_LEGACY");
        saveCohort("KR_LEGACY", "p-focal");
        Champion champion = championRepository.findById(1L).orElseThrow();
        List<Item> buildItems = itemRepository.findAllById(List.of(3071L, 6610L));
        ChampionBuildStats existingStats = statsRepository.saveAndFlush(new ChampionBuildStats(
                "16.15",
                420,
                champion,
                ChampionPosition.TOP,
                null,
                null,
                null,
                null,
                null,
                "PLATINUM",
                "3071>6610",
                buildItems,
                1,
                1
        ));
        sampleRepository.saveAndFlush(new CompositionStatsSample(
                existingStats,
                "KR_LEGACY",
                "p-focal"
        ));

        ChampionBuildStatsRebuildResult result = rebuildService.rebuildAll("PLATINUM");

        assertThat(result).isEqualTo(new ChampionBuildStatsRebuildResult(1, 1, 0, 31));
        assertThat(completionRepository.count()).isEqualTo(1);
        assertThat(statsRepository.count()).isEqualTo(32);
        assertThat(sampleRepository.count()).isEqualTo(32);
        assertThat(statsRepository.findAll())
                .allSatisfy(stats -> {
                    assertThat(stats.getGameCount()).isEqualTo(1);
                    assertThat(stats.getWinCount()).isEqualTo(1);
                });
    }

    @Test
    void 같은_대상을_동시에_claim해도_한_트랜잭션만_집계한다() throws Exception {
        prepareReferenceData();
        saveCompleteMatch("KR_CONCURRENT");
        RawMatch rawMatch = rawMatchRepository.findById("KR_CONCURRENT").orElseThrow();
        RawMatchTimeline timeline = rawMatchTimelineRepository.findById("KR_CONCURRENT").orElseThrow();
        CountDownLatch readySignal = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);

        List<ChampionBuildStatsMatchProcessor.Result> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ChampionBuildStatsMatchProcessor.Result> first = executor.submit(() -> processAfterSignal(
                    rawMatch, timeline, readySignal, startSignal
            ));
            Future<ChampionBuildStatsMatchProcessor.Result> second = executor.submit(() -> processAfterSignal(
                    rawMatch, timeline, readySignal, startSignal
            ));

            assertThat(readySignal.await(5, TimeUnit.SECONDS)).isTrue();
            startSignal.countDown();
            results = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );
        }

        assertThat(results).extracting(ChampionBuildStatsMatchProcessor.Result::claimedParticipants)
                .containsExactlyInAnyOrder(1, 0);
        assertThat(results).extracting(ChampionBuildStatsMatchProcessor.Result::recordedSamples)
                .containsExactlyInAnyOrder(32, 0);
        assertThat(completionRepository.count()).isEqualTo(1);
        assertThat(sampleRepository.count()).isEqualTo(32);
    }

    @Test
    void 변경된_매치를_replay하면_이전_기여를_빼고_새_기여만_남긴다() {
        prepareReferenceData();
        saveCompleteMatch("KR_REPLAY");
        saveCohort("KR_REPLAY", "p-focal");
        rebuildService.rebuildAll("PLATINUM");

        replaceFocalBuildAndWin("KR_REPLAY");
        ChampionBuildStatsRebuildResult firstReplay = rebuildService.replayOne("KR_REPLAY", "PLATINUM");
        ChampionBuildStatsRebuildResult secondReplay = rebuildService.replayOne("KR_REPLAY", "PLATINUM");

        assertThat(firstReplay).isEqualTo(new ChampionBuildStatsRebuildResult(1, 1, 0, 32));
        assertThat(secondReplay).isEqualTo(new ChampionBuildStatsRebuildResult(1, 1, 0, 32));
        assertThat(sampleRepository.count()).isEqualTo(32);
        assertThat(sampleRepository.findAll()).allSatisfy(sample -> assertThat(sample.getWin()).isFalse());
        assertThat(statsRepository.findAll().stream()
                .filter(stats -> stats.getGameCount() == 0))
                .hasSize(32)
                .allSatisfy(stats -> assertThat(stats.getBuildKey()).isEqualTo("3071>6610"));
        assertThat(statsRepository.findAll().stream()
                .filter(stats -> stats.getGameCount() == 1))
                .hasSize(32)
                .allSatisfy(stats -> {
                    assertThat(stats.getBuildKey()).isEqualTo("6610>3071");
                    assertThat(stats.getWinCount()).isZero();
                });
        assertThat(statsRepository.findBestMatchingStatsForScope(
                "16.15", 420, "PLATINUM", 1L, "TOP",
                false, false, false, false, false
        )).get().satisfies(stats -> assertThat(stats.getBuildKey()).isEqualTo("6610>3071"));
    }

    @Test
    void 기존_sample의_win이_null이면_저장된_정규화_승패로_보완한_뒤_replay한다() {
        prepareReferenceData();
        saveCompleteMatch("KR_BACKFILL");
        saveCohort("KR_BACKFILL", "p-focal");
        rebuildService.rebuildAll("PLATINUM");
        setSampleWinToNull("KR_BACKFILL", "p-focal");

        ChampionBuildStatsRebuildResult result = rebuildService.replayOne("KR_BACKFILL", "PLATINUM");

        assertThat(result).isEqualTo(new ChampionBuildStatsRebuildResult(1, 1, 0, 32));
        assertThat(sampleRepository.findAll())
                .hasSize(32)
                .allSatisfy(sample -> assertThat(sample.getWin()).isTrue());
        assertThat(statsRepository.findAll()).allSatisfy(stats -> {
            assertThat(stats.getGameCount()).isEqualTo(1);
            assertThat(stats.getWinCount()).isEqualTo(1);
        });
    }

    @Test
    void 기존_승패를_복원할_수_없으면_replay를_중단하고_통계를_보존한다() {
        prepareReferenceData();
        saveCompleteMatch("KR_UNKNOWN_WIN");
        saveCohort("KR_UNKNOWN_WIN", "p-focal");
        rebuildService.rebuildAll("PLATINUM");
        setSampleWinToNull("KR_UNKNOWN_WIN", "p-focal");
        normalizedRepository.deleteAllInBatch();

        assertThatThrownBy(() -> rebuildService.replayOne("KR_UNKNOWN_WIN", "PLATINUM"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("previous win contributions are unknown");
        assertThat(sampleRepository.count()).isEqualTo(32);
        assertThat(sampleRepository.findAll()).allSatisfy(sample -> assertThat(sample.getWin()).isNull());
        assertThat(statsRepository.findAll()).allSatisfy(stats -> {
            assertThat(stats.getGameCount()).isEqualTo(1);
            assertThat(stats.getWinCount()).isEqualTo(1);
        });
    }

    private ChampionBuildStatsMatchProcessor.Result processAfterSignal(
            RawMatch rawMatch,
            RawMatchTimeline timeline,
            CountDownLatch readySignal,
            CountDownLatch startSignal
    ) {
        readySignal.countDown();
        try {
            startSignal.await();
            return matchProcessor.rebuild(
                    rawMatch,
                    timeline,
                    "RANKED_SOLO_5x5",
                    "PLATINUM",
                    List.of("p-focal"),
                    Set.of(3071, 6610),
                    "v1"
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent processing test was interrupted", exception);
        }
    }

    private void prepareReferenceData() {
        championRepository.saveAll(List.of(
                new Champion(1L, "Aatrox", "아트록스", List.of(ChampionTag.FIGHTER)),
                new Champion(2L, "Ally", "아군", List.of(ChampionTag.FIGHTER)),
                new Champion(3L, "Enemy", "적군", List.of(ChampionTag.TANK))
        ));
        itemRepository.saveAll(List.of(
                new Item(3071L, "아이템 A"),
                new Item(6610L, "아이템 B")
        ));
    }

    private void saveCompleteMatch(String matchId) {
        rawMatchRepository.save(new RawMatch(matchId, """
                {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                  {"puuid":"p-focal","participantId":1,"championId":1,"teamId":100,
                   "teamPosition":"TOP","item0":3071,"item1":6610,"win":true},
                  {"puuid":"p-ally","participantId":2,"championId":2,"teamId":100,
                   "teamPosition":"JUNGLE","item0":3071,"item1":6610,"win":false},
                  {"puuid":"p-enemy","participantId":3,"championId":3,"teamId":200,"win":false}
                ]}}
                """));
        rawMatchTimelineRepository.save(new RawMatchTimeline(matchId, """
                {"metadata":{"participants":["p-focal","p-ally","p-enemy"]},"info":{"frames":[
                  {"events":[
                    {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071},
                    {"timestamp":200,"type":"ITEM_PURCHASED","participantId":1,"itemId":6610},
                    {"timestamp":100,"type":"ITEM_PURCHASED","participantId":2,"itemId":3071},
                    {"timestamp":200,"type":"ITEM_PURCHASED","participantId":2,"itemId":6610}
                  ]}
                ]}}
                """));
    }

    private void replaceFocalBuildAndWin(String matchId) {
        rawMatchRepository.saveAndFlush(new RawMatch(matchId, """
                {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                  {"puuid":"p-focal","participantId":1,"championId":1,"teamId":100,
                   "teamPosition":"TOP","item0":6610,"item1":3071,"win":false},
                  {"puuid":"p-ally","participantId":2,"championId":2,"teamId":100,
                   "teamPosition":"JUNGLE","item0":3071,"item1":6610,"win":false},
                  {"puuid":"p-enemy","participantId":3,"championId":3,"teamId":200,"win":true}
                ]}}
                """));
        rawMatchTimelineRepository.saveAndFlush(new RawMatchTimeline(matchId, """
                {"metadata":{"participants":["p-focal","p-ally","p-enemy"]},"info":{"frames":[
                  {"events":[
                    {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":6610},
                    {"timestamp":200,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071},
                    {"timestamp":100,"type":"ITEM_PURCHASED","participantId":2,"itemId":3071},
                    {"timestamp":200,"type":"ITEM_PURCHASED","participantId":2,"itemId":6610}
                  ]}
                ]}}
                """));
    }

    private void setSampleWinToNull(String matchId, String puuid) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createNativeQuery("""
                        UPDATE composition_stats_samples
                        SET win = NULL
                        WHERE match_id = :matchId
                          AND puuid = :puuid
                        """)
                        .setParameter("matchId", matchId)
                        .setParameter("puuid", puuid)
                        .executeUpdate()
        );
        entityManager.clear();
    }

    private void saveCohort(String matchId, String puuid) {
        cohortRepository.save(new MatchParticipantCohort(
                matchId,
                puuid,
                "RANKED_SOLO_5x5",
                "PLATINUM",
                "I",
                java.time.Instant.parse("2026-08-06T08:00:00Z")
        ));
    }

}
