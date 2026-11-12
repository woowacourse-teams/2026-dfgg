package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChampionResponse(
        String version,
        String dataVersion,
        Map<String, ChampionData> data,
        Map<String, String> englishNames
) {
    public ChampionResponse(Map<String, ChampionData> data) {
        this(null, null, data, Map.of());
    }
    public ChampionResponse(String version, String dataVersion, Map<String, ChampionData> data) {
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
