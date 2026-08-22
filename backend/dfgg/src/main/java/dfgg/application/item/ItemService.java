package dfgg.application.item;

import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ItemData;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ItemService {

    private static final String BOOTS_TAG = "Boots";
    private static final String CONSUMABLE_TAG = "Consumable";
    private static final String TRINKET_TAG = "Trinket";

    private final DataDragonClient dataDragonClient;
    private final ItemRepository itemRepository;

    public ItemService(DataDragonClient dataDragonClient, ItemRepository itemRepository) {
        this.dataDragonClient = dataDragonClient;
        this.itemRepository = itemRepository;
    }

    public void syncCoreItems() {
        ItemResponse response = dataDragonClient.getItems();
        List<Item> coreItems = response.data()
                .entrySet()
                .stream()
                .filter(entry -> isCoreItem(entry.getValue()))
                .map(entry -> {
                    Long itemId = Long.parseLong(entry.getKey());
                    ItemData data = entry.getValue();
                    return new Item(
                            itemId,
                            data.name(),
                            data.tags()
                    );
                }).toList();

        itemRepository.saveAll(coreItems);
    }

    public List<Item> findItemsByIds(Collection<Long> itemIds) {
        return itemRepository.findAllById(itemIds);
    }

    public Set<Integer> findCoreItemIds() {
        return itemRepository.findAll().stream()
                .map(Item::getItemId)
                .map(Math::toIntExact)
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean isCoreItem(ItemData data) {
        if (isStartingItem(data)) {
            return false;
        }
        List<String> tags = tagsOf(data);
        if (!tags.contains(BOOTS_TAG) && hasRemainingUpgrade(data)) {
            return false;
        }
        if (data.depth() != null && data.depth() <= 1) {
            return false;
        }
        if (Boolean.TRUE.equals(data.consumed())) {
            return false;
        }
        if (tags.contains(CONSUMABLE_TAG) || tags.contains(TRINKET_TAG)) {
            return false;
        }
        return data.maps() == null || !Boolean.FALSE.equals(data.maps().get("11"));
    }

    private boolean isStartingItem(ItemData data) {
        return data.from() == null || data.from().isEmpty();
    }

    private boolean hasRemainingUpgrade(ItemData data) {
        return data.into() != null && !data.into().isEmpty();
    }

    private List<String> tagsOf(ItemData data) {
        if (data.tags() == null) {
            return List.of();
        }
        return data.tags();
    }
}
