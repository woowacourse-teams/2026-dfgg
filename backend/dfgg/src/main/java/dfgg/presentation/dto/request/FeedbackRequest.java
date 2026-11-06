package dfgg.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record FeedbackRequest(
        @NotNull(message = "작성 날짜는 필수입니다.")
        LocalDate date,

        @NotBlank(message = "작성 내용은 필수입니다.")
        String content
) {
}
