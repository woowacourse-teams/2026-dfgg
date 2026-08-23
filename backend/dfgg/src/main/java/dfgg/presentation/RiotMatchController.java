package dfgg.presentation;

import dfgg.application.RiotCollectionOrchestrator;
import dfgg.application.match.RiotMatchSyncService;
import dfgg.application.stats.ChampionBuildStatsRebuildMatchService;
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
    private final RiotCollectionOrchestrator collectionOrchestrator;
    private final ChampionBuildStatsRebuildMatchService statsRebuildService;

    public RiotMatchController(
            RiotMatchSyncService riotMatchSyncService,
            RiotCollectionOrchestrator collectionOrchestrator,
            ChampionBuildStatsRebuildMatchService statsRebuildService
    ) {
        this.riotMatchSyncService = riotMatchSyncService;
        this.collectionOrchestrator = collectionOrchestrator;
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
    public ResponseEntity<Void> rebuildStats(
            @RequestParam
            @Pattern(regexp = "IRON|BRONZE|SILVER|GOLD|PLATINUM|EMERALD|DIAMOND")
            String tier
    ) {
        // 신규 매치는 정규화 객체를 바로 집계하고, 과거 미완료 데이터는 DB에서 찾아 복구한다.
        try {
            collectionOrchestrator.normalizeAndAggregatePendingMatches(tier);
        } finally {
            // 신규 처리에서 실패해도 기존 정규화 데이터의 복구·백필은 실행한다.
            statsRebuildService.rebuildAll(tier);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/riot/matches/{matchId}/stats/replay")
    public ResponseEntity<Void> replayStats(
            @PathVariable String matchId,
            @RequestParam
            @Pattern(regexp = "IRON|BRONZE|SILVER|GOLD|PLATINUM|EMERALD|DIAMOND")
            String tier
    ) {
        statsRebuildService.replayOne(matchId, tier);
        return ResponseEntity.noContent().build();
    }
}
