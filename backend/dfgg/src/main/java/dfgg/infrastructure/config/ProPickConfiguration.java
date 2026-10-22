package dfgg.infrastructure.config;

import dfgg.domain.propick.ProLeagues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProPickConfiguration {

    /** 접두사가 잘못되면 기동할 때 실패한다. */
    @Bean
    ProLeagues proLeagues(ProPickProperties properties) {
        return new ProLeagues(properties.leagues());
    }
}
