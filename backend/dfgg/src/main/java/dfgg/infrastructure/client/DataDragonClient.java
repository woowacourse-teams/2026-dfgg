package dfgg.infrastructure.client;

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
            throw new IllegalArgumentException("[Error] Data Dragon version response is empty");
        }
        return versions[0];
    }
}
