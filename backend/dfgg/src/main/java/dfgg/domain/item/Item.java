package dfgg.domain.item;

public class Item {
    private Long itemId;
    private String name;

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
