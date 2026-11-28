package dfgg.application.recommend.v3.generator.transition;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dfgg.application.recommend.v3.generator.RecentPatchWindowCache;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Build 전이 결과를 메모리에 담아 두고, 없을 때만 원본 리더로 읽는다.
 *
 * <p>데이터 버전(최근 패치 창, Core 아이템 목록)이 바뀌면 세대를 올린다. 키에 세대가 있어서
 * 옛 버전으로 시작한 조회가 늦게 들어와도 새 세대에서는 읽히지 않는다.
 */
public class CachingBuildTransitionReader implements BuildTransitionReader {

    private static final String METRIC_CACHE_NAME = "build_transition";

    private final BuildTransitionReader delegate;
    private final RecentPatchWindowCache recentPatchWindow;
    private final Cache<BuildTransitionKey, List<TransitionRow>> cache;
    private final AtomicReference<Generation> generation = new AtomicReference<>(Generation.initial());

    CachingBuildTransitionReader(
            BuildTransitionReader delegate,
            RecentPatchWindowCache recentPatchWindow,
            Cache<BuildTransitionKey, List<TransitionRow>> cache
    ) {
        this.delegate = delegate;
        this.recentPatchWindow = recentPatchWindow;
        this.cache = cache;
    }

    /** 시간 만료는 두지 않는다. 값은 데이터 버전이 바뀔 때만 틀려지고, 그때는 세대가 바뀐다. */
    public static CachingBuildTransitionReader of(
            BuildTransitionReader delegate,
            RecentPatchWindowCache recentPatchWindow,
            long maximumSize
    ) {
        Cache<BuildTransitionKey, List<TransitionRow>> cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .recordStats()
                .build();
        return new CachingBuildTransitionReader(delegate, recentPatchWindow, cache);
    }

    /** 히트, 미스, 밀려난 수, 크기를 {@code cache="build_transition"}으로, 세대를 게이지로 올린다. */
    public void bindMetrics(MeterRegistry registry) {
        CaffeineCacheMetrics.monitor(registry, cache, METRIC_CACHE_NAME);
        Gauge.builder("build_cache_generation", this, CachingBuildTransitionReader::currentGeneration)
                .description("Build 전이 캐시의 데이터 버전 세대")
                .register(registry);
    }

    @Override
    public List<TransitionRow> exactPrefix(BuildTransitionRequest request) {
        BuildTransitionKey key = BuildTransitionKey.exactPrefix(generationOf(request), request);
        return cache.get(key, ignored -> delegate.exactPrefix(request));
    }

    @Override
    public List<TransitionRow> lastItem(BuildTransitionRequest request) {
        BuildTransitionKey key = BuildTransitionKey.lastItem(generationOf(request), request);
        return cache.get(key, ignored -> delegate.lastItem(request));
    }

    /** 데이터 버전이 바뀐 횟수. 예상보다 자주 오르면 버전 계산이 흔들리고 있다는 뜻이다. */
    public long currentGeneration() {
        return generation.get().number();
    }

    private long generationOf(BuildTransitionRequest request) {
        BuildDataVersion version = new BuildDataVersion(recentPatchWindow.current().patches(), request.coreItemIds());
        Generation current = generation.get();
        if (current.matches(version)) {
            return current.number();
        }
        return advance(version);
    }

    private synchronized long advance(BuildDataVersion version) {
        Generation current = generation.get();
        if (current.matches(version)) {
            return current.number();
        }
        Generation next = current.next(version);
        generation.set(next);
        cache.invalidateAll();
        return next.number();
    }

    private record Generation(BuildDataVersion version, long number) {

        private static Generation initial() {
            return new Generation(null, 0);
        }

        private boolean matches(BuildDataVersion other) {
            return other.equals(version);
        }

        private Generation next(BuildDataVersion nextVersion) {
            return new Generation(nextVersion, number + 1);
        }
    }
}
