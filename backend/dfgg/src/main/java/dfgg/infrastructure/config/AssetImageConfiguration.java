package dfgg.infrastructure.config;

import dfgg.application.patch.ServicePatch;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.match.PatchVersion;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AssetProperties.class, LeagueOfLegendsVersionProperties.class})
public class AssetImageConfiguration {

    /** 기동할 때는 설정값({@code riot.version})으로 시작하고, 이후 {@code PatchMonitor}가 앞으로만 바꾼다. */
    @Bean
    ServicePatch servicePatch(LeagueOfLegendsVersionProperties riot) {
        return new ServicePatch(PatchVersion.of(riot.version()));
    }

    @Bean
    ImageUrls imageUrls(AssetProperties assets, ServicePatch servicePatch) {
        return assets.imageUrls(servicePatch::value);
    }
}
