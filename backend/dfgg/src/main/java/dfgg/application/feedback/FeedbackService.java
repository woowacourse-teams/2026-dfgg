package dfgg.application.feedback;

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
        Feedback feedback = Feedback.create(request.date(), request.content());
        Feedback saveFeedback = feedbackRepository.save(feedback);
        return FeedbackResponse.from(saveFeedback);
    }
}
