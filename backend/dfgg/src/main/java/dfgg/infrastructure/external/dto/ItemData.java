package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemData(
        String name,
        List<String> from,
        List<String> into,
        List<String> tags,
        Map<String, Boolean> maps,
        Boolean consumed,
        Integer depth,
        Gold gold,
        Image image,
        Boolean inStore,
        Boolean hideFromAll
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Gold(Integer base, Integer total, Boolean purchasable) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(String full) {
    }

    public ItemData(String name, List<String> from, List<String> into, List<String> tags,
                    Map<String, Boolean> maps, Boolean consumed, Integer depth, Map<String, Integer> gold) {
        this(name, from, into, tags, maps, consumed, depth,
                gold == null ? null : new Gold(gold.get("base"), gold.get("total"), null),
                null, null, null);
    }

    public ItemData(
            String name,
            List<String> from,
            List<String> into,
            List<String> tags,
            Map<String, Boolean> maps,
            Boolean consumed,
            Integer depth
    ) {
        this(name, from, into, tags, maps, consumed, depth, null);
    }

    public ItemData(String name, List<String> from, List<String> into) {
        this(name, from, into, null, null, null, null, null);
    }

    public ItemData(
            String name,
            List<String> from,
            List<String> into,
            List<String> tags,
            Map<String, Boolean> maps,
            Boolean consumed
    ) {
        this(name, from, into, tags, maps, consumed, null, null);
    }
}
