package dfgg.application.aram;

import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.aram.Augment;
import dfgg.domain.aram.AugmentCatalog;
import dfgg.domain.image.ImageKeys;
import dfgg.infrastructure.external.client.CommunityDragonClient;
import dfgg.infrastructure.external.dto.CommunityDragonAugments;
import dfgg.infrastructure.external.dto.CommunityDragonDescriptions;
import dfgg.infrastructure.storage.S3ImageStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * 픽률 데이터의 증강 ID에 이름, 등급, 요약 설명을 붙이고 아이콘을 S3에 올린다.
 * <p>
 * 이름을 붙이지 못했거나 아이콘을 저장하지 못한 증강은 빼고 경고만 남긴다. 많이 빠지면 {@link AugmentCatalog}가 거부한다.
 */
@Service
public class AugmentCatalogService {

    private static final Logger log = LoggerFactory.getLogger(AugmentCatalogService.class);

    /** 순서대로 받으면 아이콘 다운로드만 약 1분이라 nginx 제한 60초에 걸린다. 동시 8개면 약 6초다. */
    private static final int ICON_CONCURRENCY = 8;

    private final CommunityDragonClient communityDragonClient;
    private final S3ImageStorage storage;

    public AugmentCatalogService(CommunityDragonClient communityDragonClient, S3ImageStorage storage) {
        this.communityDragonClient = communityDragonClient;
        this.storage = storage;
    }

    public AugmentCatalog link(String patch, Set<Integer> augmentIds) {
        CommunityDragonAugments source = communityDragonClient.getAugments(patch);
        Optional<CommunityDragonDescriptions> descriptions = fetchDescriptions(patch);
        List<Augment> augments = new ArrayList<>();
        for (int augmentId : augmentIds) {
            source.toAugment(patch, augmentId).ifPresentOrElse(augment -> {
                augment.describe(descriptions.map(found -> found.descriptionOf(augmentId)).orElse(Map.of()));
                augments.add(augment);
            }, () -> log.warn("연결되지 않은 증강을 뺍니다: patch={}, augmentId={}", patch, augmentId));
        }
        AugmentCatalog catalog = AugmentCatalog.of(augments, augmentIds.size());
        AugmentCatalog stored = catalog.excluding(storeIcons(patch, catalog, source));
        log.info("증강 설명: patch={}, described={}/{}", patch,
                stored.augments().stream().filter(Augment::isDescribed).count(), stored.augments().size());
        return stored;
    }

    /** 설명은 부가 정보라서 받지 못해도 이름·등급·아이콘·픽률은 그대로 저장한다. */
    private Optional<CommunityDragonDescriptions> fetchDescriptions(String patch) {
        try {
            return Optional.of(communityDragonClient.getAugmentDescriptions(patch));
        } catch (AugmentDataRejectedException exception) {
            log.warn("증강 설명 없이 저장합니다: patch={}, reason={}", patch, exception.getMessage());
            return Optional.empty();
        }
    }

    /** @return 아이콘을 저장하지 못한 증강 ID */
    private Set<Integer> storeIcons(String patch, AugmentCatalog catalog, CommunityDragonAugments source) {
        Set<Integer> failures = ConcurrentHashMap.newKeySet();
        try (ExecutorService executor = Executors.newFixedThreadPool(ICON_CONCURRENCY)) {
            CompletableFuture<?>[] uploads = catalog.augments().stream()
                    .map(augment -> CompletableFuture.runAsync(
                            () -> storeIcon(patch, augment.augmentId(), source, failures), executor))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(uploads).join();
        }
        return failures;
    }

    private void storeIcon(String patch, int augmentId, CommunityDragonAugments source, Set<Integer> failures) {
        try {
            storage.store(ImageKeys.augment(patch, augmentId),
                    () -> communityDragonClient.getAugmentIcon(patch, source.iconPathOf(augmentId)));
        } catch (AugmentDataRejectedException | SdkException exception) {
            log.warn("아이콘을 저장하지 못한 증강을 뺍니다: patch={}, augmentId={}, reason={}",
                    patch, augmentId, exception.getMessage());
            failures.add(augmentId);
        }
    }
}
