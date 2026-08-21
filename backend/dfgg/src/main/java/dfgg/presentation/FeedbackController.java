package dfgg.presentation;

import dfgg.application.FeedbackService;
import dfgg.presentation.dto.request.FeedbackRequest;
import dfgg.presentation.dto.response.FeedbackResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<FeedbackResponse> saveFeedback(@RequestBody @Valid FeedbackRequest request) {
        FeedbackResponse response = feedbackService.save(request);
        return ResponseEntity.ok(response);
    }
}
