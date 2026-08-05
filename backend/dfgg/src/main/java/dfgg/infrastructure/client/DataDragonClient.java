package dfgg.infrastructure.client;

import dfgg.infrastructure.dto.ChampionResponse;
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

        if (response == null) {
            throw new IllegalStateException("[Error] Data Dragon champion response is empty");
        }

        return response;
    }
}
