package dfgg.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 대회 픽 수집 스케줄링을 켠다. 매치 수집({@code collection.scheduler.enabled})과 따로 켜고 끈다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "pro-picks.scheduler", name = "enabled", havingValue = "true")
public class ProPickSchedulingConfiguration {
}
