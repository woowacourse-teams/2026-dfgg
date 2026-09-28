package dfgg.common.handler;

import dfgg.common.exception.ChampionNotFoundException;
import dfgg.common.exception.InvalidRecommendationRequestException;
import dfgg.common.exception.NextItemRecommendationNotFoundException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ChampionNotFoundException.class, InvalidRecommendationRequestException.class})
    public ProblemDetail handleBadRequest(RuntimeException exception) {
        log.warn("400 잘못된 요청: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(NextItemRecommendationNotFoundException.class)
    public ProblemDetail handleNotFound(NextItemRecommendationNotFoundException exception) {
        log.warn("404 찾지 못함: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = exception.getBody();
        String reasons = fieldErrorMessages(exception);
        log.warn("400 검증 실패: {}", reasons);
        problem.setDetail(reasons);
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
