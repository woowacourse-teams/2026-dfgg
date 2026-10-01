package dfgg.presentation.dto.response;

import dfgg.domain.feedback.Feedback;
import java.time.LocalDate;

public record FeedbackResponse(
        Long id,
        LocalDate date,
        String content
) {
    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(
                feedback.getId(),
                feedback.getDate(),
                feedback.getContent()
        );
    }
}
