package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.counter.ChampionCounter;
import dfgg.domain.counter.ChampionCounterRepository;
import java.util.List;
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
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(statements = "DELETE FROM champion_counters")
class ChampionCounterRepositoryTest {

    private static final String INSERT_COUNTER =
            "INSERT INTO champion_counters (enemy_champion_id, position, counter_champion_id, rank) VALUES (?, ?, ?, ?)";
    private static final int AHRI = 103;
    private static final int MALZAHAR = 90;
    private static final int LISSANDRA = 127;
    private static final int ANNIE = 1;
    private static final int ZED = 238;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChampionCounterRepository counterRepository;

    @Test
    @Sql("/sql/champion-counter-test-data.sql")
    @DisplayName("적·라인의 카운터를 넣은 순서가 아니라 rank 오름차순으로 돌려준다")
    void findByEnemiesAndPosition_WhenInsertedOutOfOrder_ReturnByRank() {
        // given: (적, 라인, rank) 유니크 인덱스를 타면 ORDER BY 없이도 rank 순으로 나온다.
        //        인덱스를 끄고 입력 순서(3·1·2위)대로 읽혀도 정렬되는지 본다. SET LOCAL은 이 테스트 트랜잭션에만 걸린다.
        jdbcTemplate.execute("SET LOCAL enable_indexscan = off");
        jdbcTemplate.execute("SET LOCAL enable_indexonlyscan = off");
        jdbcTemplate.execute("SET LOCAL enable_bitmapscan = off");

        // when
        List<ChampionCounter> counters =
                counterRepository.findByEnemiesAndPosition(List.of(AHRI), ChampionPosition.MID);

        // then
        assertThat(counters)
                .extracting(ChampionCounter::getRank, ChampionCounter::getCounterChampionId)
                .containsExactly(tuple(1, MALZAHAR), tuple(2, ANNIE), tuple(3, LISSANDRA));
    }

    @Test
    @Sql("/sql/champion-counter-test-data.sql")
    @DisplayName("다른 라인이나 다른 적의 카운터는 섞이지 않는다")
    void findByEnemiesAndPosition_WhenOtherPositionOrEnemyExists_ExcludeThem() {
        // when
        List<ChampionCounter> counters =
                counterRepository.findByEnemiesAndPosition(List.of(AHRI), ChampionPosition.MID);

        // then
        assertThat(counters)
                .allSatisfy(counter -> {
                    assertThat(counter.getEnemyChampionId()).isEqualTo(AHRI);
                    assertThat(counter.getPosition()).isEqualTo(ChampionPosition.MID);
                })
                .hasSize(3);
    }

    @Test
    @Sql("/sql/champion-counter-test-data.sql")
    @DisplayName("넣어 둔 데이터가 없는 적·라인이면 빈 목록이다")
    void findByEnemiesAndPosition_WhenNoData_ReturnEmpty() {
        // when
        List<ChampionCounter> counters =
                counterRepository.findByEnemiesAndPosition(List.of(ZED), ChampionPosition.TOP);

        // then
        assertThat(counters).isEmpty();
    }

    @Test
    @Sql("/sql/champion-counter-test-data.sql")
    @DisplayName("여러 적의 카운터를 한 번에 읽고, 적마다 rank 순으로 묶어 돌려준다")
    void findByEnemiesAndPosition_WhenSeveralEnemies_ReturnAllGroupedByEnemyInRankOrder() {
        // when
        List<ChampionCounter> counters =
                counterRepository.findByEnemiesAndPosition(List.of(ZED, AHRI), ChampionPosition.MID);

        // then
        assertThat(counters)
                .extracting(ChampionCounter::getEnemyChampionId, ChampionCounter::getRank)
                .containsExactly(tuple(AHRI, 1), tuple(AHRI, 2), tuple(AHRI, 3), tuple(ZED, 1));
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
