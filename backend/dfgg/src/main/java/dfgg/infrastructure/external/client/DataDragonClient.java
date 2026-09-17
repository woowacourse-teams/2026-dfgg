package dfgg.infrastructure.external.client;

import dfgg.infrastructure.external.dto.ChampionResponse;
import dfgg.infrastructure.external.dto.ItemResponse;
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
        return normalizePatch(getLatestDataVersion());
    }

    private String getLatestDataVersion() {
        String[] versions = restClient.get()
                .uri("api/versions.json")
                .retrieve()
                .body(String[].class);

        if (versions == null || versions.length == 0) {
            throw new IllegalStateException("[Error] Data Dragon version response is empty");
        }
        return versions[0];
    }

    private static String normalizePatch(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("[Error] Data Dragon version is invalid");
        }

        String[] components = version.split("\\.");
        if (components.length < 2
                || components[0].isBlank()
                || components[1].isBlank()) {
            throw new IllegalStateException("[Error] Data Dragon version is invalid: " + version);
        }
        return components[0] + "." + components[1];
    }

    public ChampionResponse getChampions() {
        String version = getLatestDataVersion();
        ChampionResponse response = restClient.get()
                .uri("/cdn/{version}/data/ko_KR/champion.json",
                        version)
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

        ChampionResponse english = restClient.get()
                .uri("/cdn/{version}/data/en_US/champion.json", version)
                .retrieve()
                .body(ChampionResponse.class);
        if (english == null || english.data() == null || english.data().isEmpty()) {
            throw new IllegalStateException("[Error] 영문 챔피언 응답이 비어 있습니다.");
        }
        java.util.Map<String, String> englishNames = new java.util.HashMap<>();
        english.data().forEach((id, data) -> {
            if (data != null && data.name() != null && !data.name().isBlank()) {
                englishNames.put(id, data.name());
            }
        });
        return new ChampionResponse(normalizePatch(version), version, response.data(), englishNames);
    }

    public byte[] getChampionImage(String version, String filename) {
        byte[] image = restClient.get()
                .uri("/cdn/{version}/img/champion/{filename}", version, filename)
                .retrieve()
                .body(byte[].class);
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (image == null || image.length < signature.length
                || !java.util.Arrays.equals(signature, java.util.Arrays.copyOf(image, signature.length))) {
            throw new IllegalStateException("[Error] Data Dragon champion image is not PNG: " + filename);
        }
        return image;
    }

    public byte[] getItemImage(String version, String filename) {
        byte[] image = restClient.get()
                .uri("/cdn/{version}/img/item/{filename}", version, filename)
                .retrieve()
                .body(byte[].class);
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (image == null || image.length < signature.length
                || !java.util.Arrays.equals(signature, java.util.Arrays.copyOf(image, signature.length))) {
            throw new IllegalStateException("[Error] 아이템 이미지가 PNG 형식이 아닙니다: " + filename);
        }
        return image;
    }

    public ItemResponse getItems() {
        String version = getLatestDataVersion();
        ItemResponse response = restClient.get()
                .uri("/cdn/{version}/data/ko_KR/item.json", version)
                .retrieve()
                .body(ItemResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("[Error] Data Dragon item response is empty");
        }

        ItemResponse english = restClient.get()
                .uri("/cdn/{version}/data/en_US/item.json", version)
                .retrieve()
                .body(ItemResponse.class);
        if (english == null || english.data() == null || english.data().isEmpty()) {
            throw new IllegalStateException("[Error] 영문 아이템 응답이 비어 있습니다.");
        }
        java.util.Map<String, String> englishNames = new java.util.HashMap<>();
        english.data().forEach((id, data) -> {
            if (data != null && data.name() != null && !data.name().isBlank()) {
                englishNames.put(id, data.name());
            }
        });
        return new ItemResponse(normalizePatch(version), version, response.data(), englishNames);
    }
}
