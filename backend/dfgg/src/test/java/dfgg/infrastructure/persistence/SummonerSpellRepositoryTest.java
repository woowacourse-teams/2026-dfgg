package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.domain.spell.SummonerSpell;
import dfgg.domain.spell.SummonerSpellRepository;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SummonerSpellRepositoryTest {

    private static final String INSERT_SPELL =
            "INSERT INTO summoner_spells (spell_id, riot_key, name, icon_key) VALUES (?, ?, '{}'::jsonb, 'icon.png')";
    private static final int FLASH = 4;

    @Autowired
    private SummonerSpellRepository spellRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("소환사 주문을 저장하고 영문 키·이름·아이콘과 함께 다시 읽는다")
    void save_WhenSpell_ReadBackWithKeyNameAndIcon() {
        // given
        Map<String, String> name = Map.of("ko-KR", "점멸", "en-US", "Flash");
        spellRepository.save(new SummonerSpell(FLASH, "SummonerFlash", name, "SummonerFlash.png"));
        entityManager.flush();
        entityManager.clear();

        // when
        SummonerSpell flash = spellRepository.findById(FLASH).orElseThrow();

        // then
        assertThat(flash.getRiotKey()).isEqualTo("SummonerFlash");
        assertThat(flash.getName()).isEqualTo(name);
        assertThat(flash.getIconKey()).isEqualTo("SummonerFlash.png");
    }

    @Test
    @DisplayName("같은 영문 키를 가진 주문이 두 번 들어오면 거부한다")
    void insert_WhenRiotKeyDuplicated_Reject() {
        // given
        jdbcTemplate.update(INSERT_SPELL, FLASH, "SummonerFlash");

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SPELL, 99, "SummonerFlash"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
