package dfgg.presentation;

import dfgg.application.aram.AramMayhemQueryService;
import dfgg.domain.language.Language;
import dfgg.presentation.dto.response.ChampionAugmentsResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aram-mayhem")
public class AramMayhemController {

    private final AramMayhemQueryService aramMayhemQueryService;

    public AramMayhemController(AramMayhemQueryService aramMayhemQueryService) {
        this.aramMayhemQueryService = aramMayhemQueryService;
    }

    @GetMapping("/champions/{championId}/augments")
    public ResponseEntity<ChampionAugmentsResponse> getAugments(
            @PathVariable int championId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        ChampionAugmentsResponse response = aramMayhemQueryService.findByChampion(
                championId, Language.fromAcceptLanguage(acceptLanguage));

        return ResponseEntity.ok(response);
    }
}
