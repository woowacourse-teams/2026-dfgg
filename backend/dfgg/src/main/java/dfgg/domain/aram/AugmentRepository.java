package dfgg.domain.aram;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AugmentRepository extends JpaRepository<Augment, Long> {

    /** 저장된 패치 목록. 조회 기준 패치는 이 중 최고 패치다. */
    @Query("SELECT DISTINCT augment.patch FROM Augment augment")
    List<String> findDistinctPatches();
}
