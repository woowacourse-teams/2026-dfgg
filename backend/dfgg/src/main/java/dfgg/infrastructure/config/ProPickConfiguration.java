package dfgg.infrastructure.config;

import dfgg.domain.propick.ProLeagues;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProPickConfiguration {

    /** 접두사가 잘못되면 기동할 때 실패한다. */
    @Bean
    ProLeagues proLeagues(ProPickProperties properties) {
        return new ProLeagues(properties.leagues());
    }

    /** 수집 시작일·선수 갱신 시각의 기준. 테스트에서 고정 시계로 바꾼다. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
