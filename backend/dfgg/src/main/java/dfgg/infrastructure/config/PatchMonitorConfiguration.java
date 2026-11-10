package dfgg.infrastructure.config;

import dfgg.application.champion.ChampionService;
import dfgg.application.item.ItemService;
import dfgg.application.patch.PatchCatalogSync;
import dfgg.application.patch.PatchImageChecker;
import dfgg.application.patch.PatchMonitor;
import dfgg.application.patch.ServicePatch;
import dfgg.application.rune.RuneService;
import dfgg.application.spell.SummonerSpellService;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.storage.S3ImageStorage;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * 새 패치 감지(U9). 모든 서버에서 켜고, main을 쓰는 서버({@code collection.scheduler.enabled})만 카탈로그와 그림을 올린다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "patch.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PatchMonitorConfiguration {

    @Bean
    PatchImageChecker patchImageChecker(S3ImageStorage storage) {
        return new PatchImageChecker(storage);
    }

    @Bean
    @ConditionalOnProperty(prefix = "collection.scheduler", name = "enabled", havingValue = "true")
    PatchCatalogSync patchCatalogSync(ChampionService champions, ItemService items, SummonerSpellService spells,
                                      RuneService runes, DataDragonClient dataDragon, PatchImageChecker images) {
        return new PatchCatalogSync(champions, items, spells, runes, dataDragon, images);
    }

    /** 기동 직후 감지가 readiness 전에 돌므로 Data Dragon 조회에 짧은 제한 시간을 둔다. */
    @Bean
    PatchMonitor patchMonitor(RestClient.Builder restClients, ServicePatch servicePatch, PatchImageChecker images,
                              ObjectProvider<PatchCatalogSync> catalogSync, MeterRegistry meters) {
        return new PatchMonitor(versionClient(restClients), servicePatch, images,
                Optional.ofNullable(catalogSync.getIfAvailable()), meters);
    }

    @Bean
    PatchMonitorScheduler patchMonitorScheduler(PatchMonitor monitor) {
        return new PatchMonitorScheduler(monitor);
    }

    private DataDragonClient versionClient(RestClient.Builder restClients) {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var requests = new JdkClientHttpRequestFactory(httpClient);
        requests.setReadTimeout(Duration.ofSeconds(5));
        return new DataDragonClient(restClients.clone().requestFactory(requests));
    }
}
