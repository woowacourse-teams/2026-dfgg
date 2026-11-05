package dfgg.application.aram;

import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.aram.AugmentCatalog;
import dfgg.domain.aram.AugmentRepository;
import dfgg.domain.aram.ChampionAugment;
import dfgg.domain.match.PatchVersion;
import dfgg.infrastructure.external.client.AramhexClient;
import dfgg.infrastructure.external.dto.AramhexPatchData;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 최신 패치의 증강 픽률을 받아 저장한다. 값을 모두 검증한 뒤 아이콘을 올리고, 어느 단계든 실패하면 직전 데이터가 남는다. */
@Service
public class AramMayhemSyncService {

    private static final Logger log = LoggerFactory.getLogger(AramMayhemSyncService.class);

    private final AramhexClient aramhexClient;
    private final AugmentCatalogService augmentCatalogService;
    private final AugmentRepository augmentRepository;
    private final AugmentSnapshotWriter snapshotWriter;

    public AramMayhemSyncService(AramhexClient aramhexClient, AugmentCatalogService augmentCatalogService,
                                 AugmentRepository augmentRepository, AugmentSnapshotWriter snapshotWriter) {
        this.aramhexClient = aramhexClient;
        this.augmentCatalogService = augmentCatalogService;
        this.augmentRepository = augmentRepository;
        this.snapshotWriter = snapshotWriter;
    }

    public void sync() {
        AramhexPatchData data = aramhexClient.getLatestPatchData();
        rejectOlderThanStored(data.patch());
        List<ChampionAugment> calculated = data.toChampionAugments();
        AugmentCatalog catalog = augmentCatalogService.link(data.patch(), data.augmentIds());
        List<ChampionAugment> championAugments = calculated.stream()
                .filter(championAugment -> catalog.contains(championAugment.augmentId()))
                .toList();
        snapshotWriter.replace(data.patch(), catalog, championAugments);
        log.info("증강 데이터 저장: patch={}, augments={}, championAugments={}",
                data.patch(), catalog.augments().size(), championAugments.size());
    }

    private void rejectOlderThanStored(String patch) {
        PatchVersion incoming = PatchVersion.of(patch);
        PatchVersion.latest(augmentRepository.findDistinctPatches())
                .filter(stored -> incoming.compareTo(stored) < 0)
                .ifPresent(stored -> {
                    throw new AugmentDataRejectedException(
                            "저장된 패치보다 낮은 패치입니다: " + patch + " < " + stored.value());
                });
    }
}
