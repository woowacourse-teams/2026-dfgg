package dfgg.infrastructure.config;

import dfgg.domain.match.TierScope;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 추천 v3의 {@code tiers}는 통계 집계·학습 데이터 export·서빙이 모두 같은 값을 봐야 한다.
 * 각자 상수를 들면 한쪽만 고쳐 놓고 지표가 왜 맞지 않는지 찾게 된다.
 * 정책이 바뀌면 {@code RECOMMENDATION_V3_TIERS}만 바꾸고 재집계·재학습한다.
 * <p>
 * 검증은 생성자에서 한다 — 잘못된 티어는 기동을 막아야지, 첫 요청 때 드러나면 늦다.
 */
@ConfigurationProperties("recommendation.v3")
public record V3RecommendationProperties(

        @DefaultValue({"EMERALD", "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER"})
        List<String> tiers
) {

    private static final String TIERS_PROPERTY = "recommendation.v3.tiers";

    public V3RecommendationProperties {
        tiers = TierScope.of(tiers, TIERS_PROPERTY).values();
    }

    /** 집계·export·서빙이 받아 갈 티어 범위. */
    public TierScope scope() {
        return new TierScope(tiers);
    }
}
