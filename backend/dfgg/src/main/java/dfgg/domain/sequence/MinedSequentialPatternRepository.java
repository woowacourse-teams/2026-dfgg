package dfgg.domain.sequence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MinedSequentialPatternRepository extends JpaRepository<MinedSequentialPattern, Long> {

    void deleteByAlgorithmVersion(String algorithmVersion);
}
