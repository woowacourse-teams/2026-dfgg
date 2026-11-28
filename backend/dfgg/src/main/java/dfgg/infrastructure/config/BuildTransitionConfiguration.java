package dfgg.infrastructure.config;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.recommend.v3.generator.RecentPatchWindowCache;
import dfgg.application.recommend.v3.generator.transition.BuildTransitionReader;
import dfgg.application.recommend.v3.generator.transition.DatabaseBuildTransitionReader;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Build 전이 리더를 등록한다.
 */
@Configuration(proxyBeanMethods = false)
public class BuildTransitionConfiguration {

    @Bean
    BuildTransitionReader buildTransitionReader(
            NormalizedMatchParticipantRepository participantRepository,
            ChampionPositionNormalizer positionNormalizer,
            RecentPatchWindowCache recentPatchWindow
    ) {
        return new DatabaseBuildTransitionReader(participantRepository, positionNormalizer, recentPatchWindow);
    }
}
