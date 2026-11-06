package dfgg.domain.item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "items")
public class Item {

    @Id
    @Column(name = "item_id")
    private Long itemId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Integer> gold;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "from_item_ids", columnDefinition = "jsonb")
    private List<String> fromItemIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "into_item_ids", columnDefinition = "jsonb")
    private List<String> intoItemIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", nullable = false, columnDefinition = "jsonb default '[]'::jsonb")
    private List<String> tags = new ArrayList<>();

    protected Item() {
    }

    public Item(Long itemId, Map<String, String> name) {
        this(itemId, name, List.of());
    }

    public Item(Long itemId, Map<String, String> name, List<String> tags) {
        this(itemId, name, null, tags);
    }

    public Item(Long itemId, Map<String, String> name, Map<String, Integer> gold, List<String> tags) {
        this(itemId, name, gold, null, null, tags);
    }

    public Item(
            Long itemId,
            Map<String, String> name,
            Map<String, Integer> gold,
            List<String> fromItemIds,
            List<String> intoItemIds,
            List<String> tags
    ) {
        this.itemId = itemId;
        this.name = Map.copyOf(name);
        if (gold != null) {
            this.gold = Map.copyOf(gold);
        }
        if (fromItemIds != null) {
            this.fromItemIds = List.copyOf(fromItemIds);
        }
        if (intoItemIds != null) {
            this.intoItemIds = List.copyOf(intoItemIds);
        }
        this.tags = new ArrayList<>();
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    public Long getItemId() {
        return itemId;
    }

    public Map<String, String> getName() {
        return Map.copyOf(name);
    }

    public Map<String, Integer> getGold() {
        if (gold == null) {
            return null;
        }
        return Map.copyOf(gold);
    }

    public List<String> getFromItemIds() {
        if (fromItemIds == null) {
            return null;
        }
        return List.copyOf(fromItemIds);
    }

    public List<String> getIntoItemIds() {
        if (intoItemIds == null) {
            return null;
        }
        return List.copyOf(intoItemIds);
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
