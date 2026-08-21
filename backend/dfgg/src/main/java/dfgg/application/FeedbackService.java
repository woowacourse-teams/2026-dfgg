package dfgg.application;

import dfgg.domain.feedback.Feedback;
import dfgg.domain.feedback.FeedbackRepository;
import dfgg.presentation.dto.request.FeedbackRequest;
import dfgg.presentation.dto.response.FeedbackResponse;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {
    private final FeedbackRepository feedbackRepository;

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    public FeedbackResponse save(FeedbackRequest request) {
        Feedback feedback = new Feedback(null, request.date(), request.content());
        feedbackRepository.save(feedback);
        return new FeedbackResponse(request.date(), request.content());
    }
}
