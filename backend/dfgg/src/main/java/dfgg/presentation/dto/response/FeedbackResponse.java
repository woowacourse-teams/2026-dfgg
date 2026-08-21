package dfgg.presentation.dto.response;

import java.time.LocalDate;

public record FeedbackResponse(
        LocalDate date,
        String content
) {
}
