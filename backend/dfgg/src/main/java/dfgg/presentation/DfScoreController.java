package dfgg.presentation;

import dfgg.application.score.DfScoreService;
import dfgg.presentation.dto.request.DfScoreRequest;
import dfgg.presentation.dto.response.DfScoreResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전적 화면의 경기별 DF 점수. 클라이언트가 끝난 경기의 원시 값을 보내면 참가자별 점수와 MVP·ACE를 돌려준다.
 */
@RestController
@RequestMapping("/api/df-score")
public class DfScoreController {

    private final DfScoreService dfScoreService;

    public DfScoreController(DfScoreService dfScoreService) {
        this.dfScoreService = dfScoreService;
    }

    @PostMapping
    public ResponseEntity<DfScoreResponse> score(@Valid @RequestBody DfScoreRequest request) {
        DfScoreResponse response = dfScoreService.score(request);
        return ResponseEntity.ok(response);
    }
}
