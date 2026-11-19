package dfgg.presentation;

import dfgg.application.rune.RuneRecommendationService;
import dfgg.domain.language.Language;
import dfgg.presentation.dto.request.RuneRecommendationRequest;
import dfgg.presentation.dto.response.RuneRecommendationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RuneRecommendationController {

    private final RuneRecommendationService runeRecommendationService;

    public RuneRecommendationController(RuneRecommendationService runeRecommendationService) {
        this.runeRecommendationService = runeRecommendationService;
    }

    @PostMapping("/runes")
    public ResponseEntity<RuneRecommendationResponse> recommendRunes(
            @Valid @RequestBody RuneRecommendationRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        RuneRecommendationResponse response = runeRecommendationService.recommend(
                request.myChampion(), Language.fromAcceptLanguage(acceptLanguage));
        return ResponseEntity.ok(response);
    }
}
