package dfgg.presentation;

import dfgg.application.aram.AramMayhemQueryService;
import dfgg.application.aram.AugmentTitleService;
import dfgg.domain.language.Language;
import dfgg.presentation.dto.request.AugmentLookupRequest;
import dfgg.presentation.dto.response.AugmentLookupResponse;
import dfgg.presentation.dto.response.AugmentTitlesResponse;
import dfgg.presentation.dto.response.ChampionAugmentsResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aram-mayhem")
public class AramMayhemController {

    /** 같은 패치·언어의 견본은 바뀌지 않으므로 하루 동안 다시 묻지 않아도 된다. */
    private static final Duration TITLES_MAX_AGE = Duration.ofDays(1);

    private final AramMayhemQueryService aramMayhemQueryService;
    private final AugmentTitleService augmentTitleService;

    public AramMayhemController(AramMayhemQueryService aramMayhemQueryService,
                                AugmentTitleService augmentTitleService) {
        this.aramMayhemQueryService = aramMayhemQueryService;
        this.augmentTitleService = augmentTitleService;
    }

    /**
     * 언어는 다른 API와 같은 {@code Accept-Language} 규칙으로 고른다. 앱은 게임 클라이언트 언어를 이 헤더에 담는다.
     * <p>
     * 같은 주소라도 헤더에 따라 응답이 달라지므로 {@code Vary}로 알린다. 그래야 중간 캐시가 다른 언어 견본을 내주지 않는다.
     * {@code If-None-Match}가 ETag와 같으면 Spring이 본문 없이 304로 바꾼다.
     */
    @GetMapping("/augment-titles")
    public ResponseEntity<AugmentTitlesResponse> getTitles(
            @RequestParam(required = false) String patch,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        AugmentTitlesResponse response = augmentTitleService.findTitles(
                patch, Language.fromAcceptLanguage(acceptLanguage));

        return ResponseEntity.ok()
                .eTag(response.etag())
                .cacheControl(titlesCacheControl(patch))
                .varyBy(HttpHeaders.ACCEPT_LANGUAGE)
                .body(response);
    }

    /** 패치를 생략하면 최신 패치가 바뀔 수 있어 매번 ETag로 다시 확인하게 한다. */
    private static CacheControl titlesCacheControl(String patch) {
        if (patch == null) {
            return CacheControl.noCache();
        }
        return CacheControl.maxAge(TITLES_MAX_AGE).cachePublic();
    }

    @GetMapping("/champions/{championId}/augments")
    public ResponseEntity<ChampionAugmentsResponse> getAugments(
            @PathVariable int championId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        ChampionAugmentsResponse response = aramMayhemQueryService.findByChampion(
                championId, Language.fromAcceptLanguage(acceptLanguage));

        return ResponseEntity.ok(response);
    }

    /** 이름에 특수문자가 있어 본문으로 받는다. 조회이지만 다른 조회 API처럼 POST다. */
    @PostMapping("/champions/{championId}/augments/lookup")
    public ResponseEntity<AugmentLookupResponse> lookup(
            @PathVariable int championId,
            @Valid @RequestBody AugmentLookupRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        AugmentLookupResponse response = aramMayhemQueryService.lookup(
                championId, request.names(), Language.fromAcceptLanguage(acceptLanguage));

        return ResponseEntity.ok(response);
    }
}
