package dfgg.infrastructure.config;

import dfgg.domain.match.TierScope;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 집계·학습 데이터 export·서빙이 같은 인스턴스를 받게 하려는 것이다.
 * 각자 설정을 읽으면 한쪽만 다른 값을 보게 되고, 그 차이는 지표로 드러나지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(V3RecommendationProperties.class)
public class TierScopeConfiguration {

    @Bean
    TierScope v3TierScope(V3RecommendationProperties properties) {
        return properties.scope();
    }
}
