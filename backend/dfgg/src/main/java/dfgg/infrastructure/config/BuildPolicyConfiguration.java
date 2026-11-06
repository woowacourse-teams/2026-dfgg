package dfgg.infrastructure.config;

import dfgg.domain.recommendation.AssassinBuildPolicy;
import dfgg.domain.recommendation.ChampionBuildPolicy;
import dfgg.domain.recommendation.FighterBuildPolicy;
import dfgg.domain.recommendation.MageBuildPolicy;
import dfgg.domain.recommendation.MarksmanBuildPolicy;
import dfgg.domain.recommendation.SupportBuildPolicy;
import dfgg.domain.recommendation.TankBuildPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 도메인 정책이 Spring에 의존하지 않도록 애플리케이션에서 사용할 정책 빈을 등록한다.
 */
@Configuration(proxyBeanMethods = false)
public class BuildPolicyConfiguration {

    @Bean
    ChampionBuildPolicy tankBuildPolicy() {
        return new TankBuildPolicy();
    }

    @Bean
    ChampionBuildPolicy fighterBuildPolicy() {
        return new FighterBuildPolicy();
    }

    @Bean
    ChampionBuildPolicy mageBuildPolicy() {
        return new MageBuildPolicy();
    }

    @Bean
    ChampionBuildPolicy assassinBuildPolicy() {
        return new AssassinBuildPolicy();
    }

    @Bean
    ChampionBuildPolicy marksmanBuildPolicy() {
        return new MarksmanBuildPolicy();
    }

    @Bean
    ChampionBuildPolicy supportBuildPolicy() {
        return new SupportBuildPolicy();
    }
}
