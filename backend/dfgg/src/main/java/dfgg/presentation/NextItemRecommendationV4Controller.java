package dfgg.presentation;

import dfgg.application.recommend.NextItemRecommendationV4Service;
import dfgg.domain.language.Language;
import dfgg.presentation.dto.request.NextItemRecommendationV4Request;
import dfgg.presentation.dto.response.NextItemRecommendationV4Response;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations/v4")
public class NextItemRecommendationV4Controller {
    private final NextItemRecommendationV4Service recommendations;

    public NextItemRecommendationV4Controller(NextItemRecommendationV4Service recommendations) {
        this.recommendations = recommendations;
    }

    @PostMapping
    public ResponseEntity<NextItemRecommendationV4Response> recommend(
            @Valid @RequestBody NextItemRecommendationV4Request request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(recommendations.recommendNextItem(request, Language.fromAcceptLanguage(acceptLanguage)));
    }
}
