package dfgg.application.itemstats;

import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.ItemMetaStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.RecentPatchWindow;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4개 generator가 읽을 통계 테이블을 원본 참가자 데이터에서 다시 만든다.
 *
 * <p>전량 재계산 방식이라 멱등하다 — 부분 갱신은 이전 실행이 중간에 죽었을 때 남은 행을
 * 추적해야 하는데, 통계가 조금씩 어긋나면 추천 결과를 보고 역추적하기가 매우 어렵다.
 */
@Service
public class ItemStatsAggregationService {

    private static final Logger log = LoggerFactory.getLogger(ItemStatsAggregationService.class);

    /** 관측된 패치가 없을 때 {@code IN ()} 문법 오류를 피하려고 넣는, 아무것도 매칭하지 않는 값. */
    private static final List<String> MATCHES_NOTHING = List.of("");

    private final NormalizedMatchParticipantRepository participantRepository;
    private final ChampionItemStatsRepository championItemStatsRepository;
    private final ChampionItemRollupRepository championItemRollupRepository;
    private final ChampionPairItemStatsRepository championPairItemStatsRepository;
    private final ItemMetaStatsRepository itemMetaStatsRepository;

    public ItemStatsAggregationService(
            NormalizedMatchParticipantRepository participantRepository,
            ChampionItemStatsRepository championItemStatsRepository,
            ChampionItemRollupRepository championItemRollupRepository,
            ChampionPairItemStatsRepository championPairItemStatsRepository,
            ItemMetaStatsRepository itemMetaStatsRepository
    ) {
        this.participantRepository = participantRepository;
        this.championItemStatsRepository = championItemStatsRepository;
        this.championItemRollupRepository = championItemRollupRepository;
        this.championPairItemStatsRepository = championPairItemStatsRepository;
        this.itemMetaStatsRepository = itemMetaStatsRepository;
    }

    @Transactional
    public ItemStatsAggregationResult aggregate(int recentPatchWindowSize) {
        return aggregate(recentPatchWindowSize, Set.of());
    }

    /**
     * {@code excludedPatches}의 경기를 통계에서 제외하고 집계한다.
     *
     * <p>patch split 평가용이다. test 패치의 경기가 통계에 남아 있으면 모델이 "아직 오지 않은
     * 패치"를 이미 본 셈이 되어 지표가 낙관적으로 나온다. 서빙 경로는 빈 집합으로 호출하므로
     * 동작이 달라지지 않는다.
     */
    @Transactional
    public ItemStatsAggregationResult aggregate(int recentPatchWindowSize, Collection<String> excludedPatches) {
        long startedAt = System.currentTimeMillis();
        // 최근 윈도도 제외 후 남은 패치에서 고른다. 제외한 패치가 '최근'으로 뽑히면 누수가 그대로다.
        RecentPatchWindow window = recentPatchWindow(recentPatchWindowSize, excludedPatches);
        Collection<String> recentPatches = recentPatchParameter(window);
        Collection<String> excluded = excludedPatchParameter(excludedPatches);

        championItemStatsRepository.deleteAllInBatch();
        championItemStatsRepository.aggregateFrom(recentPatches, excluded);

        championItemRollupRepository.deleteAllInBatch();
        championItemRollupRepository.aggregateFrom(recentPatches, excluded);

        championPairItemStatsRepository.deleteAllInBatch();
        championPairItemStatsRepository.aggregateFrom(recentPatches, excluded);

        itemMetaStatsRepository.deleteAllInBatch();
        itemMetaStatsRepository.aggregateFrom(excluded);

        ItemStatsAggregationResult result = new ItemStatsAggregationResult(
                window.patches(),
                championItemStatsRepository.count(),
                championItemRollupRepository.count(),
                championPairItemStatsRepository.count(),
                itemMetaStatsRepository.count(),
                System.currentTimeMillis() - startedAt
        );
        log.info(
                "Item stats aggregation completed: recentPatches={}, championItemStats={}, rollup={}, pair={}, meta={}, durationMs={}",
                result.recentPatches(), result.championItemStatsCount(), result.championItemRollupCount(),
                result.championPairItemStatsCount(), result.itemMetaStatsCount(), result.durationMillis()
        );
        return result;
    }

    private RecentPatchWindow recentPatchWindow(int recentPatchWindowSize, Collection<String> excludedPatches) {
        List<String> patches = participantRepository.findDistinctPatches().stream()
                .filter(patch -> !excludedPatches.contains(patch))
                .toList();
        return RecentPatchWindow.of(patches, recentPatchWindowSize);
    }

    /** 빈 컬렉션은 {@code NOT IN ()}이 되어 SQL 문법 오류가 난다. 어떤 patch와도 같지 않은 값을 넣는다. */
    private Collection<String> excludedPatchParameter(Collection<String> excludedPatches) {
        if (excludedPatches.isEmpty()) {
            return MATCHES_NOTHING;
        }
        return excludedPatches;
    }

    private Collection<String> recentPatchParameter(RecentPatchWindow window) {
        if (window.isEmpty()) {
            return MATCHES_NOTHING;
        }
        return window.patches();
    }
}
