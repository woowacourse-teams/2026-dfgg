package dfgg.presentation;

import dfgg.application.player.RiotPlayerSyncService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class RiotPlayerController {

    private final RiotPlayerSyncService riotPlayerSyncService;

    public RiotPlayerController(RiotPlayerSyncService riotPlayerSyncService) {
        this.riotPlayerSyncService = riotPlayerSyncService;
    }

    @PostMapping("/riot/players")
    public ResponseEntity<Void> syncPlayers(
            @RequestParam(defaultValue = "RANKED_SOLO_5x5")
            @Pattern(regexp = "RANKED_SOLO_5x5|RANKED_FLEX_SR") String queue,
            @RequestParam(defaultValue = "PLATINUM")
            @Pattern(regexp = "IRON|BRONZE|SILVER|GOLD|PLATINUM|EMERALD|DIAMOND|MASTER") String tier,
            @RequestParam(defaultValue = "I")
            @Pattern(regexp = "I|II|III|IV") String division,
            @RequestParam(defaultValue = "1") @Positive int page
    ) {
        riotPlayerSyncService.syncLeagueEntries(queue, tier, division, page);
        return ResponseEntity.noContent().build();
    }
}
