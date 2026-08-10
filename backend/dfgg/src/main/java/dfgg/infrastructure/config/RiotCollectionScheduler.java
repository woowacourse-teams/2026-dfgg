package dfgg.infrastructure.config;

import dfgg.application.RiotCollectionOrchestrator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "collection.scheduler", name = "enabled", havingValue = "true")
public class RiotCollectionScheduler {

    private final RiotCollectionOrchestrator orchestrator;

    public RiotCollectionScheduler(RiotCollectionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(
            cron = "${collection.scheduler.cron:0 */5 * * * *}",
            zone = "${collection.scheduler.zone:Asia/Seoul}"
    )
    public void collect() {
        orchestrator.runOnce();
    }
}
