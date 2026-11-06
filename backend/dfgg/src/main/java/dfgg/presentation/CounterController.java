package dfgg.presentation;

import dfgg.application.counter.CounterService;
import dfgg.domain.language.Language;
import dfgg.presentation.dto.request.CounterRequest;
import dfgg.presentation.dto.response.CounterResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/counters")
public class CounterController {

    private final CounterService counterService;

    public CounterController(CounterService counterService) {
        this.counterService = counterService;
    }

    @PostMapping
    public ResponseEntity<CounterResponse> recommend(
            @Valid @RequestBody CounterRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        CounterResponse response = counterService.recommend(request, Language.fromAcceptLanguage(acceptLanguage));
        return ResponseEntity.ok(response);
    }
}
