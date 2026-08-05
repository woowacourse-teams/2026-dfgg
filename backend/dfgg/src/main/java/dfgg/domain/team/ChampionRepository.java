package dfgg.domain.team;

import dfgg.domain.champion.Champion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChampionRepository extends JpaRepository<Champion, Long> {

    Optional<Champion> findByRiotKeyIgnoreCase(String riotKey);

    Optional<Champion> findByNameKo(String nameKo);

    Optional<Champion> findByNameEnIgnoreCase(String nameEn);
}
