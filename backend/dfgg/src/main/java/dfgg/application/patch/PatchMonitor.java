package dfgg.application.patch;

import dfgg.domain.match.PatchVersion;
import dfgg.infrastructure.external.client.DataDragonClient;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data Dragon의 새 패치를 감지해 서비스 패치를 바꾼다. 재기동하지 않고 메모리 값만 바꾼다.
 * 올리는 서버는 그림이 없으면 동기화한 뒤, 받는 서버는 S3에 완료 표시가 생기면 전환한다.
 * 어느 단계가 실패해도 기존 패치를 유지하고 다음 확인에서 다시 시도한다.
 */
public class PatchMonitor {

    private static final Logger log = LoggerFactory.getLogger(PatchMonitor.class);

    private final DataDragonClient dataDragon;
    private final ServicePatch servicePatch;
    private final PatchImageChecker images;
    private final Optional<PatchCatalogSync> catalogSync;

    private volatile PatchVersion pending;

    public PatchMonitor(DataDragonClient dataDragon, ServicePatch servicePatch, PatchImageChecker images,
                        Optional<PatchCatalogSync> catalogSync, MeterRegistry meters) {
        this.dataDragon = dataDragon;
        this.servicePatch = servicePatch;
        this.images = images;
        this.catalogSync = catalogSync;
        Gauge.builder("dfgg.patch.pending", this, PatchMonitor::pendingValue)
                .description("새 패치를 감지했지만 아직 전환하지 않았으면 1")
                .register(meters);
    }

    /** 기동 직후. 그림이 이미 있으면 전환하고, 동기화는 하지 않는다(기동을 붙잡지 않게). */
    public void detect() {
        latest().ifPresent(patch -> advance(patch, false));
    }

    /** 정기 확인. 올리는 서버는 필요하면 동기화한다. */
    public void check() {
        latest().ifPresent(patch -> advance(patch, true));
    }

    /** 감지했지만 아직 전환하지 못한 패치만 다시 확인한다. */
    public void retryPending() {
        PatchVersion patch = pending;
        if (patch == null) {
            return;
        }
        advance(patch, true);
    }

    private Optional<PatchVersion> latest() {
        try {
            return Optional.of(PatchVersion.of(dataDragon.getLatestVersion()));
        } catch (RuntimeException exception) {
            log.warn("Data Dragon 최신 패치 조회 실패. 서비스 패치 {}를 유지합니다.", servicePatch.value(), exception);
            return Optional.empty();
        }
    }

    private void advance(PatchVersion patch, boolean allowSync) {
        if (patch.compareTo(servicePatch.current()) <= 0) {
            pending = null;
            return;
        }
        pending = patch;
        if (!imagesReady(patch, allowSync)) {
            return;
        }
        if (servicePatch.advanceTo(patch)) {
            log.info("서비스 패치를 {}로 전환했습니다.", patch.value());
        }
        pending = null;
    }

    private boolean imagesReady(PatchVersion patch, boolean allowSync) {
        try {
            if (allowSync) {
                syncIfMissing(patch);
            }
            boolean ready = images.isReady(patch);
            if (!ready) {
                log.info("패치 {} 그림이 아직 준비되지 않아 서비스 패치 {}를 유지합니다.", patch.value(), servicePatch.value());
            }
            return ready;
        } catch (RuntimeException exception) {
            log.warn("패치 {} 준비 실패. 서비스 패치 {}를 유지하고 다음 확인에서 다시 시도합니다.",
                    patch.value(), servicePatch.value(), exception);
            return false;
        }
    }

    private void syncIfMissing(PatchVersion patch) {
        if (catalogSync.isEmpty() || images.isReady(patch)) {
            return;
        }
        log.info("패치 {} 카탈로그와 그림을 동기화합니다.", patch.value());
        catalogSync.get().syncFor(patch);
    }

    private double pendingValue() {
        if (pending == null) {
            return 0;
        }
        return 1;
    }
}
