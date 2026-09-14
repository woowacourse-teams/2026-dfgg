package dfgg.common.handler;

import dfgg.common.exception.ChampionNotFoundException;
import dfgg.common.exception.InvalidRecommendationRequestException;
import dfgg.common.exception.NextItemRecommendationNotFoundException;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({ChampionNotFoundException.class, InvalidRecommendationRequestException.class})
    public ProblemDetail handleBadRequest(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(NextItemRecommendationNotFoundException.class)
    public ProblemDetail handleNotFound(NextItemRecommendationNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = exception.getBody();
        problem.setDetail(fieldErrorMessages(exception));
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    /**
     * 순서를 고정한다. 검증기의 순회 순서가 새어 나오면 같은 요청에 다른 본문이 나간다.
     */
    private String fieldErrorMessages(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
