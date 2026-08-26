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

    /**
     * 이미 산 아이템 개수가 이 값 미만이면(기본 2 → 1,2코어) 실제 구매 순서에 정확히
     * anchoring된 원본 데이터 집계를, 그 이상이면(3코어~) gap 허용 PrefixSpan 마이닝
     * 결과를 사용한다. 1~2코어는 챔피언 정체성과 강하게 묶여 anchoring이 중요하고,
     * 3코어부터는 상황 대응 비중이 커져 PrefixSpan의 느슨한 매칭이 표본을 더 확보해준다
     * (근거: 실 데이터에서 "무한의 대검"이 1코어로는 3명뿐인데 빌드 어딘가엔 1929명에
     * 있어, anchoring 없는 마이닝이 1코어 추천을 완전히 그르쳤던 사례로 확인됨).
     */
    @DefaultValue("2")
    int anchoredPrefixLimit,

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
