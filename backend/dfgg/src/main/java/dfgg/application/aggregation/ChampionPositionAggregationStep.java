package dfgg.application.aggregation;

import dfgg.application.counter.ChampionPositionAggregationService;
import org.springframework.stereotype.Component;

/** 현재 패치의 챔피언별 라인 분포를 다시 계산한다. */
@Component
public class ChampionPositionAggregationStep implements StatsAggregationStep {
    private final ChampionPositionAggregationService service;

    public ChampionPositionAggregationStep(ChampionPositionAggregationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "champion-positions";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Object aggregate() {
        return service.aggregate();
    }
}
