package dfgg.presentation;

import dfgg.application.ChampionSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class ChampionController {

    private final ChampionSyncService championSyncService;

    public ChampionController(ChampionSyncService championSyncService) {
        this.championSyncService = championSyncService;
    }

    @PostMapping("/champions")
    public ResponseEntity<Void> getChampions() {
        championSyncService.syncChampions();
        return ResponseEntity.noContent().build();
    }
}
