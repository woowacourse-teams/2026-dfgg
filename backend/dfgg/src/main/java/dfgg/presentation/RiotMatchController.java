package dfgg.presentation;

import dfgg.application.RiotMatchSyncService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class RiotMatchController {

    private final RiotMatchSyncService riotMatchSyncService;

    public RiotMatchController(RiotMatchSyncService riotMatchSyncService) {
        this.riotMatchSyncService = riotMatchSyncService;
    }

    @PostMapping("/riot/matches")
    public ResponseEntity<Void> syncMatches(
            @RequestParam(defaultValue = "0") @PositiveOrZero int playerPage,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int playerCount,
            @RequestParam(defaultValue = "0") @PositiveOrZero int start,
            @RequestParam(defaultValue = "1") @Min(1) @Max(100) int count
    ) {
        riotMatchSyncService.syncMatches(playerPage, playerCount, start, count);
        return ResponseEntity.noContent().build();
    }
}
