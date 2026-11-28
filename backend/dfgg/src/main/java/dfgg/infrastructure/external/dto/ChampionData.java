package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChampionData(
        String key,
        String name,
        List<String> tags,
        Image image
) {
    public ChampionData(String key, String name, List<String> tags) {
        this(key, name, tags, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(String full) {
    }
}
