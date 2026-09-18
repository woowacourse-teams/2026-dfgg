package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemResponse(
        String version,
        String dataVersion,
        Map<String, ItemData> data,
        Map<String, String> englishNames
) {
    public ItemResponse(Map<String, ItemData> data) {
        this(null, null, data, Map.of());
    }
    public ItemResponse(String version, String dataVersion, Map<String, ItemData> data) {
        this(version, dataVersion, data, Map.of());
    }

    public Map<String, String> localizedName(String id) {
        String englishName = englishNames == null ? null : englishNames.get(id);
        if (englishName == null || englishName.isBlank()) {
            throw new IllegalStateException("[Error] 영문 이름이 없습니다: " + id);
        }
        return Map.of("ko-KR", data.get(id).name(), "en-US", englishName);
    }
}
