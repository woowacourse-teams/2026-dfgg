package dfgg.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChampionResponse(
        Map<String, ChampionData> data
) {
}
