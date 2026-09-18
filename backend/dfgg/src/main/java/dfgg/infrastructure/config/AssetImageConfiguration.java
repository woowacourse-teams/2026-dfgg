package dfgg.infrastructure.config;

import dfgg.domain.image.ImageUrls;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AssetProperties.class, LeagueOfLegendsVersionProperties.class})
public class AssetImageConfiguration {

    @Bean
    ImageUrls imageUrls(AssetProperties assets, LeagueOfLegendsVersionProperties riot) {
        return assets.imageUrls(riot.version());
    }
}
