package dfgg.application.aram;

import dfgg.domain.aram.AugmentCatalog;
import dfgg.domain.aram.AugmentRepository;
import dfgg.domain.aram.ChampionAugment;
import dfgg.domain.aram.ChampionAugmentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 패치를 통째로 교체한다. 중간에 실패하면 지운 데이터까지 되돌려 직전 데이터로 계속 응답한다.
 * <p>
 * 외부 호출과 아이콘 업로드가 DB 연결을 붙들지 않도록 트랜잭션을 저장에만 건다.
 */
@Service
public class AugmentSnapshotWriter {

    private final AugmentRepository augmentRepository;
    private final ChampionAugmentRepository championAugmentRepository;

    public AugmentSnapshotWriter(AugmentRepository augmentRepository,
                                 ChampionAugmentRepository championAugmentRepository) {
        this.augmentRepository = augmentRepository;
        this.championAugmentRepository = championAugmentRepository;
    }

    @Transactional
    public void replace(String patch, AugmentCatalog catalog, List<ChampionAugment> championAugments) {
        augmentRepository.deleteByPatch(patch);
        augmentRepository.saveAll(catalog.augments());
        championAugmentRepository.saveAll(championAugments);
    }
}
