package dfgg.presentation;

import dfgg.application.rune.RuneService;
import dfgg.application.spell.SummonerSpellService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class PickCatalogController {

    private final RuneService runeService;
    private final SummonerSpellService spellService;

    public PickCatalogController(RuneService runeService, SummonerSpellService spellService) {
        this.runeService = runeService;
        this.spellService = spellService;
    }

    @PostMapping("/runes")
    public ResponseEntity<Void> syncRunes() {
        runeService.syncRunes();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/summoner-spells")
    public ResponseEntity<Void> syncSpells() {
        spellService.syncSpells();
        return ResponseEntity.noContent().build();
    }
}
