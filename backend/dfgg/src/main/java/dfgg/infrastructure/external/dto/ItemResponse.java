package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemResponse(
        String version,
        String dataVersion,
        Map<String, ItemData> data
) {
    public ItemResponse(Map<String, ItemData> data) {
        this(null, null, data);
    }
}
