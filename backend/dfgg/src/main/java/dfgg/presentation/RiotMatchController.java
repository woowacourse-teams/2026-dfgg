package dfgg.presentation;

import dfgg.application.RiotMatchSyncService;
import dfgg.application.ChampionBuildStatsRebuildService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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
    private final ChampionBuildStatsRebuildService statsRebuildService;

    public RiotMatchController(RiotMatchSyncService riotMatchSyncService) {
        this(riotMatchSyncService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RiotMatchController(
            RiotMatchSyncService riotMatchSyncService,
            ChampionBuildStatsRebuildService statsRebuildService
    ) {
        this.riotMatchSyncService = riotMatchSyncService;
        this.statsRebuildService = statsRebuildService;
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

    @PostMapping("/riot/matches/timelines")
    public ResponseEntity<Void> syncMissingTimelines() {
        riotMatchSyncService.syncMissingTimelines();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/riot/matches/stats")
    public ResponseEntity<Void> rebuildStats(
            @RequestParam
            @Pattern(regexp = "IRON|BRONZE|SILVER|GOLD|PLATINUM|EMERALD|DIAMOND")
            String tier
    ) {
        if (statsRebuildService == null) {
            throw new IllegalStateException("stats rebuild service is not configured");
        }
        statsRebuildService.rebuildAll(tier);
        return ResponseEntity.noContent().build();
    }
}
