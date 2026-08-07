package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemData(
        String name,
        List<String> into,
        List<String> tags,
        Map<String, Boolean> maps,
        Boolean consumed,
        Integer depth
) {

    public ItemData(String name, List<String> into) {
        this(name, into, null, null, null, null);
    }

    public ItemData(
            String name,
            List<String> into,
            List<String> tags,
            Map<String, Boolean> maps,
            Boolean consumed
    ) {
        this(name, into, tags, maps, consumed, null);
    }
}
