package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dfgg.application.RiotCollectionOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class RiotSchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RiotCollectionOrchestrator.class, () -> mock(RiotCollectionOrchestrator.class))
            .withUserConfiguration(
                    RiotSchedulingConfiguration.class,
                    RiotCollectionScheduler.class
            );

    @Test
    void 기본_cron은_5분_주기다() {
        assertThat(new RiotSchedulerProperties().getCron()).isEqualTo("0 */5 * * * *");
    }

    @Test
    void enabled가_false이면_스케줄러를_등록하지_않는다() {
        contextRunner
                .withPropertyValues("collection.scheduler.enabled=false")
                .run(context -> {
                    assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
                    assertThat(context).doesNotHaveBean(RiotCollectionScheduler.class);
                });
    }

    @Test
    void enabled가_true이면_스케줄러를_등록한다() {
        contextRunner
                .withPropertyValues("collection.scheduler.enabled=true")
                .run(context -> {
                    assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).hasSize(1);
                    assertThat(context).hasSingleBean(RiotCollectionScheduler.class);
                });
    }

    @Test
    void cron_timezone과_수집_범위를_설정으로_bind한다() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(
                        "collection.scheduler.enabled=true",
                        "collection.scheduler.cron=0 30 4 * * *",
                        "collection.scheduler.zone=UTC",
                        "collection.scheduler.tiers=PLATINUM,EMERALD",
                        "collection.scheduler.divisions=I,II",
                        "collection.scheduler.match-count=50"
                )
                .run(context -> {
                    RiotSchedulerProperties properties = context.getBean(RiotSchedulerProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getCron()).isEqualTo("0 30 4 * * *");
                    assertThat(properties.getZone()).isEqualTo("UTC");
                    assertThat(properties.getTiers()).containsExactly("PLATINUM", "EMERALD");
                    assertThat(properties.getDivisions()).containsExactly("I", "II");
                    assertThat(properties.getMatchCount()).isEqualTo(50);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RiotSchedulerProperties.class)
    static class PropertiesConfiguration {
    }
}
