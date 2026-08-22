package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.item.ItemService;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedParticipant;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CompositionStatsSampleRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ChampionBuildStatsAggregationService.class, ItemService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChampionBuildStatsAggregationServiceIntegrationTest {

    @Autowired
    private ChampionBuildStatsAggregationService aggregationService;

    @Autowired
    private ChampionBuildStatsRepository statsRepository;

    @Autowired
    private CompositionStatsSampleRepository sampleRepository;

    @Autowired
    private ChampionRepository championRepository;

    @Autowired
    private ItemRepository itemRepository;

    @MockitoBean
    private DataDragonClient dataDragonClient;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        sampleRepository.deleteAllInBatch();
        statsRepository.deleteAll();
        itemRepository.deleteAllInBatch();
        championRepository.deleteAllInBatch();
    }

    @Test
    void 같은_정규화_매치를_두_번_집계해도_카운트가_중복되지_않는다() {
        prepareReferenceData();
        NormalizedMatch match = match("KR_1", true);

        aggregationService.aggregate(match, "PLATINUM", List.of("p-focal"));
        aggregationService.aggregate(match, "PLATINUM", List.of("p-focal"));

        assertThat(statsRepository.count()).isEqualTo(32);
        assertThat(sampleRepository.count()).isEqualTo(32);
        assertThat(statsRepository.findAll())
                .allSatisfy(stats -> {
                    assertThat(stats.getGameCount()).isEqualTo(1);
                    assertThat(stats.getWinCount()).isEqualTo(1);
                });
    }

    @Test
    void 같은_sample을_동시에_집계해도_카운트는_한_번만_증가한다() throws Exception {
        prepareReferenceData();
        NormalizedMatch match = match("KR_1", true);

        List<Integer> recordedSamples = aggregateConcurrently(match, match);

        assertThat(recordedSamples).containsExactlyInAnyOrder(32, 0);
        assertThat(statsRepository.count()).isEqualTo(32);
        assertThat(sampleRepository.count()).isEqualTo(32);
        assertThat(statsRepository.findAll())
                .allSatisfy(stats -> {
                    assertThat(stats.getGameCount()).isEqualTo(1);
                    assertThat(stats.getWinCount()).isEqualTo(1);
                });
    }

    @Test
    void 서로_다른_sample을_동시에_집계하면_카운트가_정확히_두_번_증가한다() throws Exception {
        prepareReferenceData();
        NormalizedMatch winningMatch = match("KR_WIN", true);
        NormalizedMatch losingMatch = match("KR_LOSE", false);

        List<Integer> recordedSamples = aggregateConcurrently(winningMatch, losingMatch);

        assertThat(recordedSamples).containsExactlyInAnyOrder(32, 32);
        assertThat(statsRepository.count()).isEqualTo(32);
        assertThat(sampleRepository.count()).isEqualTo(64);
        assertThat(statsRepository.findAll())
                .allSatisfy(stats -> {
                    assertThat(stats.getGameCount()).isEqualTo(2);
                    assertThat(stats.getWinCount()).isEqualTo(1);
                });
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

    private NormalizedMatch match(String matchId, boolean win) {
        return new NormalizedMatch(
                matchId,
                "16.15",
                420,
                List.of(
                        participant("p-focal", 1, 1, 100, "TOP", win),
                        participant("p-ally", 2, 2, 100, "JUNGLE", false),
                        participant("p-enemy", 3, 3, 200, "TOP", false)
                )
        );
    }

    private List<Integer> aggregateConcurrently(
            NormalizedMatch firstMatch,
            NormalizedMatch secondMatch
    ) throws Exception {
        CountDownLatch readySignal = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> aggregateAfterSignal(
                    firstMatch,
                    readySignal,
                    startSignal
            ));
            Future<Integer> second = executor.submit(() -> aggregateAfterSignal(
                    secondMatch,
                    readySignal,
                    startSignal
            ));

            assertThat(readySignal.await(5, TimeUnit.SECONDS)).isTrue();
            startSignal.countDown();
            return List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );
        }
    }

    private int aggregateAfterSignal(
            NormalizedMatch match,
            CountDownLatch readySignal,
            CountDownLatch startSignal
    ) {
        readySignal.countDown();
        try {
            startSignal.await();
            return aggregationService.aggregate(match, "PLATINUM", List.of("p-focal"));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent aggregation test was interrupted", exception);
        }
    }

    private NormalizedParticipant participant(
            String puuid,
            int participantId,
            int championId,
            int teamId,
            String position,
            boolean win
    ) {
        return new NormalizedParticipant(
                puuid,
                participantId,
                championId,
                teamId,
                position,
                win,
                List.of(3071, 6610),
                List.of(3071, 6610),
                true
        );
    }
}
