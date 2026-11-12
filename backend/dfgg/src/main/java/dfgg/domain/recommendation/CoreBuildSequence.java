package dfgg.domain.recommendation;

import dfgg.domain.item.Item;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CoreBuildSequence {
    private static final String BOOTS_TAG = "Boots";
    private static final int CORE_ITEM_COUNT = 3;

    private final List<Item> orderedItems;

    private CoreBuildSequence(List<Item> orderedItems) {
        this.orderedItems = List.copyOf(orderedItems);
    }

    public static Optional<CoreBuildSequence> from(List<Item> purchasedItems) {
        List<Item> coreItems = new ArrayList<>(CORE_ITEM_COUNT);

        for (Item item : purchasedItems) {
            if (item.hasTag(BOOTS_TAG)) {
                continue;
            }
            coreItems.add(item);

            if (coreItems.size() == CORE_ITEM_COUNT) {
                break;
            }
        }
        if (coreItems.size() < CORE_ITEM_COUNT) {
            return Optional.empty();
        }
        return Optional.of(new CoreBuildSequence(coreItems));
    }

    public List<Long> clusterKey() {
        return orderedItems.stream()
                .map(Item::getItemId)
                .sorted()
                .toList();
    }

    public List<Item> getOrderedItems() {
        return orderedItems;
    }
}
