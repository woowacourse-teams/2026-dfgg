package dfgg.domain.rune;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 한 챔피언·포지션이 한 패치에서 쓴 룬 페이지 기록. 판 하나가 페이지 하나다.
 *
 * <p>추천은 (핵심 룬, 보조 계열) 묶음 단위로 순위를 매기고, 고른 묶음마다 가장 많이 쓴 페이지 전체를 낸다.
 * 서로 다른 묶음이 모자라면 상위 묶음의 다음으로 많이 쓴 페이지로 채운다.
 */
public final class RunePageSamples {

    private final List<RunePage> pages;

    public RunePageSamples(List<RunePage> pages) {
        this.pages = List.copyOf(pages);
    }

    public RuneRecommendation recommend(RuneRecommendationPolicy policy) {
        int games = pages.size();
        if (games < policy.minimumGames()) {
            return new RuneRecommendation(games, List.of());
        }
        List<PathSamples> paths = rankedPaths(policy.minimumPathGames());
        List<RunePage> chosen = choosePages(paths, policy.pageCount());
        List<RecommendedRunePage> recommended = IntStream.range(0, chosen.size())
                .mapToObj(index -> recommend(index + 1, chosen.get(index), paths, games))
                .toList();
        return new RuneRecommendation(games, recommended);
    }

    /** 기준 판 수를 넘는 묶음을 판 수 순으로 정렬한다. 판 수가 같으면 id 순으로 고정한다. */
    private List<PathSamples> rankedPaths(int minimumPathGames) {
        Map<RunePath, Map<RunePage, Long>> pageCountsByPath = pages.stream()
                .collect(groupingBy(RunePage::path, groupingBy(Function.identity(), counting())));
        return pageCountsByPath.entrySet().stream()
                .map(entry -> new PathSamples(entry.getKey(), entry.getValue()))
                .filter(path -> path.games() >= minimumPathGames)
                .sorted(Comparator.comparingLong(PathSamples::games).reversed()
                        .thenComparing(PathSamples::path, RunePath.BY_IDS))
                .toList();
    }

    /** 묶음마다 대표 페이지를 먼저 고르고, 모자라면 상위 묶음의 다음 페이지로 채운다. */
    private List<RunePage> choosePages(List<PathSamples> paths, int pageCount) {
        SequencedSet<RunePage> chosen = new LinkedHashSet<>();
        Stream<RunePage> representatives = paths.stream().map(PathSamples::mostUsedPage);
        Stream<RunePage> variants = paths.stream().flatMap(path -> path.rankedPages().stream());
        Stream.concat(representatives, variants)
                .takeWhile(page -> chosen.size() < pageCount)
                .forEach(chosen::add);
        return List.copyOf(chosen);
    }

    private RecommendedRunePage recommend(int rank, RunePage page, List<PathSamples> paths, int games) {
        long pathGames = paths.stream()
                .filter(path -> path.path().equals(page.path()))
                .findFirst()
                .orElseThrow()
                .games();
        return new RecommendedRunePage(rank, page, Math.toIntExact(pathGames), (double) pathGames / games);
    }

    /** 한 묶음에 속한 페이지별 판 수 */
    private record PathSamples(RunePath path, Map<RunePage, Long> pageCounts) {

        private static final Comparator<RunePage> PAGE_BY_IDS = Comparator.comparing(PathSamples::idsOf,
                PathSamples::compareIds);

        long games() {
            return pageCounts.values().stream().mapToLong(Long::longValue).sum();
        }

        RunePage mostUsedPage() {
            return rankedPages().getFirst();
        }

        List<RunePage> rankedPages() {
            return pageCounts.entrySet().stream()
                    .sorted(Map.Entry.<RunePage, Long>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey(PAGE_BY_IDS)))
                    .map(Map.Entry::getKey)
                    .toList();
        }

        private static List<Integer> idsOf(RunePage page) {
            return Stream.of(
                    Stream.of(page.primaryStyleId()), page.primaryPerkIds().stream(),
                    Stream.of(page.subStyleId()), page.subPerkIds().stream(),
                    Stream.of(page.statShards().offenseId(), page.statShards().flexId(),
                            page.statShards().defenseId())
            ).flatMap(Function.identity()).toList();
        }

        private static int compareIds(List<Integer> left, List<Integer> right) {
            for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
                int compared = Integer.compare(left.get(index), right.get(index));
                if (compared != 0) {
                    return compared;
                }
            }
            return Integer.compare(left.size(), right.size());
        }
    }
}
