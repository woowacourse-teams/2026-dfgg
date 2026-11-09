package dfgg.domain.item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "items")
public class Item {

    @Id
    @Column(name = "item_id")
    private Long itemId;

    @Column(nullable = false)
    private String name;

    protected Item() {
    }

    public Item(Long itemId, String name) {
        this.itemId = itemId;
        this.name = name;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }
}
