package dfgg.application.recommend.v3.generator;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ChampionItemRollup;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStats;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * lift의 분모({@link ChampionBaseline})를 읽는다. generator와 feature 추출기가 이 한 곳을 거친다.
 * <p>
 * 요청한 포지션의 통계가 한 판도 없으면(off-role) 챔피언 전체(rollup)로 물러선다.
 * 분모가 0이면 lift 계산기가 모든 아이템에 1.0을 줘 후보가 아이템 ID 순서로 뭉개지고, 백오프 단계는 여전히 "조합 근거 있음"으로 남는다.
 * 몇 판이라도 있으면 포지션 통계를 그대로 쓰므로 정상 요청의 값은 바뀌지 않는다.
 * <p>
 * 분모를 따로 구하면 후보의 점수와 그 후보의 feature가 서로 다른 축에 선다 —
 * 실제로 Counter generator만 rollup으로 물러서고 feature는 결측으로 남던 상태가 있었다.
 */
@Component
public class ChampionBaselineReader {

    private final ChampionItemStatsRepository championItemStatsRepository;
    private final ChampionItemRollupRepository championItemRollupRepository;

    public ChampionBaselineReader(
            ChampionItemStatsRepository championItemStatsRepository,
            ChampionItemRollupRepository championItemRollupRepository
    ) {
        this.championItemStatsRepository = championItemStatsRepository;
        this.championItemRollupRepository = championItemRollupRepository;
    }

    public ChampionBaseline read(long championId, ChampionPosition position) {
        int id = Math.toIntExact(championId);
        List<ChampionItemStats> positionStats =
                championItemStatsRepository.findByChampionIdAndPosition(id, position);
        if (!positionStats.isEmpty()) {
            return fromPosition(positionStats);
        }
        return fromRollup(championItemRollupRepository.findByChampionId(id));
    }

    private ChampionBaseline fromPosition(List<ChampionItemStats> positionStats) {
        Map<Long, Integer> purchaseAll = new HashMap<>();
        Map<Long, Integer> purchaseRecent = new HashMap<>();
        int gameAll = 0;
        int gameRecent = 0;
        for (ChampionItemStats stats : positionStats) {
            purchaseAll.put(stats.getItemId(), stats.getPurchaseCountAll());
            purchaseRecent.put(stats.getItemId(), stats.getPurchaseCountRecent());
            gameAll = Math.max(gameAll, stats.getChampionGameCountAll());
            gameRecent = Math.max(gameRecent, stats.getChampionGameCountRecent());
        }
        return new ChampionBaseline(purchaseAll, purchaseRecent, gameAll, gameRecent);
    }

    private ChampionBaseline fromRollup(List<ChampionItemRollup> rollup) {
        Map<Long, Integer> purchaseAll = new HashMap<>();
        Map<Long, Integer> purchaseRecent = new HashMap<>();
        int gameAll = 0;
        int gameRecent = 0;
        for (ChampionItemRollup stats : rollup) {
            purchaseAll.put(stats.getItemId(), stats.getPurchaseCountAll());
            purchaseRecent.put(stats.getItemId(), stats.getPurchaseCountRecent());
            gameAll = Math.max(gameAll, stats.getChampionGameCountAll());
            gameRecent = Math.max(gameRecent, stats.getChampionGameCountRecent());
        }
        return new ChampionBaseline(purchaseAll, purchaseRecent, gameAll, gameRecent);
    }
}
