package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
@Sql("/sql/pro-pick-schema-test-data.sql")
class ProPickSchemaTest {

    private static final String INSERT_PICK = """
            INSERT INTO pro_picks (tournament_id, player_id, source_game_id, played_at, team, side, position,
                                   champion_id, win, spell_ids, item_ids, rune_ids)
            VALUES (1, ?, ?, '2026-10-25 08:00:00+00', 'T1', ?, ?, 9001, true, ?::integer[], ?::bigint[], ?::integer[])
            RETURNING id
            """;
    private static final String GAME = "2026 Season World Championship/Main Event_Round 1_1_1";
    private static final long FAKER = 1L;
    private static final long KRATOS = 2L;
    private static final String SPELLS = "{9001,NULL}";
    private static final String ITEMS = "{9001,NULL,NULL,NULL,NULL,NULL,9001}";
    private static final String RUNES = "{9001,NULL,NULL,NULL,NULL,NULL,NULL,NULL,9001}";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("대회·선수·챔피언이 있고 주문 2칸·아이템 7칸·룬 9칸 배열인 픽은 저장된다 — 빈 칸은 NULL")
    void insert_WhenValidPickWithSlotArrays_SaveRow() {
        // when
        insertPick(FAKER, GAME, "BLUE", "MID");

        // then
        assertThat(countOf("pro_picks")).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{9001}|{9001,NULL,NULL,NULL,NULL,NULL,9001}|{9001,NULL,NULL,NULL,NULL,NULL,NULL,NULL,9001}",
            "{9001,9001,9001}|{9001,NULL,NULL,NULL,NULL,NULL,9001}|{9001,NULL,NULL,NULL,NULL,NULL,NULL,NULL,9001}",
            "{9001,NULL}|{9001,NULL,NULL,NULL,NULL,9001}|{9001,NULL,NULL,NULL,NULL,NULL,NULL,NULL,9001}",
            "{9001,NULL}|{9001,NULL,NULL,NULL,NULL,NULL,NULL,9001}|{9001,NULL,NULL,NULL,NULL,NULL,NULL,NULL,9001}",
            "{9001,NULL}|{9001,NULL,NULL,NULL,NULL,NULL,9001}|{9001,NULL,NULL,NULL,NULL,NULL,NULL,9001}",
            "{9001,NULL}|{9001,NULL,NULL,NULL,NULL,NULL,9001}|{9001,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,9001}"})
    @DisplayName("칸 수(주문 2, 아이템 7, 룬 9)가 다른 배열은 거부한다")
    void insert_WhenSlotCountWrong_Reject(String spells, String items, String runes) {
        assertThatThrownBy(() -> insertPick(FAKER, GAME, "BLUE", "MID", spells, items, runes))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"spell_ids", "item_ids", "rune_ids"})
    @DisplayName("주문·아이템·룬 배열이 NULL이면 거부한다 — 빈 칸은 배열 안의 NULL로 둔다")
    void insert_WhenSlotArrayNull_Reject(String column) {
        // given
        long pickId = insertPick(FAKER, GAME, "BLUE", "MID");

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE pro_picks SET " + column + " = NULL WHERE id = ?", pickId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("주문·아이템·룬 자식 테이블은 없다 — 배열 컬럼으로 합쳤다")
    void schema_WhenMerged_HaveNoChildTables() {
        assertThat(jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN ('pro_pick_spells', 'pro_pick_items', 'pro_pick_runes')
                """, String.class)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MIDDLE", "Bot", "UTILITY", ""})
    @DisplayName("포지션이 다섯 라인 이름이 아니면 거부한다")
    void insert_WhenPositionNotOneOfFive_Reject(String position) {
        assertThatThrownBy(() -> insertPick(FAKER, GAME, "BLUE", position))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "PURPLE", "blue"})
    @DisplayName("진영이 BLUE·RED가 아니면 거부한다 — Leaguepedia 1·2는 변환해서 넣는다")
    void insert_WhenSideNotBlueOrRed_Reject(String side) {
        assertThatThrownBy(() -> insertPick(FAKER, GAME, side, "MID"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 경기의 같은 선수는 한 번만 저장된다")
    void insert_WhenSameGameAndPlayer_Reject() {
        // given
        insertPick(FAKER, GAME, "BLUE", "MID");

        // when & then
        assertThatThrownBy(() -> insertPick(FAKER, GAME, "BLUE", "MID"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 경기라도 선수가 다르면 저장된다")
    void insert_WhenSameGameOtherPlayer_SaveRow() {
        // given
        insertPick(FAKER, GAME, "BLUE", "MID");

        // when
        insertPick(KRATOS, GAME, "RED", "MID");

        // then
        assertThat(countOf("pro_picks")).isEqualTo(2);
    }

    @Test
    @DisplayName("카탈로그에 없는 챔피언의 픽은 거부한다")
    void insert_WhenChampionNotInCatalog_Reject() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO pro_picks (tournament_id, player_id, source_game_id, played_at, team, side, position,
                                       champion_id, win, spell_ids, item_ids, rune_ids)
                VALUES (1, 1, 'game', '2026-10-25 08:00:00+00', 'T1', 'BLUE', 'MID', 987654, true,
                        ?::integer[], ?::bigint[], ?::integer[])
                """, SPELLS, ITEMS, RUNES))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 OverviewPage의 대회는 한 번만 저장된다")
    void insert_WhenOverviewPageDuplicated_Reject() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO pro_tournaments (overview_page, name, league)
                VALUES ('2026 Season World Championship/Main Event', 'dup', 'WCS')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 Leaguepedia 페이지의 선수는 한 번만 저장된다")
    void insert_WhenLeaguepediaPageDuplicated_Reject() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO pro_players (leaguepedia_page, name, updated_at) VALUES ('Faker', 'Faker', now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertPick(long playerId, String gameId, String side, String position) {
        return insertPick(playerId, gameId, side, position, SPELLS, ITEMS, RUNES);
    }

    private long insertPick(long playerId, String gameId, String side, String position,
                            String spells, String items, String runes) {
        return jdbcTemplate.queryForObject(INSERT_PICK, Long.class, playerId, gameId, side, position,
                spells, items, runes);
    }

    private int countOf(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
