package dfgg.domain.aram;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChampionAugmentRepository extends JpaRepository<ChampionAugment, Long> {

    List<ChampionAugment> findByPatchAndChampionId(String patch, int championId);
}
