package dfgg.domain.champion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChampionRepository extends JpaRepository<Champion, Long> {

    Optional<Champion> findByRiotKeyIgnoreCase(String riotKey);

    Optional<Champion> findByNameIgnoreCase(String name);

    /**
     * 태그까지 함께 읽어온다. {@code championTags}는 {@code @ElementCollection}이라 기본이 지연
     * 로딩이고, 트랜잭션 밖에서 읽으면 {@code LazyInitializationException}이 난다.
     * <p>
     * feature 추출은 서빙(트랜잭션 안)과 평가 하네스·학습 데이터 export(트랜잭션 밖)에서
     * 모두 돌기 때문에, 호출자의 트랜잭션 여부에 기대지 않고 여기서 미리 채운다.
     */
    @Query("""
            SELECT DISTINCT champion
            FROM Champion champion
            LEFT JOIN FETCH champion.championTags
            WHERE champion.championId IN :championIds
            """)
    List<Champion> findAllWithTagsByChampionIdIn(@Param("championIds") Collection<Long> championIds);
}
