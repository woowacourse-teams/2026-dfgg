package dfgg.application;

import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ItemData;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ItemSyncService {
    private final DataDragonClient dataDragonClient;
    private final ItemRepository itemRepository;

    public ItemSyncService(DataDragonClient dataDragonClient, ItemRepository itemRepository) {
        this.dataDragonClient = dataDragonClient;
        this.itemRepository = itemRepository;
    }

    public void syncCoreItem() {
        ItemResponse response = dataDragonClient.getItems();

        List<Item> coreItems = response.data()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().into() == null
                        || entry.getValue().into().isEmpty())
                .map(entry -> {
                    Long itemId = Long.parseLong(entry.getKey());
                    ItemData data = entry.getValue();
                    return new Item(itemId,
                            data.name());
                }).toList();

        itemRepository.saveAll(coreItems);
    }
}
