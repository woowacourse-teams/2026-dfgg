package dfgg.domain.rune;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.language.Language;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class RuneRepositoryTest {

    @Autowired
    private RuneRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("룬 이름을 언어별로 저장하고 요청한 언어로 읽는다")
    void save_RestoresNameByLanguage() {
        // given
        repository.save(new Rune(8437, RuneKind.PERK, Map.of("ko-KR", "착취의 손아귀", "en-US", "Grasp of the Undying")));
        entityManager.flush();
        entityManager.clear();

        // when
        Rune rune = repository.findById(8437).orElseThrow();

        // then
        assertThat(rune.kind()).isEqualTo(RuneKind.PERK);
        assertThat(rune.nameIn(Language.KO_KR)).isEqualTo("착취의 손아귀");
        assertThat(rune.nameIn(Language.EN_US)).isEqualTo("Grasp of the Undying");
    }
}
