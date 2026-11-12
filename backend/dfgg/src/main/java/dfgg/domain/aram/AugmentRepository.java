package dfgg.domain.aram;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AugmentRepository extends JpaRepository<Augment, Long> {

    /** 저장된 패치 목록. 조회 기준 패치는 이 중 최고 패치다. */
    @Query("SELECT DISTINCT augment.patch FROM Augment augment")
    List<String> findDistinctPatches();

    List<Augment> findByPatch(String patch);

    /** 픽률은 외래 키의 {@code ON DELETE CASCADE}로 함께 지워진다. */
    @Modifying
    @Query("DELETE FROM Augment augment WHERE augment.patch = :patch")
    int deleteByPatch(@Param("patch") String patch);
}
