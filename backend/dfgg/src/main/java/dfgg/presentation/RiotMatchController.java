package dfgg.presentation;

import dfgg.application.RiotCollectionOrchestrator;
import dfgg.application.match.MatchRenormalizationService;
import dfgg.application.match.RenormalizationResult;
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
    private final MatchRenormalizationService renormalizationService;

    public RiotMatchController(
            RiotMatchSyncService riotMatchSyncService,
            RiotCollectionOrchestrator collectionOrchestrator,
            ChampionBuildStatsRebuildMatchService statsRebuildService,
            MatchRenormalizationService renormalizationService
    ) {
        this.riotMatchSyncService = riotMatchSyncService;
        this.collectionOrchestrator = collectionOrchestrator;
        this.statsRebuildService = statsRebuildService;
        this.renormalizationService = renormalizationService;
    }

    /**
     * 이미 정규화된 매치를 원본에서 다시 정규화한다.
     * 정규화 로직을 고쳤을 때 기존 데이터에 반영하는 유일한 경로다
     * {@code /riot/matches/stats}는 아직 정규화되지 않은 매치만 처리한다.
     * <p>
     * Riot API를 호출하지 않는다(티어 표본 경로 사용).
     * 단일 API 키의 rate limit과 무관하다.
     * <p>
     * {@code limit}은 필수다. 기본값을 두면 실수로 전량을 한 번에 돌리게 되는데, 운영 DB에
     * 그건 되돌리기 어려운 작업이다. 응답의 {@code nextCursor}를 {@code afterMatchId}로 넘겨
     * 이어서 돌린다.
     */
    @PostMapping("/riot/matches/renormalize")
    public ResponseEntity<RenormalizationResult> renormalize(
            @RequestParam
            @Pattern(regexp = "IRON|BRONZE|SILVER|GOLD|PLATINUM|EMERALD|DIAMOND|MASTER|GRANDMASTER|CHALLENGER")
            String tier,
            @RequestParam(defaultValue = "") String afterMatchId,
            @RequestParam @Min(1) @Max(1000) int limit
    ) {
        return ResponseEntity.ok(renormalizationService.renormalize(tier, afterMatchId, limit));
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
            @Pattern(regexp = "IRON|BRONZE|SILVER|GOLD|PLATINUM|EMERALD|DIAMOND|MASTER|GRANDMASTER|CHALLENGER")
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
            @Pattern(regexp = "IRON|BRONZE|SILVER|GOLD|PLATINUM|EMERALD|DIAMOND|MASTER|GRANDMASTER|CHALLENGER")
            String tier
    ) {
        statsRebuildService.replayOne(matchId, tier);
        return ResponseEntity.noContent().build();
    }
}
