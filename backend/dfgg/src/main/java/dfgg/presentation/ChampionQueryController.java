package dfgg.presentation;

import dfgg.application.champion.ChampionQueryService;
import dfgg.presentation.dto.response.ChampionsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 클라이언트용 챔피언 조회. 동기화({@code POST /admin/champions})와 나눈다 — 운영에서 {@code /admin}을 막아도 이 경로는 열려 있어야 한다.
 */
@RestController
@RequestMapping("/api/champions")
public class ChampionQueryController {

    private final ChampionQueryService championQueryService;

    public ChampionQueryController(ChampionQueryService championQueryService) {
        this.championQueryService = championQueryService;
    }

    @GetMapping
    public ResponseEntity<ChampionsResponse> getChampions() {
        ChampionsResponse response = championQueryService.findAll();

        return ResponseEntity.ok(response);
    }
}
