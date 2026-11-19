package dfgg.application.feedback;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dfgg.domain.feedback.Feedback;
import dfgg.domain.feedback.FeedbackRepository;
import dfgg.presentation.dto.request.FeedbackRequest;
import dfgg.presentation.dto.response.FeedbackResponse;
import java.time.LocalDate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @InjectMocks
    private FeedbackService feedbackService;

    @Test
    void 피드백을_저장하고_저장한_내용을_반환한다() {
        // given
        LocalDate date = LocalDate.of(2026, 8, 21);
        FeedbackRequest request = new FeedbackRequest(date, "추천 결과가 유용했습니다.");
        Feedback savedFeedback = mock(Feedback.class);
        given(savedFeedback.getId()).willReturn(1L);
        given(savedFeedback.getDate()).willReturn(date);
        given(savedFeedback.getContent()).willReturn("추천 결과가 유용했습니다.");

        given(feedbackRepository.save(any(Feedback.class)))
                .willReturn(savedFeedback);
        // when
        FeedbackResponse response = feedbackService.save(request);

        // then
        Assertions.assertThat(response)
                .isEqualTo(new FeedbackResponse(1L, date, "추천 결과가 유용했습니다."));

        ArgumentCaptor<Feedback> feedbackCaptor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(feedbackCaptor.capture());
        Feedback feedback = feedbackCaptor.getValue();
        Assertions.assertThat(feedback.getDate()).isEqualTo(date);
        Assertions.assertThat(feedback.getContent()).isEqualTo("추천 결과가 유용했습니다.");
    }
}
