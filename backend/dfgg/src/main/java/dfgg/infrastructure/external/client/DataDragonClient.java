package dfgg.infrastructure.external.client;

import dfgg.infrastructure.external.dto.ChampionResponse;
import dfgg.infrastructure.external.dto.ItemData;
import dfgg.infrastructure.external.dto.ItemResponse;
import dfgg.infrastructure.external.dto.RuneResponse;
import dfgg.infrastructure.external.dto.SummonerSpellResponse;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public String getLatestVersion() {
        return normalizePatch(getLatestDataVersion());
    }

    private String getLatestDataVersion() {
        return getDataVersions()[0];
    }

    /** Raw 경기 패치에 대응하는 가장 높은 Data Dragon 아이템 빌드를 찾는다. */
    public String resolveItemDataVersionForPatch(String patch) {
        if (patch == null || !patch.matches("[0-9]+\\.[0-9]+")) {
            throw new IllegalArgumentException("게임 패치는 major.minor 형식이어야 합니다.");
        }
        return Arrays.stream(getDataVersions())
                .filter(version -> version != null && version.matches(java.util.regex.Pattern.quote(patch) + "\\.[0-9]+"))
                .max(Comparator.comparingInt(this::itemBuildNumber))
                .orElseThrow(() -> new IllegalStateException("패치 " + patch + "에 해당하는 Data Dragon 빌드가 없습니다."));
    }

    private int itemBuildNumber(String version) {
        return Integer.parseInt(version.substring(version.lastIndexOf('.') + 1));
    }

    private String[] getDataVersions() {
        String[] versions = restClient.get()
                .uri("api/versions.json")
                .retrieve()
                .body(String[].class);
        if (versions == null || versions.length == 0) {
            throw new IllegalStateException("[Error] Data Dragon version response is empty");
        }
        return versions;
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

    /** 룬 동기화에 사용할 최신 Data Dragon 빌드의 룬을 한글 이름으로 조회하고, 같은 빌드의 영문 이름을 붙인다. */
    public RuneResponse getRunes() {
        String version = getLatestDataVersion();
        List<RuneResponse.Style> korean = getRuneStyles(version, "ko_KR");
        Map<Integer, String> englishNames = new HashMap<>();
        for (RuneResponse.Style style : getRuneStyles(version, "en_US")) {
            englishNames.put(style.id(), style.name());
            style.perks().forEach(perk -> englishNames.put(perk.id(), perk.name()));
        }
        return new RuneResponse(normalizePatch(version), version, korean, englishNames);
    }

    private List<RuneResponse.Style> getRuneStyles(String version, String locale) {
        RuneResponse.Style[] styles = restClient.get()
                .uri("/cdn/{version}/data/{locale}/runesReforged.json", version, locale)
                .retrieve()
                .body(RuneResponse.Style[].class);
        if (styles == null || styles.length == 0) {
            throw new IllegalStateException("[Error] 룬 응답이 비어 있습니다: " + locale);
        }
        return List.of(styles);
    }

    /**
     * 룬 아이콘은 빌드 버전 없는 {@code /cdn/img/perk-images/...} 아래에 있다.
     * 경로를 그대로 URL에 붙이므로 {@code perk-images} 밖이나 상위 경로를 가리키는 값은 거부한다.
     */
    public byte[] getRuneImage(String iconPath) {
        if (iconPath == null || !iconPath.matches("perk-images(/[A-Za-z0-9_-]+)+\\.png")) {
            throw new IllegalArgumentException("[Error] 룬 이미지 경로가 올바르지 않습니다: " + iconPath);
        }
        byte[] image = restClient.get()
                .uri("/cdn/img/" + iconPath)
                .retrieve()
                .body(byte[].class);
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (image == null || image.length < signature.length
                || !Arrays.equals(signature, Arrays.copyOf(image, signature.length))) {
            throw new IllegalStateException("[Error] 룬 이미지가 PNG 형식이 아닙니다: " + iconPath);
        }
        return image;
    }

    /** 현재 서비스 아이템 동기화에 사용할 최신 Data Dragon 빌드를 조회한다. */
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

    /** 지정한 운영 빌드의 한글 아이템 카탈로그와 같은 빌드의 영문 이름을 조회한다. */
    public ItemResponse getLocalizedItems(String dataVersion) {
        ItemResponse english = getItems(dataVersion);
        ItemResponse korean = restClient.get()
                .uri("/cdn/{version}/data/ko_KR/item.json", dataVersion)
                .retrieve()
                .body(ItemResponse.class);
        if (korean == null || korean.data() == null || korean.data().isEmpty()) {
            throw new IllegalStateException("Data Dragon 한글 아이템 카탈로그가 비어 있습니다.");
        }
        if (!dataVersion.equals(korean.version())) {
            throw new IllegalStateException("Data Dragon 한글 응답 버전이 요청한 빌드와 다릅니다.");
        }

        Map<String, ItemData> localizedItems = new HashMap<>();
        Map<String, String> englishNames = new HashMap<>();
        korean.data().forEach((id, item) -> {
            // 실제 카탈로그에는 이름이 없는 폐기·타 모드 항목이 포함된다. 협곡 미사용이 명시된 경우만 제외한다.
            if (item != null && item.maps() != null && Boolean.FALSE.equals(item.maps().get("11"))
                    && (item.name() == null || item.name().isBlank())) {
                return;
            }
            if (item == null || item.name() == null || item.name().isBlank()) {
                throw new IllegalStateException("Data Dragon 아이템의 한글 이름이 없습니다: " + id);
            }
            ItemData englishItem = english.data().get(id);
            if (englishItem == null || englishItem.name() == null || englishItem.name().isBlank()) {
                throw new IllegalStateException("Data Dragon 아이템의 영문 이름이 없습니다: " + id);
            }
            englishNames.put(id, englishItem.name());
            localizedItems.put(id, item);
        });
        if (localizedItems.isEmpty()) {
            throw new IllegalStateException("Data Dragon 한글 아이템 카탈로그가 비어 있습니다.");
        }
        return new ItemResponse(english.version(), dataVersion, Map.copyOf(localizedItems), Map.copyOf(englishNames));
    }

    /**
     * 과거 경기의 구매를 경기 당시 카탈로그로 분류하기 위해 지정한 Data Dragon 빌드를 조회한다.
     * 최신 빌드로 대체하거나 기존 items DB를 동기화하지 않는다.
     *
     * @param dataVersion 경기 패치에 해당하는 전체 빌드 버전. 예: 16.18.1
     */
    public ItemResponse getItems(String dataVersion) {
        if (dataVersion == null || !dataVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw new IllegalArgumentException("Data Dragon 빌드는 숫자 세 부분으로 지정해야 합니다. 예: 16.18.1");
        }
        ItemResponse response = restClient.get()
                .uri("/cdn/{version}/data/en_US/item.json", dataVersion)
                .retrieve()
                .body(ItemResponse.class);
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("Data Dragon 아이템 카탈로그가 비어 있습니다.");
        }
        if (!dataVersion.equals(response.version())) {
            throw new IllegalStateException("Data Dragon 응답 버전이 요청한 빌드와 다릅니다.");
        }
        return new ItemResponse(normalizePatch(dataVersion), dataVersion, response.data());
    }
    /** 최신 빌드의 한글 소환사 주문과 같은 빌드의 영문 이름을 조회한다. */
    public SummonerSpellResponse getSummonerSpells() {
        String version = getLatestDataVersion();
        SummonerSpellResponse korean = getSummonerSpells(version, "ko_KR");
        SummonerSpellResponse english = getSummonerSpells(version, "en_US");
        Map<String, String> englishNames = new HashMap<>();
        english.data().forEach((riotKey, spell) -> englishNames.put(riotKey, spell.name()));
        return new SummonerSpellResponse(normalizePatch(version), version, korean.data(), Map.copyOf(englishNames));
    }

    private SummonerSpellResponse getSummonerSpells(String version, String locale) {
        SummonerSpellResponse response = restClient.get()
                .uri("/cdn/{version}/data/{locale}/summoner.json", version, locale)
                .retrieve()
                .body(SummonerSpellResponse.class);
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("[Error] 소환사 주문 응답이 비어 있습니다: " + locale);
        }
        return response;
    }

    public byte[] getSummonerSpellImage(String version, String filename) {
        byte[] image = restClient.get()
                .uri("/cdn/{version}/img/spell/{filename}", version, filename)
                .retrieve()
                .body(byte[].class);
        return requirePng(image, filename);
    }

    private byte[] requirePng(byte[] image, String name) {
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (image == null || image.length < signature.length
                || !Arrays.equals(signature, Arrays.copyOf(image, signature.length))) {
            throw new IllegalStateException("[Error] 이미지가 PNG 형식이 아닙니다: " + name);
        }
        return image;
    }
}
