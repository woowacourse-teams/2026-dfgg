package dfgg.presentation;

import dfgg.application.recommend.MultiBuildRecommendationService;
import dfgg.application.recommend.RecommendationService;
import dfgg.presentation.dto.request.RecommendationRequest;
import dfgg.presentation.dto.response.MultiBuildRecommendationResponse;
import dfgg.presentation.dto.response.RecommendationResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final MultiBuildRecommendationService multiBuildRecommendationService;

    public RecommendationController(
            RecommendationService recommendationService,
            MultiBuildRecommendationService multiBuildRecommendationService
    ) {
        this.recommendationService = recommendationService;
        this.multiBuildRecommendationService = multiBuildRecommendationService;
    }

    @PostMapping("/v1")
    public ResponseEntity<RecommendationResponse> recommendV1(
            @Valid @RequestBody RecommendationRequest request
    ) {
        RecommendationResponse response = recommendationService.recommend(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/v2")
    public ResponseEntity<MultiBuildRecommendationResponse> recommendV2(
            @Valid @RequestBody RecommendationRequest request
    ) {
        MultiBuildRecommendationResponse response =
                multiBuildRecommendationService.recommend(request);
        return ResponseEntity.ok(response);
    }
}
