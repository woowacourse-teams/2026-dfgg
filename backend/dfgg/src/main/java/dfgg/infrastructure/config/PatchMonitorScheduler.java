package dfgg.infrastructure.config;

import dfgg.application.patch.PatchMonitor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 감지 시점: 기동 직후, 매일 12:00 KST. 감지했지만 전환하지 못했으면 10분마다 다시 확인한다.
 */
public class PatchMonitorScheduler {

    private final PatchMonitor monitor;

    public PatchMonitorScheduler(PatchMonitor monitor) {
        this.monitor = monitor;
    }

    /** readiness 전에 돈다. 그림이 이미 있으면 새 슬롯이 처음부터 새 패치로 응답한다. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        monitor.detect();
    }

    @Scheduled(cron = "${patch.monitor.cron:0 0 12 * * *}", zone = "Asia/Seoul")
    public void daily() {
        monitor.check();
    }

    @Scheduled(fixedDelayString = "${patch.monitor.retry-delay:10m}", initialDelayString = "${patch.monitor.retry-delay:10m}")
    public void retry() {
        monitor.retryPending();
    }
}
