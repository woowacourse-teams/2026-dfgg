package dfgg.application.recommend.v3.generator.transition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dfgg.application.item.ItemService;
import dfgg.application.recommend.v3.generator.transition.BuildCacheWarmer.StopReason;
import dfgg.application.recommend.v3.generator.transition.BuildCacheWarmer.WarmupLimit;
import dfgg.application.recommend.v3.generator.transition.BuildCacheWarmer.WarmupResult;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPositionGameCount;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuildCacheWarmerTest {

    private static final long JINX = 222L;
    private static final long LULU = 117L;
    private static final long KRAKEN = 6673L;
    private static final long INFINITY_EDGE = 3031L;
    private static final long SHIELDBOW = 3036L;

    private ChampionItemStatsRepository championItemStatsRepository;
    private ItemService itemService;

    @BeforeEach
    void setUp() {
        championItemStatsRepository = mock(ChampionItemStatsRepository.class);
        itemService = mock(ItemService.class);
        given(itemService.findCoreItemIds()).willReturn(Set.of(6673, 3031, 3036));
        given(championItemStatsRepository.findGameCountsByChampionPosition()).willReturn(List.of(
                new ChampionPositionGameCount((int) JINX, ChampionPosition.BOTTOM, 100),
                new ChampionPositionGameCount((int) LULU, ChampionPosition.SUPPORT, 10)
        ));
    }

    private BuildCacheWarmer warmer(BuildTransitionReader reader, int maxKeys) {
        return new BuildCacheWarmer(reader, championItemStatsRepository, itemService,
                new WarmupLimit(maxKeys, 1, Duration.ofSeconds(10)), Clock.systemUTC());
    }

    @Test
    @DisplayName("표본이 큰 구매 순서부터 채운다")
    void warm_WhenSeveralCandidates_LoadsHigherSupportFirst() {
        // given: 징크스 코어 0개(100판) → 징크스 크라켄(80판) → 룰루 코어 0개(10판) → 룰루 무한의대검(9판)
        //        징크스 실드보우(5판)는 상한에 걸려 읽지 않는다
        RecordingReader reader = new RecordingReader(Map.of(
                List.of(JINX), List.of(row(KRAKEN, 80), row(SHIELDBOW, 5)),
                List.of(LULU), List.of(row(INFINITY_EDGE, 9))
        ));

        // when
        warmer(reader, 5).warm();

        // then
        assertThat(reader.exactPrefixLoads).containsExactly(
                List.of(JINX),
                List.of(JINX, KRAKEN),
                List.of(LULU),
                List.of(LULU, INFINITY_EDGE)
        );
    }

    @Test
    @DisplayName("상한에 닿으면 멈춘다")
    void warm_WhenMaxKeysReached_StopsWithMaxKeys() {
        // given
        RecordingReader reader = new RecordingReader(Map.of(List.of(JINX), List.of(row(KRAKEN, 80))));

        // when
        WarmupResult result = warmer(reader, 1).warm();

        // then
        assertThat(result.loadedKeys()).isEqualTo(1);
        assertThat(result.stopReason()).isEqualTo(StopReason.MAX_KEYS);
    }

    @Test
    @DisplayName("정확한 prefix가 비면 서빙처럼 마지막 아이템 조회도 채운다")
    void warm_WhenExactPrefixIsEmpty_LoadsLastItemToo() {
        // given: 징크스 크라켄 다음은 표본이 없다
        RecordingReader reader = new RecordingReader(Map.of(List.of(JINX), List.of(row(KRAKEN, 80))));

        // when
        WarmupResult result = warmer(reader, 10).warm();

        // then
        assertThat(reader.lastItemLoads).containsExactly(List.of(JINX, KRAKEN));
        assertThat(result.stopReason()).isEqualTo(StopReason.EXHAUSTED);
    }

    @Test
    @DisplayName("풀템 단계는 채우지 않는다")
    void warm_WhenNextStepIsFullBuild_DoesNotExpand() {
        // given: 룰루(서포터)만 두고, 어느 단계에서든 다음 아이템이 하나 있다고 본다
        given(championItemStatsRepository.findGameCountsByChampionPosition()).willReturn(List.of(
                new ChampionPositionGameCount((int) LULU, ChampionPosition.SUPPORT, 10)));
        AlwaysOneMoreReader reader = new AlwaysOneMoreReader();

        // when
        warmer(reader, 100).warm();

        // then: 서포터는 6개가 풀템이라 코어 0~5개 단계만 읽는다
        assertThat(reader.loadedPrefixSizes).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("시간 상한이 지나면 멈춘다")
    void warm_WhenTimeoutPassed_StopsWithTimeout() {
        // given
        BuildCacheWarmer warmer = new BuildCacheWarmer(new RecordingReader(Map.of()), championItemStatsRepository,
                itemService, new WarmupLimit(10, 1, Duration.ZERO), Clock.systemUTC());

        // when
        WarmupResult result = warmer.warm();

        // then
        assertThat(result.loadedKeys()).isZero();
        assertThat(result.stopReason()).isEqualTo(StopReason.TIMEOUT);
    }

    @Test
    @DisplayName("원본 조회가 실패해도 기동을 막지 않는다")
    void run_WhenReaderFails_DoesNotThrow() {
        // given
        given(championItemStatsRepository.findGameCountsByChampionPosition())
                .willThrow(new IllegalStateException("DB 연결 실패"));
        BuildCacheWarmer warmer = warmer(new RecordingReader(Map.of()), 10);

        // when & then
        assertThatCode(() -> warmer.run(null)).doesNotThrowAnyException();
    }

    private static TransitionRow row(long itemId, int support) {
        return new TransitionRow(itemId, support, support / 2, support, support / 2);
    }

    /** 키는 [챔피언, 산 아이템...]. 없는 키는 빈 결과다. */
    private static class RecordingReader implements BuildTransitionReader {

        private final Map<List<Long>, List<TransitionRow>> rowsByKey;
        private final List<List<Long>> exactPrefixLoads = Collections.synchronizedList(new ArrayList<>());
        private final List<List<Long>> lastItemLoads = Collections.synchronizedList(new ArrayList<>());

        private RecordingReader(Map<List<Long>, List<TransitionRow>> rowsByKey) {
            this.rowsByKey = rowsByKey;
        }

        @Override
        public List<TransitionRow> exactPrefix(BuildTransitionRequest request) {
            List<Long> key = keyOf(request);
            exactPrefixLoads.add(key);
            return rowsByKey.getOrDefault(key, List.of());
        }

        @Override
        public List<TransitionRow> lastItem(BuildTransitionRequest request) {
            lastItemLoads.add(keyOf(request));
            return List.of();
        }

        private List<Long> keyOf(BuildTransitionRequest request) {
            List<Long> key = new ArrayList<>();
            key.add(request.championId());
            key.addAll(request.purchasedItemIds());
            return key;
        }
    }

    private static class AlwaysOneMoreReader implements BuildTransitionReader {

        private final List<Integer> loadedPrefixSizes = Collections.synchronizedList(new ArrayList<>());

        @Override
        public List<TransitionRow> exactPrefix(BuildTransitionRequest request) {
            loadedPrefixSizes.add(request.purchasedItemIds().size());
            return List.of(row(1000L + request.purchasedItemIds().size(), 5));
        }

        @Override
        public List<TransitionRow> lastItem(BuildTransitionRequest request) {
            return List.of();
        }
    }
}
