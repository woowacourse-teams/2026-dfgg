package dfgg.domain.sequence;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinedSequentialPatternRepository extends JpaRepository<MinedSequentialPattern, Long> {

    void deleteByAlgorithmVersion(String algorithmVersion);

    long countByAlgorithmVersion(String algorithmVersion);

    List<MinedSequentialPattern> findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
            String algorithmVersion,
            Long championId,
            ChampionPosition position,
            String tier,
            String patch
    );
}
