package dfgg.application.itemstats;

import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.ItemMetaStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.RecentPatchWindow;
import java.util.Collection;
import java.util.List;
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
        long startedAt = System.currentTimeMillis();
        RecentPatchWindow window = recentPatchWindow(recentPatchWindowSize);
        Collection<String> recentPatches = recentPatchParameter(window);

        championItemStatsRepository.deleteAllInBatch();
        championItemStatsRepository.aggregateFrom(recentPatches);

        championItemRollupRepository.deleteAllInBatch();
        championItemRollupRepository.aggregateFrom(recentPatches);

        championPairItemStatsRepository.deleteAllInBatch();
        championPairItemStatsRepository.aggregateFrom(recentPatches);

        itemMetaStatsRepository.deleteAllInBatch();
        itemMetaStatsRepository.aggregateFrom();

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

    private RecentPatchWindow recentPatchWindow(int recentPatchWindowSize) {
        return RecentPatchWindow.of(participantRepository.findDistinctPatches(), recentPatchWindowSize);
    }

    private Collection<String> recentPatchParameter(RecentPatchWindow window) {
        if (window.isEmpty()) {
            return MATCHES_NOTHING;
        }
        return window.patches();
    }
}
