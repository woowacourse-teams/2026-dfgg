package dfgg.application.recommend.v3.generator.transition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.github.benmanes.caffeine.cache.Caffeine;
import dfgg.application.recommend.v3.generator.RecentPatchWindowCache;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.RecentPatchWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CachingBuildTransitionReaderTest {

    private static final Set<Long> CORE_ITEM_IDS = Set.of(3031L, 3036L, 6673L);
    private static final List<TransitionRow> ROWS = List.of(new TransitionRow(3031L, 5, 3, 2, 1));

    private CountingReader delegate;
    private RecentPatchWindowCache recentPatchWindow;
    private CachingBuildTransitionReader reader;

    @BeforeEach
    void setUp() {
        delegate = new CountingReader(ROWS);
        recentPatchWindow = mock(RecentPatchWindowCache.class);
        given(recentPatchWindow.current()).willReturn(new RecentPatchWindow(Set.of("16.17")));
        reader = CachingBuildTransitionReader.of(delegate, recentPatchWindow, 100);
    }

    private BuildTransitionRequest request(List<Long> purchasedItemIds) {
        return new BuildTransitionRequest(157L, ChampionPosition.MID, purchasedItemIds, CORE_ITEM_IDS);
    }

    @Test
    @DisplayName("같은 구매 순서를 두 번 읽으면 원본은 한 번만 읽는다")
    void exactPrefix_WhenSameKeyReadTwice_ReadsDelegateOnce() {
        // when
        List<TransitionRow> first = reader.exactPrefix(request(List.of(6673L)));
        List<TransitionRow> second = reader.exactPrefix(request(List.of(6673L)));

        // then
        assertThat(first).isEqualTo(ROWS);
        assertThat(second).isEqualTo(ROWS);
        assertThat(delegate.exactPrefixCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 결과도 담아 두어 다시 읽지 않는다")
    void exactPrefix_WhenResultIsEmpty_CachesEmptyResult() {
        // given
        CountingReader emptyDelegate = new CountingReader(List.of());
        CachingBuildTransitionReader emptyReader = CachingBuildTransitionReader.of(
                emptyDelegate, recentPatchWindow, 100);

        // when
        emptyReader.exactPrefix(request(List.of(6673L)));
        List<TransitionRow> second = emptyReader.exactPrefix(request(List.of(6673L)));

        // then
        assertThat(second).isEmpty();
        assertThat(emptyDelegate.exactPrefixCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("구매 순서가 다르면 따로 읽는다")
    void exactPrefix_WhenPrefixDiffers_ReadsEachKey() {
        // when
        reader.exactPrefix(request(List.of(6673L)));
        reader.exactPrefix(request(List.of(6673L, 3031L)));

        // then
        assertThat(delegate.exactPrefixCalls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("정확한 prefix와 마지막 아이템 조회는 같은 아이템이어도 다른 키다")
    void lastItem_WhenSameItemsAsExactPrefix_UsesSeparateKey() {
        // when
        reader.exactPrefix(request(List.of(6673L)));
        reader.lastItem(request(List.of(6673L)));
        reader.lastItem(request(List.of(3036L, 6673L)));

        // then: 마지막 아이템이 같으면 앞쪽 구매가 달라도 같은 키다
        assertThat(delegate.exactPrefixCalls.get()).isEqualTo(1);
        assertThat(delegate.lastItemCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Core 아이템 목록이 바뀌면 세대를 올리고 다시 읽는다")
    void exactPrefix_WhenCoreItemsChange_ReadsAgainInNewGeneration() {
        // given
        reader.exactPrefix(request(List.of(6673L)));
        BuildTransitionRequest changedCatalog = new BuildTransitionRequest(
                157L, ChampionPosition.MID, List.of(6673L), Set.of(3031L, 6673L));

        // when
        reader.exactPrefix(changedCatalog);

        // then
        assertThat(delegate.exactPrefixCalls.get()).isEqualTo(2);
        assertThat(reader.currentGeneration()).isEqualTo(2);
    }

    @Test
    @DisplayName("최근 패치 창이 바뀌면 옛 세대 값을 돌려주지 않는다")
    void exactPrefix_WhenPatchWindowChanges_DoesNotReturnOldGeneration() {
        // given
        reader.exactPrefix(request(List.of(6673L)));
        given(recentPatchWindow.current()).willReturn(new RecentPatchWindow(Set.of("16.18")));

        // when
        reader.exactPrefix(request(List.of(6673L)));
        reader.exactPrefix(request(List.of(6673L)));

        // then
        assertThat(delegate.exactPrefixCalls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("버전이 그대로면 세대가 오르지 않는다")
    void currentGeneration_WhenVersionUnchanged_StaysSame() {
        // when
        reader.exactPrefix(request(List.of(6673L)));
        reader.lastItem(request(List.of(3031L)));

        // then
        assertThat(reader.currentGeneration()).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 요청이 같은 키를 동시에 읽어도 원본은 한 번만 읽는다")
    void exactPrefix_WhenConcurrentMissOnSameKey_ReadsDelegateOnce() throws Exception {
        // given
        CountDownLatch release = new CountDownLatch(1);
        CountingReader slowDelegate = new CountingReader(ROWS, release);
        CachingBuildTransitionReader slowReader = CachingBuildTransitionReader.of(
                slowDelegate, recentPatchWindow, 100);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<List<TransitionRow>>> futures = new ArrayList<>();

        // when
        for (int i = 0; i < 8; i++) {
            futures.add(executor.submit(() -> slowReader.exactPrefix(request(List.of(6673L)))));
        }
        release.countDown();
        for (Future<List<TransitionRow>> future : futures) {
            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(ROWS);
        }
        executor.shutdown();

        // then
        assertThat(slowDelegate.exactPrefixCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("용량을 넘으면 오래 안 쓴 키가 밀려나 다시 읽는다")
    void exactPrefix_WhenOverMaximumSize_EvictsAndReadsAgain() {
        // given
        CachingBuildTransitionReader tinyReader = new CachingBuildTransitionReader(
                delegate, recentPatchWindow, Caffeine.newBuilder().maximumSize(1).executor(Runnable::run).build());

        // when
        tinyReader.exactPrefix(request(List.of(6673L)));
        tinyReader.exactPrefix(request(List.of(3031L)));
        tinyReader.exactPrefix(request(List.of(6673L)));

        // then
        assertThat(delegate.exactPrefixCalls.get()).isEqualTo(3);
    }

    private static class CountingReader implements BuildTransitionReader {

        private final List<TransitionRow> rows;
        private final CountDownLatch release;
        private final AtomicInteger exactPrefixCalls = new AtomicInteger();
        private final AtomicInteger lastItemCalls = new AtomicInteger();

        private CountingReader(List<TransitionRow> rows) {
            this(rows, new CountDownLatch(0));
        }

        private CountingReader(List<TransitionRow> rows, CountDownLatch release) {
            this.rows = rows;
            this.release = release;
        }

        @Override
        public List<TransitionRow> exactPrefix(BuildTransitionRequest request) {
            exactPrefixCalls.incrementAndGet();
            await();
            return rows;
        }

        @Override
        public List<TransitionRow> lastItem(BuildTransitionRequest request) {
            lastItemCalls.incrementAndGet();
            return rows;
        }

        private void await() {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
