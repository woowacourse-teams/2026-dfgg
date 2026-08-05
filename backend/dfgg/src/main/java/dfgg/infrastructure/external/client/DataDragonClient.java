package dfgg.infrastructure.external.client;

import dfgg.infrastructure.external.dto.ChampionResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DataDragonClient {

    private static final String BASE_URL = "https://ddragon.leagueoflegends.com";

    private final RestClient restClient;

    public DataDragonClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(BASE_URL)
                .build();
    }

    public String getLatestVersion() {
        String[] versions = restClient.get()
                .uri("api/versions.json")
                .retrieve()
                .body(String[].class);

        if (versions == null || versions.length == 0) {
            throw new IllegalStateException("[Error] Data Dragon version response is empty");
        }
        return versions[0];
    }

    public ChampionResponse getChampions() {
        ChampionResponse response = restClient.get()
                .uri("/cdn/{version}/data/ko_KR/champion.json",
                        getLatestVersion())
                .retrieve()
                .body(ChampionResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("[Error] Data Dragon champion response is empty");
        }

        boolean hasInvalidChampion = response.data().values().stream()
                .anyMatch(data -> data == null
                        || data.key() == null
                        || data.key().isBlank()
                        || data.name() == null
                        || data.name().isBlank()
                        || data.tags() == null
                        || data.tags().isEmpty());

        if (hasInvalidChampion) {
            throw new IllegalStateException("[Error] Data Dragon champion data is invalid");
        }

        return response;
    }
}
