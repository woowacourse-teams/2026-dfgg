package dfgg.domain.item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "items")
public class Item {

    @Id
    @Column(name = "item_id")
    private Long itemId;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", nullable = false, columnDefinition = "jsonb default '[]'::jsonb")
    private List<String> tags = new ArrayList<>();

    protected Item() {
    }

    public Item(Long itemId, String name) {
        this(itemId, name, List.of());
    }

    public Item(Long itemId, String name, List<String> tags) {
        this.itemId = itemId;
        this.name = name;
        this.tags = new ArrayList<>();
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    public Long getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
