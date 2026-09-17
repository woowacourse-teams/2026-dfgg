package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChampionResponse(
        String version,
        String dataVersion,
        Map<String, ChampionData> data
) {
    public ChampionResponse(Map<String, ChampionData> data) {
        this(null, null, data);
    }
}
