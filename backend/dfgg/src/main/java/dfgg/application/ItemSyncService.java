package dfgg.application;

import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ItemData;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ItemSyncService {

    private static final Logger log = LoggerFactory.getLogger(ItemSyncService.class);

    private final DataDragonClient dataDragonClient;
    private final ItemRepository itemRepository;

    public ItemSyncService(DataDragonClient dataDragonClient, ItemRepository itemRepository) {
        this.dataDragonClient = dataDragonClient;
        this.itemRepository = itemRepository;
    }

    public void syncCoreItem() {
        long startedAtNanos = System.nanoTime();
        log.info("Core item metadata sync started");
        ItemResponse response = dataDragonClient.getItems();

        List<Item> coreItems = response.data()
                .entrySet()
                .stream()
                .filter(entry -> isCoreItem(entry.getValue()))
                .map(entry -> {
                    Long itemId = Long.parseLong(entry.getKey());
                    ItemData data = entry.getValue();
                    return new Item(itemId,
                            data.name());
                }).toList();

        itemRepository.saveAll(coreItems);
        log.info(
                "Core item metadata sync completed: savedItems={}, durationMs={}",
                coreItems.size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
        );
    }

    private boolean isCoreItem(ItemData data) {
        if (data.into() != null && !data.into().isEmpty()) {
            return false;
        }
        if (data.depth() != null && data.depth() <= 1) {
            return false;
        }
        if (Boolean.TRUE.equals(data.consumed())) {
            return false;
        }
        if (data.tags() != null && data.tags().stream()
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .anyMatch(tag -> tag.equals("consumable") || tag.equals("trinket"))) {
            return false;
        }
        return data.maps() == null || !Boolean.FALSE.equals(data.maps().get("11"));
    }
}
