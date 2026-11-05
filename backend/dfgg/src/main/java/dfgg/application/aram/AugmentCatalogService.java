package dfgg.application.aram;

import dfgg.domain.aram.Augment;
import dfgg.domain.aram.AugmentCatalog;
import dfgg.domain.image.ImageKeys;
import dfgg.infrastructure.external.client.CommunityDragonClient;
import dfgg.infrastructure.external.dto.CommunityDragonAugments;
import dfgg.infrastructure.storage.S3ImageStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 픽률 데이터의 증강 ID에 이름과 등급을 붙이고 아이콘을 S3에 올린다. 연결되지 않은 증강은 빼고 경고만 남긴다. */
@Service
public class AugmentCatalogService {

    private static final Logger log = LoggerFactory.getLogger(AugmentCatalogService.class);

    private final CommunityDragonClient communityDragonClient;
    private final S3ImageStorage storage;

    public AugmentCatalogService(CommunityDragonClient communityDragonClient, S3ImageStorage storage) {
        this.communityDragonClient = communityDragonClient;
        this.storage = storage;
    }

    public AugmentCatalog link(String patch, Set<Integer> augmentIds) {
        CommunityDragonAugments source = communityDragonClient.getAugments(patch);
        List<Augment> augments = new ArrayList<>();
        for (int augmentId : augmentIds) {
            source.toAugment(patch, augmentId).ifPresentOrElse(augments::add,
                    () -> log.warn("연결되지 않은 증강을 뺍니다: patch={}, augmentId={}", patch, augmentId));
        }
        AugmentCatalog catalog = AugmentCatalog.of(augments);
        catalog.augments().forEach(augment -> storeIcon(patch, augment.augmentId(), source));
        return catalog;
    }

    private void storeIcon(String patch, int augmentId, CommunityDragonAugments source) {
        storage.store(ImageKeys.augment(patch, augmentId),
                () -> communityDragonClient.getAugmentIcon(patch, source.iconPathOf(augmentId)));
    }
}
