package dfgg.presentation;

import dfgg.application.champion.ChampionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class ChampionController {

    private final ChampionService championService;

    public ChampionController(ChampionService championService) {
        this.championService = championService;
    }

    @PostMapping("/champions")
    public ResponseEntity<Void> getChampions() {
        championService.syncChampions();
        return ResponseEntity.noContent().build();
    }
}
