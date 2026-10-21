package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChampionCounterRepositoryTest {

    private static final String INSERT_COUNTER =
            "INSERT INTO champion_counters (enemy_champion_id, position, counter_champion_id, rank) VALUES (?, ?, ?, ?)";
    private static final int AHRI = 103;
    private static final int MALZAHAR = 90;
    private static final int LISSANDRA = 127;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM champion_counters");
    }

    @Test
    @DisplayName("사용자가 넣는 형식의 카운터 행은 저장된다")
    void insert_WhenValidCounterRows_SaveRows() {
        // when
        jdbcTemplate.update(INSERT_COUNTER, AHRI, "MID", MALZAHAR, 1);
        jdbcTemplate.update(INSERT_COUNTER, AHRI, "MID", LISSANDRA, 2);

        // then
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM champion_counters", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("같은 적·라인에 같은 카운터를 두 번 넣으면 거부한다")
    void insert_WhenSameCounterForSameEnemyAndPosition_Reject() {
        // given
        jdbcTemplate.update(INSERT_COUNTER, AHRI, "MID", MALZAHAR, 1);

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_COUNTER, AHRI, "MID", MALZAHAR, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 적·라인에 같은 순위를 두 번 넣으면 거부한다")
    void insert_WhenSameRankForSameEnemyAndPosition_Reject() {
        // given
        jdbcTemplate.update(INSERT_COUNTER, AHRI, "MID", MALZAHAR, 1);

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_COUNTER, AHRI, "MID", LISSANDRA, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("라인이 다르면 같은 카운터·같은 순위를 넣을 수 있다")
    void insert_WhenDifferentPosition_AllowSameCounterAndRank() {
        // given
        jdbcTemplate.update(INSERT_COUNTER, AHRI, "MID", MALZAHAR, 1);

        // when
        jdbcTemplate.update(INSERT_COUNTER, AHRI, "TOP", MALZAHAR, 1);

        // then
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM champion_counters", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("적과 카운터가 같은 챔피언이면 거부한다")
    void insert_WhenCounterIsEnemyItself_Reject() {
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_COUNTER, AHRI, "MID", AHRI, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MIDDLE", "UTILITY", "mid", ""})
    @DisplayName("다섯 라인 이름이 아니면 거부한다 — 수집 데이터 표기(MIDDLE·UTILITY)도 받지 않는다")
    void insert_WhenPositionNotOneOfFive_Reject(String position) {
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_COUNTER, AHRI, position, MALZAHAR, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8})
    @DisplayName("순위가 1~7을 벗어나면 거부한다")
    void insert_WhenRankOutOfOneToSeven_Reject(int rank) {
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_COUNTER, AHRI, "MID", MALZAHAR, rank))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
