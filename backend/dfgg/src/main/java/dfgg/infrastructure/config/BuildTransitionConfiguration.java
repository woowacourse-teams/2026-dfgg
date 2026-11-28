package dfgg.infrastructure.config;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.item.ItemService;
import dfgg.application.recommend.v3.generator.RecentPatchWindowCache;
import dfgg.application.recommend.v3.generator.transition.BuildCacheWarmer;
import dfgg.application.recommend.v3.generator.transition.BuildCacheWarmer.WarmupLimit;
import dfgg.application.recommend.v3.generator.transition.BuildTransitionReader;
import dfgg.application.recommend.v3.generator.transition.CachingBuildTransitionReader;
import dfgg.application.recommend.v3.generator.transition.DatabaseBuildTransitionReader;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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

    @Bean
    @ConditionalOnExpression(
            "${recommendation.build-cache.enabled:true} and ${recommendation.build-cache.warmup.enabled:true}")
    BuildCacheWarmer buildCacheWarmer(
            BuildTransitionReader buildTransitionReader,
            ChampionItemStatsRepository championItemStatsRepository,
            ItemService itemService,
            @Value("${recommendation.build-cache.warmup.max-keys:5000}") int maxKeys,
            @Value("${recommendation.build-cache.warmup.parallelism:4}") int parallelism,
            @Value("${recommendation.build-cache.warmup.timeout:120s}") Duration timeout
    ) {
        return new BuildCacheWarmer(buildTransitionReader, championItemStatsRepository, itemService,
                new WarmupLimit(maxKeys, parallelism, timeout), Clock.systemUTC());
    }
}
