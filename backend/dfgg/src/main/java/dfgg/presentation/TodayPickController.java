package dfgg.presentation;

import dfgg.application.propick.TodayPickService;
import dfgg.domain.language.Language;
import dfgg.presentation.dto.request.TodayPickRequest;
import dfgg.presentation.dto.response.TodayPickResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/today-picks")
public class TodayPickController {

    private final TodayPickService todayPickService;

    public TodayPickController(TodayPickService todayPickService) {
        this.todayPickService = todayPickService;
    }

    @PostMapping
    public ResponseEntity<TodayPickResponse> todayPicks(
            @Valid @RequestBody TodayPickRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return ResponseEntity.ok(todayPickService.recommend(request.position(),
                Language.fromAcceptLanguage(acceptLanguage)));
    }
}
