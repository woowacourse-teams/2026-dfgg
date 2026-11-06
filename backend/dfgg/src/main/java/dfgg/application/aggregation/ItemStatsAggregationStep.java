package dfgg.application.aggregation;

import dfgg.application.itemstats.ItemStatsAggregationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 아이템 통계 4종을 원본 참가자 데이터에서 다시 계산한다. 최근 창 크기는 서빙과 같은 설정을 쓴다. */
@Component
public class ItemStatsAggregationStep implements StatsAggregationStep {
    private final ItemStatsAggregationService service;
    private final int recentPatchWindowSize;

    public ItemStatsAggregationStep(ItemStatsAggregationService service,
                                    @Value("${recommendation.recent-patch-window-size}") int recentPatchWindowSize) {
        this.service = service;
        this.recentPatchWindowSize = recentPatchWindowSize;
    }

    @Override
    public String name() {
        return "item-stats";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Object aggregate() {
        return service.aggregate(recentPatchWindowSize);
    }
}
