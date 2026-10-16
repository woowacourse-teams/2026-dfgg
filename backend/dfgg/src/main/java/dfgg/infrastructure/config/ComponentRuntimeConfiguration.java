package dfgg.infrastructure.config;

import dfgg.application.recommend.component.ComponentRecommendationRuntime;
import dfgg.application.recommend.component.ComponentRecommendationService;
import dfgg.domain.match.ComponentPolicyLookup;
import dfgg.infrastructure.external.client.DataDragonClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ComponentRuntimeConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ComponentRuntimeConfiguration.class);

    @Bean
    public ComponentRecommendationRuntime componentRecommendationRuntime(
            ObjectProvider<ComponentRecommendationService> recommendations,
            ObjectProvider<ComponentPolicyLookup> lookups,
            LeagueOfLegendsVersionProperties version,
            RestClient.Builder restClients,
            @Value("${recommendation.component-ltr.model-path:}") String modelPath) {
        try {
            ComponentRecommendationService service = modelPath.isBlank() ? null : recommendations.getIfAvailable();
            if (service == null) {
                log.info("Component 모델이 설정되지 않아 Component 추천을 비활성화합니다.");
                return ComponentRecommendationRuntime.unavailable();
            }
            lookups.getObject().validatePatch(version.version());
            DataDragonClient client = catalogClient(restClients);
            String dataVersion = client.resolveItemDataVersionForPatch(version.version());
            var catalog = client.getLocalizedItems(dataVersion);
            log.info("Component 추천 준비 완료: patch={}, dataVersion={}", version.version(), dataVersion);
            return ComponentRecommendationRuntime.available(service, catalog);
        } catch (RuntimeException exception) {
            log.error("Component 추천 준비 실패: patch={}. Component 추천을 비활성화합니다.", version.version(), exception);
            return ComponentRecommendationRuntime.unavailable();
        }
    }

    private DataDragonClient catalogClient(RestClient.Builder restClients) {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var requests = new JdkClientHttpRequestFactory(httpClient);
        requests.setReadTimeout(Duration.ofSeconds(5));
        return new DataDragonClient(restClients.clone().requestFactory(requests));
    }
}
