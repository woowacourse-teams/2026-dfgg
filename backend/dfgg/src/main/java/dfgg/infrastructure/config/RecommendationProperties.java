package dfgg.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 추천 서빙 시점에 어떤 학습 버전(algorithmVersion)의 임베딩을 읽을지 설정한다.
 * 체크포인트를 재학습할 때마다 코드 배포 없이 값만 바꿀 수 있도록 상수로 박아두지 않고 설정으로 뺀다.
 */
@ConfigurationProperties("recommendation")
public record RecommendationProperties (
    @DefaultValue("checkpoint-a-4")
    String identityAlgorithmVersion,

    @DefaultValue("checkpoint-c-1-counter")
    String counterAlgorithmVersion
) {
}
