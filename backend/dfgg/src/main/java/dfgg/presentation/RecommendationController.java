package dfgg.presentation;

import dfgg.application.RecommendationService;
import dfgg.presentation.dto.request.RecommendationRequest;
import dfgg.presentation.dto.response.RecommendationResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping
    public ResponseEntity<RecommendationResponse> recommend(
            @Valid @RequestBody RecommendationRequest request
    ) {

        RecommendationResponse response = recommendationService.recommend(request);
        return ResponseEntity.ok().body(response);
    }
}
