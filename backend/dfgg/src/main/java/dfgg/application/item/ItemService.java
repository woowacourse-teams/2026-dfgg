package dfgg.application.item;

import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ItemData;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ItemService {

    private static final String CONSUMABLE_TAG = "Consumable";
    private static final String TRINKET_TAG = "Trinket";

    private final DataDragonClient dataDragonClient;
    private final ItemRepository itemRepository;
    private final ItemImageService itemImageService;

    public ItemService(DataDragonClient dataDragonClient, ItemRepository itemRepository,
                       ItemImageService itemImageService) {
        this.dataDragonClient = dataDragonClient;
        this.itemRepository = itemRepository;
        this.itemImageService = itemImageService;
    }

    public void syncItems() {
        ItemResponse response = dataDragonClient.getItems();
        List<Item> items = response.data()
                .entrySet()
                .stream()
                .filter(entry -> isSummonersRiftItem(entry.getValue()))
                .map(entry -> {
                    Long itemId = Long.parseLong(entry.getKey());
                    ItemData data = entry.getValue();
                    if (data.image() == null) {
                        throw new IllegalStateException("[Error] 아이템 이미지 정보가 없습니다: " + itemId);
                    }
                    Map<String, String> names = response.localizedName(entry.getKey());
                    itemImageService.store(response.version(), response.dataVersion(), itemId, data.image().full());
                    return new Item(
                            itemId,
                            names,
                            goldOf(data),
                            data.from(),
                            data.into(),
                            data.tags()
                    );
                }).toList();

        itemRepository.saveAll(items);
    }

    private Map<String, Integer> goldOf(ItemData data) {
        if (data.gold() == null) {
            return null;
        }
        return Map.of(
                "base", data.gold().base(),
                "total", data.gold().total()
        );
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

    private boolean isSummonersRiftItem(ItemData data) {
        if (data.maps() == null || !Boolean.TRUE.equals(data.maps().get("11"))) {
            return false;
        }
        if (data.gold() == null || !Boolean.TRUE.equals(data.gold().purchasable())
                || Boolean.FALSE.equals(data.inStore()) || Boolean.TRUE.equals(data.hideFromAll())) {
            return false;
        }
        List<String> tags = tagsOf(data);
        return !Boolean.TRUE.equals(data.consumed())
                && !tags.contains(CONSUMABLE_TAG) && !tags.contains(TRINKET_TAG);
    }

    private List<String> tagsOf(ItemData data) {
        if (data.tags() == null) {
            return List.of();
        }
        return data.tags();
    }
}
