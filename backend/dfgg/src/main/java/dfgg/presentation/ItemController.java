package dfgg.presentation;

import dfgg.application.ItemSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class ItemController {
    private final ItemSyncService itemSyncService;

    public ItemController(ItemSyncService itemSyncService) {
        this.itemSyncService = itemSyncService;
    }

    @PostMapping("/items")
    public ResponseEntity<Void> getItems() {
        itemSyncService.syncCoreItem();
        return ResponseEntity.noContent().build();
    }
}
