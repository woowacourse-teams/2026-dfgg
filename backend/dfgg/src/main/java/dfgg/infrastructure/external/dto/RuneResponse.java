package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Data Dragon {@code runesReforged.json}의 계열과 룬. 한글 응답을 기준으로 두고 같은 빌드의 영문 이름을 id로 붙인다.
 *
 * @param version     이미지 저장에 쓰는 패치(예: 16.21)
 * @param dataVersion 조회한 Data Dragon 빌드(예: 16.21.1)
 */
public record RuneResponse(
        String version,
        String dataVersion,
        List<Style> styles,
        Map<Integer, String> englishNames
) {

    public RuneResponse {
        styles = List.copyOf(styles);
        englishNames = Map.copyOf(englishNames);
    }

    public Map<String, String> localizedName(int runeId, String koreanName) {
        String englishName = englishNames.get(runeId);
        if (koreanName == null || koreanName.isBlank() || englishName == null || englishName.isBlank()) {
            throw new IllegalStateException("[Error] 룬 이름이 없습니다: " + runeId);
        }
        return Map.of("ko-KR", koreanName, "en-US", englishName);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Style(int id, String icon, String name, List<Slot> slots) {

        public List<Perk> perks() {
            if (slots == null) {
                return List.of();
            }
            return slots.stream()
                    .flatMap(slot -> slot.runes() == null ? Stream.empty() : slot.runes().stream())
                    .toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Slot(List<Perk> runes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Perk(int id, String icon, String name) {
    }
}
