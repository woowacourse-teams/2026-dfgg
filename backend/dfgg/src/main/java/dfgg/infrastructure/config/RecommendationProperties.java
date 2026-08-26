package dfgg.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 추천 서빙 시점에 어떤 학습 버전(algorithmVersion)의 임베딩·마이닝 결과를 읽을지, 그리고
 * 80/20 비율·재정렬 가중치(w1~w4)를 설정한다. 체크포인트를 재학습하거나 백테스트로 파라미터를
 * 조정할 때마다 코드 배포 없이 값만 바꿀 수 있도록 상수로 박아두지 않고 설정으로 뺀다.
 */
@ConfigurationProperties("recommendation")
public record RecommendationProperties (
    @DefaultValue("checkpoint-a-4")
    String identityAlgorithmVersion,

    @DefaultValue("checkpoint-c-1-counter")
    String counterAlgorithmVersion,

    @DefaultValue("checkpoint-d-1")
    String patternAlgorithmVersion,

    @DefaultValue("10")
    int totalCandidateCount,

    @DefaultValue("0.8")
    double safeZoneRatio,

    @DefaultValue("1.0")
    double wilsonWeight,

    @DefaultValue("1.0")
    double myChampionWeight,

    @DefaultValue("1.0")
    double allyWeight,

    @DefaultValue("1.0")
    double enemyWeight
) {
}
