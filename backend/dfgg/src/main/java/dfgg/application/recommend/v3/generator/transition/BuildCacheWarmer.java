package dfgg.application.recommend.v3.generator.transition;

import dfgg.application.item.ItemService;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPositionGameCount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 트래픽을 받기 전에 Build 전이 캐시를 채운다. 러너가 끝나야 readiness가 트래픽을 받는다.
 *
 * <p>판 수가 많은 챔피언, 포지션의 코어 0개 키부터 읽고, 읽은 결과에서 표본이 큰 다음 아이템 순으로
 * 한 칸씩 넓혀 간다. 실패해도 기동은 막지 않는다.
 */
public class BuildCacheWarmer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BuildCacheWarmer.class);

    private final BuildTransitionReader transitionReader;
    private final ChampionItemStatsRepository championItemStatsRepository;
    private final ItemService itemService;
    private final WarmupLimit limit;
    private final Clock clock;

    public BuildCacheWarmer(
            BuildTransitionReader transitionReader,
            ChampionItemStatsRepository championItemStatsRepository,
            ItemService itemService,
            WarmupLimit limit,
            Clock clock
    ) {
        this.transitionReader = transitionReader;
        this.championItemStatsRepository = championItemStatsRepository;
        this.itemService = itemService;
        this.limit = limit;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            WarmupResult result = warm();
            log.info("Build 캐시 워밍업 완료: keys={}, elapsedMs={}, stop={}",
                    result.loadedKeys(), result.elapsed().toMillis(), result.stopReason());
        } catch (RuntimeException exception) {
            log.warn("Build 캐시 워밍업 실패, 빈 캐시로 시작한다", exception);
        }
    }

    WarmupResult warm() {
        Instant start = clock.instant();
        Set<Long> coreItemIds = coreItemIds();
        if (coreItemIds.isEmpty()) {
            return new WarmupResult(0, Duration.ZERO, StopReason.NO_CORE_ITEMS);
        }
        PriorityQueue<Candidate> queue = new PriorityQueue<>(Candidate.HIGHER_SUPPORT_FIRST);
        seed(queue, coreItemIds);

        ExecutorService executor = Executors.newFixedThreadPool(limit.parallelism());
        try {
            return expand(queue, executor, start);
        } finally {
            executor.shutdownNow();
        }
    }

    private Set<Long> coreItemIds() {
        return itemService.findCoreItemIds().stream()
                .map(Long::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void seed(PriorityQueue<Candidate> queue, Set<Long> coreItemIds) {
        for (ChampionPositionGameCount count : championItemStatsRepository.findGameCountsByChampionPosition()) {
            BuildTransitionRequest request = new BuildTransitionRequest(
                    count.championId(), count.position(), List.of(), coreItemIds);
            queue.add(new Candidate(request, count.gameCount()));
        }
    }

    private WarmupResult expand(PriorityQueue<Candidate> queue, ExecutorService executor, Instant start) {
        Instant deadline = start.plus(limit.timeout());
        int loadedKeys = 0;
        while (!queue.isEmpty()) {
            if (loadedKeys >= limit.maxKeys()) {
                return new WarmupResult(loadedKeys, elapsedSince(start), StopReason.MAX_KEYS);
            }
            Duration remaining = Duration.between(clock.instant(), deadline);
            if (remaining.isNegative() || remaining.isZero()) {
                return new WarmupResult(loadedKeys, elapsedSince(start), StopReason.TIMEOUT);
            }
            List<Callable<Expansion>> batch = nextBatch(queue, limit.maxKeys() - loadedKeys);
            for (Expansion expansion : runBatch(executor, batch, remaining)) {
                loadedKeys += expansion.loadedKeys();
                queue.addAll(expansion.children());
            }
        }
        return new WarmupResult(loadedKeys, elapsedSince(start), StopReason.EXHAUSTED);
    }

    private List<Callable<Expansion>> nextBatch(PriorityQueue<Candidate> queue, int remainingKeys) {
        int size = Math.min(limit.parallelism(), remainingKeys);
        List<Callable<Expansion>> batch = new ArrayList<>();
        while (batch.size() < size && !queue.isEmpty()) {
            Candidate candidate = queue.poll();
            batch.add(() -> load(candidate.request()));
        }
        return batch;
    }

    private List<Expansion> runBatch(ExecutorService executor, List<Callable<Expansion>> batch, Duration remaining) {
        List<Expansion> expansions = new ArrayList<>();
        try {
            for (Future<Expansion> future : executor.invokeAll(batch, remaining.toMillis(), TimeUnit.MILLISECONDS)) {
                addIfLoaded(future, expansions);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return expansions;
    }

    private void addIfLoaded(Future<Expansion> future, List<Expansion> expansions) throws InterruptedException {
        try {
            expansions.add(future.get());
        } catch (CancellationException | ExecutionException exception) {
            log.debug("Build 캐시 워밍업 키 하나를 건너뛴다", exception);
        }
    }

    /** 서빙과 같은 순서로 읽는다: 정확한 prefix가 비면 마지막 아이템으로 물러선다. */
    private Expansion load(BuildTransitionRequest request) {
        List<TransitionRow> rows = transitionReader.exactPrefix(request);
        if (rows.isEmpty() && request.hasPurchasedItem()) {
            transitionReader.lastItem(request);
            return new Expansion(2, List.of());
        }
        return new Expansion(1, childrenOf(request, rows));
    }

    private List<Candidate> childrenOf(BuildTransitionRequest request, List<TransitionRow> rows) {
        return rows.stream()
                .map(row -> new Candidate(request.followedBy(row.itemId()), row.supportAll()))
                .filter(candidate -> !candidate.request().isFullBuild())
                .toList();
    }

    private Duration elapsedSince(Instant start) {
        return Duration.between(start, clock.instant());
    }

    public record WarmupLimit(int maxKeys, int parallelism, Duration timeout) {

        public WarmupLimit {
            if (maxKeys < 0 || parallelism < 1) {
                throw new IllegalArgumentException("워밍업 상한은 0 이상, 병렬도는 1 이상이어야 합니다.");
            }
        }
    }

    record WarmupResult(int loadedKeys, Duration elapsed, StopReason stopReason) {
    }

    enum StopReason {
        EXHAUSTED,
        MAX_KEYS,
        TIMEOUT,
        NO_CORE_ITEMS
    }

    private record Candidate(BuildTransitionRequest request, long support) {

        private static final Comparator<Candidate> HIGHER_SUPPORT_FIRST =
                Comparator.comparingLong(Candidate::support).reversed()
                        .thenComparingLong(candidate -> candidate.request().championId())
                        .thenComparing(candidate -> candidate.request().position())
                        .thenComparing(candidate -> candidate.request().purchasedItemIds().toString());
    }

    private record Expansion(int loadedKeys, List<Candidate> children) {
    }
}
