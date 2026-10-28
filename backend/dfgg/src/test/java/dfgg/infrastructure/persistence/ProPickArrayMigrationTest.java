package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProPickArrayMigrationTest {

    private static final String MIGRATION = "db/migration/V21__merge_pro_pick_slots_into_arrays.sql";
    private static final long FULL_PICK = 1L;
    private static final long EMPTY_PICK = 2L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpV20Tables() {
        // V20까지의 대회 픽 테이블 모양만 따로 만든다. 테스트 트랜잭션이 끝나면 스키마째 롤백된다.
        jdbcTemplate.execute("""
                CREATE SCHEMA v21_check;
                SET LOCAL search_path TO v21_check;
                CREATE TABLE pro_picks (id BIGINT PRIMARY KEY, source_game_id VARCHAR(255) NOT NULL);
                CREATE TABLE pro_pick_spells (pick_id BIGINT NOT NULL, slot SMALLINT NOT NULL, spell_id INTEGER NOT NULL,
                                              PRIMARY KEY (pick_id, slot));
                CREATE TABLE pro_pick_items (pick_id BIGINT NOT NULL, slot SMALLINT NOT NULL, item_id BIGINT NOT NULL,
                                             PRIMARY KEY (pick_id, slot));
                CREATE TABLE pro_pick_runes (pick_id BIGINT NOT NULL, slot SMALLINT NOT NULL, rune_id INTEGER NOT NULL,
                                             PRIMARY KEY (pick_id, slot));
                INSERT INTO pro_picks VALUES (1, 'full'), (2, 'empty');
                INSERT INTO pro_pick_spells VALUES (1, 0, 4), (1, 1, 14);
                INSERT INTO pro_pick_items VALUES (1, 0, 3157), (1, 1, 3020), (1, 6, 3364);
                INSERT INTO pro_pick_runes VALUES (1, 0, 8112), (1, 1, 8139), (1, 2, 8138), (1, 3, 8135),
                                                  (1, 4, 8226), (1, 5, 8210), (1, 6, 5008), (1, 7, 5008), (1, 8, 5011);
                """);
    }

    @Test
    @DisplayName("자식 행을 칸 순서대로 배열에 옮긴다 — 배열 위치가 칸 번호이고, 빈 칸은 NULL이다")
    void migrate_WhenChildRowsExist_MoveIntoArraysBySlot() throws IOException {
        // when
        runMigration();

        // then
        assertThat(arrayOf("spell_ids", FULL_PICK)).containsExactly(4L, 14L);
        assertThat(arrayOf("item_ids", FULL_PICK)).containsExactly(3157L, 3020L, null, null, null, null, 3364L);
        assertThat(arrayOf("rune_ids", FULL_PICK))
                .containsExactly(8112L, 8139L, 8138L, 8135L, 8226L, 8210L, 5008L, 5008L, 5011L);
    }

    @Test
    @DisplayName("자식 행이 하나도 없는 픽은 모든 칸이 NULL인 배열이 된다")
    void migrate_WhenNoChildRows_FillNullSlots() throws IOException {
        // when
        runMigration();

        // then
        assertThat(arrayOf("spell_ids", EMPTY_PICK)).hasSize(2).containsOnlyNulls();
        assertThat(arrayOf("item_ids", EMPTY_PICK)).hasSize(7).containsOnlyNulls();
        assertThat(arrayOf("rune_ids", EMPTY_PICK)).hasSize(9).containsOnlyNulls();
    }

    @Test
    @DisplayName("옮긴 뒤 주문·아이템·룬 자식 테이블을 지운다")
    void migrate_WhenMoved_DropChildTables() throws IOException {
        // when
        runMigration();

        // then
        assertThat(jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables WHERE table_schema = 'v21_check' ORDER BY table_name
                """, String.class)).containsExactly("pro_picks");
    }

    private void runMigration() throws IOException {
        jdbcTemplate.execute(new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8));
    }

    private List<Long> arrayOf(String column, long pickId) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM pro_picks WHERE id = ?",
                (rs, rowNum) -> toLongs(rs.getArray(1)), pickId);
    }

    private static List<Long> toLongs(Array array) throws SQLException {
        return Arrays.stream((Object[]) array.getArray())
                .map(ProPickArrayMigrationTest::toLong)
                .toList();
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).longValue();
    }
}
