package dfgg.presentation;

import dfgg.application.spell.SummonerSpellService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 대회 픽 응답에 쓰는 소환사 주문 카탈로그 적재. 룬은 {@link RuneController}가 맡는다. */
@RestController
@RequestMapping("/admin")
public class PickCatalogController {

    private final SummonerSpellService spellService;

    public PickCatalogController(SummonerSpellService spellService) {
        this.spellService = spellService;
    }

    @PostMapping("/summoner-spells")
    public ResponseEntity<Void> syncSpells() {
        spellService.syncSpells();
        return ResponseEntity.noContent().build();
    }
}
