package dfgg.application.recommend.fallback;

import java.util.List;

/**
 * 추천 결과와, 그 결과가 폴백 체인의 어느 단계에서 나왔는지.
 * 어느 단계가 얼마나 자주 쓰이는지는 배포 후 관측 지표가 된다.
 */
public record FallbackRecommendation(
        List<Long> itemIds,
        FallbackStage servedBy
) {

}
