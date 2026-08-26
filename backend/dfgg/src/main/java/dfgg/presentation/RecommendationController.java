package dfgg.presentation;

import dfgg.application.recommend.MultiBuildRecommendationService;
import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.application.recommend.RecommendationService;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.request.RecommendationRequest;
import dfgg.presentation.dto.response.MultiBuildRecommendationResponse;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
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
    private final NextItemRecommendationService nextItemRecommendationService;

    public RecommendationController(
            RecommendationService recommendationService,
            MultiBuildRecommendationService multiBuildRecommendationService
    ) {
    public RecommendationController(RecommendationService recommendationService, NextItemRecommendationService nextItemRecommendationService) {
        this.recommendationService = recommendationService;
        this.nextItemRecommendationService = nextItemRecommendationService;
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

    @PostMapping("/v3")
    public ResponseEntity<NextItemRecommendationResponse> recommendV3(
            @Valid @RequestBody NextItemRecommendationRequest request
    ) {
        NextItemRecommendationResponse response = nextItemRecommendationService.recommendNextItem(request);
        return ResponseEntity.ok(response);
    }
}
