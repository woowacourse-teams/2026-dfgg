package dfgg.infrastructure.config;

import dfgg.application.recommend.situational.SituationalComponentRuntime;
import dfgg.domain.match.SituationalThresholds;
import dfgg.infrastructure.recommend.SituationalComponentLookupLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/** Situational 준비 실패는 이 기능만 끄고 Core·Component 추천을 막지 않는다. */
@Configuration
public class SituationalComponentConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SituationalComponentConfiguration.class);

    @Bean
    public SituationalComponentRuntime situationalComponentRuntime(
            @Value("${recommendation.situational.lookup-path:}") String path,
            @Value("${recommendation.situational.min-lift:2.0}") double minLift,
            @Value("${recommendation.situational.min-base-rate:0.01}") double minBaseRate,
            ResourceLoader resources, LeagueOfLegendsVersionProperties version) {
        if (path.isBlank()) {
            log.info("Situational 조회표가 설정되지 않아 상황 대응 추천을 비활성화합니다.");
            return SituationalComponentRuntime.disabled();
        }
        try (var input = resources.getResource(path).getInputStream()) {
            var lookup = SituationalComponentLookupLoader.load(input.readAllBytes());
            lookup.validatePatch(version.version());
            var thresholds = new SituationalThresholds(minLift, minBaseRate);
            log.info("Situational 추천 준비 완료: patch={}, minLift={}, minBaseRate={}", version.version(), minLift, minBaseRate);
            return SituationalComponentRuntime.available(lookup, thresholds);
        } catch (Exception exception) {
            log.error("Situational 추천 준비 실패: path={}. 상황 대응 추천을 비활성화합니다.", path, exception);
            return SituationalComponentRuntime.disabled();
        }
    }
}
