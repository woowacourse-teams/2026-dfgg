package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.application.recommend.v3.generator.RecentPatchWindowCache;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.RecentPatchWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecentPatchWindowCacheTest {

    private static final int WINDOW_SIZE = 3;

    private final NormalizedMatchParticipantRepository participantRepository =
            mock(NormalizedMatchParticipantRepository.class);

    @Test
    @DisplayName("여러 번 꺼내도 최근 패치 목록은 DB에서 한 번만 계산한다")
    void current_WhenCalledRepeatedly_LoadPatchesOnce() {
        // given
        when(participantRepository.findDistinctPatches()).thenReturn(List.of("16.16", "16.17", "16.18"));
        RecentPatchWindowCache cache = new RecentPatchWindowCache(participantRepository, WINDOW_SIZE);

        // when
        cache.current();
        cache.current();
        cache.current();

        // then
        verify(participantRepository, times(1)).findDistinctPatches();
    }

    @Test
    @DisplayName("관측된 패치 중 최신 N개만 창에 남긴다")
    void current_WhenMorePatchesThanWindow_KeepNewestOnly() {
        // given: 문자열로는 16.9가 16.18보다 크지만 버전으로는 오래됐다
        when(participantRepository.findDistinctPatches()).thenReturn(List.of("16.9", "16.18", "16.17", "16.16", "16.15"));
        RecentPatchWindowCache cache = new RecentPatchWindowCache(participantRepository, WINDOW_SIZE);

        // when
        RecentPatchWindow window = cache.current();

        // then
        assertThat(window.patches()).containsExactlyInAnyOrder("16.18", "16.17", "16.16");
    }

    @Test
    @DisplayName("아직 패치가 없으면 빈 창을 담아 두지 않고 다음에 다시 계산한다")
    void current_WhenNoPatchesYet_LoadAgainNextTime() {
        // given: 빈 DB로 기동한 뒤 데이터가 들어온 상황
        when(participantRepository.findDistinctPatches())
                .thenReturn(List.of())
                .thenReturn(List.of("16.18"));
        RecentPatchWindowCache cache = new RecentPatchWindowCache(participantRepository, WINDOW_SIZE);

        // when
        RecentPatchWindow beforeLoad = cache.current();
        RecentPatchWindow afterLoad = cache.current();

        // then
        assertThat(beforeLoad.isEmpty()).isTrue();
        assertThat(afterLoad.patches()).containsExactly("16.18");
    }

    @Test
    @DisplayName("첫 요청이 동시에 몰려도 최근 패치 목록은 한 번만 계산한다")
    void current_WhenCalledConcurrently_LoadPatchesOnce() throws Exception {
        // given: 첫 계산이 끝나지 않은 동안 다른 요청들이 들어온다
        CountDownLatch slowQuery = new CountDownLatch(1);
        when(participantRepository.findDistinctPatches()).thenAnswer(invocation -> {
            slowQuery.await(2, TimeUnit.SECONDS);
            return List.of("16.18");
        });
        RecentPatchWindowCache cache = new RecentPatchWindowCache(participantRepository, WINDOW_SIZE);
        ExecutorService requests = Executors.newFixedThreadPool(8);

        // when
        List<Future<RecentPatchWindow>> results = new ArrayList<>();
        for (int request = 0; request < 8; request++) {
            results.add(requests.submit(cache::current));
        }
        Thread.sleep(200);
        slowQuery.countDown();

        // then
        for (Future<RecentPatchWindow> result : results) {
            assertThat(result.get(3, TimeUnit.SECONDS).patches()).containsExactly("16.18");
        }
        verify(participantRepository, times(1)).findDistinctPatches();
        requests.shutdown();
    }
}
