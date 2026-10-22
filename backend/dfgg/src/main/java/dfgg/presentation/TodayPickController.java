package dfgg.presentation;

import dfgg.application.propick.TodayPickService;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.language.Language;
import dfgg.presentation.dto.response.TodayPickResponse;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/today-picks")
public class TodayPickController {

    private final TodayPickService todayPickService;

    public TodayPickController(TodayPickService todayPickService) {
        this.todayPickService = todayPickService;
    }

    /** {@code bans}는 쉼표로 구분하고 중복을 허용한다 (예: {@code bans=157,238}). */
    @GetMapping
    public ResponseEntity<TodayPickResponse> todayPicks(
            @RequestParam ChampionPosition position,
            @RequestParam(defaultValue = "") List<Integer> bans,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return ResponseEntity.ok(
                todayPickService.recommend(position, bans, Language.fromAcceptLanguage(acceptLanguage)));
    }
}
