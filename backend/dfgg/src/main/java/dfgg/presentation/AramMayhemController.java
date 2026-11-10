package dfgg.presentation;

import dfgg.application.aram.AramMayhemQueryService;
import dfgg.domain.language.Language;
import dfgg.presentation.dto.request.AugmentLookupRequest;
import dfgg.presentation.dto.response.AugmentLookupResponse;
import dfgg.presentation.dto.response.ChampionAugmentsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aram-mayhem")
public class AramMayhemController {

    private final AramMayhemQueryService aramMayhemQueryService;

    public AramMayhemController(AramMayhemQueryService aramMayhemQueryService) {
        this.aramMayhemQueryService = aramMayhemQueryService;
    }

    /** 이름 언어는 게임 언어({@code lang})가 있으면 그것, 없으면 앱 언어({@code Accept-Language})다. */
    @GetMapping("/champions/{championId}/augments")
    public ResponseEntity<ChampionAugmentsResponse> getAugments(
            @PathVariable int championId,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        ChampionAugmentsResponse response = aramMayhemQueryService.findByChampion(
                championId, Language.resolve(lang, acceptLanguage));

        return ResponseEntity.ok(response);
    }

    /** 이름에 특수문자가 있어 본문으로 받는다. 조회이지만 다른 조회 API처럼 POST다. */
    @PostMapping("/champions/{championId}/augments/lookup")
    public ResponseEntity<AugmentLookupResponse> lookup(
            @PathVariable int championId,
            @Valid @RequestBody AugmentLookupRequest request,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        AugmentLookupResponse response = aramMayhemQueryService.lookup(
                championId, request.names(), Language.resolve(lang, acceptLanguage));

        return ResponseEntity.ok(response);
    }
}
