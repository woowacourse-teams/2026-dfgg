package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * Data Dragon {@code summoner.json}의 한글 주문(영문 키별)과 같은 빌드의 영문 이름.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SummonerSpellResponse(
        String version,
        String dataVersion,
        Map<String, SummonerSpellData> data,
        Map<String, String> englishNames
) {

    public Map<String, String> localizedName(String riotKey) {
        String englishName = englishNames.get(riotKey);
        if (englishName == null || englishName.isBlank()) {
            throw new IllegalStateException("[Error] 소환사 주문 영문 이름이 없습니다: " + riotKey);
        }
        return Map.of("ko-KR", data.get(riotKey).name(), "en-US", englishName);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SummonerSpellData(String id, String key, String name, Image image, List<String> modes) {

        private static final String SUMMONERS_RIFT_MODE = "CLASSIC";

        public boolean isSummonersRift() {
            return modes != null && modes.contains(SUMMONERS_RIFT_MODE);
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Image(String full) {
        }
    }
}
