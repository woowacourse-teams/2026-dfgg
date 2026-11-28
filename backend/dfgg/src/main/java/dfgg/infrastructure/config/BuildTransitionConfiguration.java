package dfgg.infrastructure.config;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.recommend.v3.generator.RecentPatchWindowCache;
import dfgg.application.recommend.v3.generator.transition.BuildTransitionReader;
import dfgg.application.recommend.v3.generator.transition.CachingBuildTransitionReader;
import dfgg.application.recommend.v3.generator.transition.DatabaseBuildTransitionReader;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Build 전이 리더를 고른다. 캐시를 켜면 DB 리더 앞에 Caffeine 캐시를 둔다.
 *
 * <p>학습 데이터 export(평가 프로필)는 과거 경기를 한 번씩만 읽어 히트가 없으므로 캐시를 끈다.
 */
@Configuration(proxyBeanMethods = false)
public class BuildTransitionConfiguration {

    @Bean
    BuildTransitionReader buildTransitionReader(
            NormalizedMatchParticipantRepository participantRepository,
            ChampionPositionNormalizer positionNormalizer,
            RecentPatchWindowCache recentPatchWindow,
            ObjectProvider<MeterRegistry> meterRegistry,
            @Value("${recommendation.build-cache.enabled:true}") boolean cacheEnabled,
            @Value("${recommendation.build-cache.maximum-size:20000}") long maximumSize
    ) {
        BuildTransitionReader database = new DatabaseBuildTransitionReader(
                participantRepository, positionNormalizer, recentPatchWindow);
        if (!cacheEnabled) {
            return database;
        }
        CachingBuildTransitionReader caching = CachingBuildTransitionReader.of(
                database, recentPatchWindow, maximumSize);
        meterRegistry.ifAvailable(caching::bindMetrics);
        return caching;
    }
}
