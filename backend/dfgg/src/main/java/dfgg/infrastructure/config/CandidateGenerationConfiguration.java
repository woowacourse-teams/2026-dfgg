package dfgg.infrastructure.config;

import dfgg.application.recommend.v3.CandidateTopK;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * generator별 후보 수를 빈으로 올린다.
 * <p>
 * 서빙과 학습 데이터 export가 같은 값을 써야 한다.
 * 서로 다르면 학습 시 본 후보 집합과 서빙 시 랭킹하는 후보 집합이 달라지고, 그 차이는 오프라인 지표로 드러나지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class CandidateGenerationConfiguration {

    @Bean
    CandidateTopK candidateTopK(
            @Value("${recommendation.candidate-top-k.build}") int build,
            @Value("${recommendation.candidate-top-k.self-synergy}") int selfSynergy,
            @Value("${recommendation.candidate-top-k.ally-synergy}") int allySynergy,
            @Value("${recommendation.candidate-top-k.counter}") int counter
    ) {
        return new CandidateTopK(build, selfSynergy, allySynergy, counter);
    }
}
