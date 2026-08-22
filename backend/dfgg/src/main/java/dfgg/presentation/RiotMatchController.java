package dfgg.presentation;

import dfgg.application.ChampionBuildStatsRebuildResult;
import dfgg.application.ChampionBuildStatsRebuildService;
import dfgg.application.match.RiotMatchSyncService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestParam List<@NotBlank String> puuids,
            @RequestParam(defaultValue = "0") @PositiveOrZero int start,
            @RequestParam(defaultValue = "1") @Min(1) @Max(100) int count
    ) {
        riotMatchSyncService.syncMatches(puuids, start, count);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/riot/matches/timelines")
    public ResponseEntity<Void> syncMissingTimelines() {
        riotMatchSyncService.syncMissingTimelines();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/riot/matches/stats")
    public ResponseEntity<ChampionBuildStatsRebuildResult> rebuildStats(
            @RequestParam
            @Pattern(regexp = "IRON|BRONZE|SILVER|GOLD|PLATINUM|EMERALD|DIAMOND")
            String tier
    ) {
        if (statsRebuildService == null) {
            throw new IllegalStateException("stats rebuild service is not configured");
        }
        ChampionBuildStatsRebuildResult result = statsRebuildService.rebuildAll(tier);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/riot/matches/{matchId}/stats/replay")
    public ResponseEntity<ChampionBuildStatsRebuildResult> replayStats(
            @PathVariable String matchId,
            @RequestParam
            @Pattern(regexp = "IRON|BRONZE|SILVER|GOLD|PLATINUM|EMERALD|DIAMOND")
            String tier
    ) {
        if (statsRebuildService == null) {
            throw new IllegalStateException("stats rebuild service is not configured");
        }
        return ResponseEntity.ok(statsRebuildService.replayOne(matchId, tier));
    }
}
