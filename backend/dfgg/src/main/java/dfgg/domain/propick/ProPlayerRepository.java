package dfgg.domain.propick;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProPlayerRepository extends JpaRepository<ProPlayer, Long> {

    List<ProPlayer> findByLeaguepediaPageIn(Collection<String> leaguepediaPages);
}
