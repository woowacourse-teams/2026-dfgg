package dfgg.domain.champion;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChampionRepository extends JpaRepository<Champion, Long> {

    Optional<Champion> findByRiotKeyIgnoreCase(String riotKey);

    Optional<Champion> findByNameIgnoreCase(String name);
}
