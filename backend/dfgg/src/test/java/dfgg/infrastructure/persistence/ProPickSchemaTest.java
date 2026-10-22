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
                                   champion_id, win)
            VALUES (1, ?, ?, '2026-10-25 08:00:00+00', 'T1', ?, ?, 9001, true)
            RETURNING id
            """;
    private static final String GAME = "2026 Season World Championship/Main Event_Round 1_1_1";
    private static final long FAKER = 1L;
    private static final long KRATOS = 2L;
    private static final int TEST_SPELL = 9001;
    private static final long TEST_ITEM = 9001L;
    private static final int TEST_RUNE = 9001;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("대회·선수·챔피언이 있는 픽과 주문·아이템·룬 자식 행은 저장된다")
    void insert_WhenValidPickAndChildren_SaveRows() {
        // given
        long pickId = insertPick(FAKER, GAME, "BLUE", "MID");

        // when
        insertSpell(pickId, 0, TEST_SPELL);
        insertItem(pickId, 6, TEST_ITEM);
        insertRune(pickId, 8, TEST_RUNE);

        // then
        assertThat(countOf("pro_pick_spells") + countOf("pro_pick_items") + countOf("pro_pick_runes")).isEqualTo(3);
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

    @ParameterizedTest
    @CsvSource({"pro_pick_spells, spell_id, -1", "pro_pick_spells, spell_id, 2",
            "pro_pick_items, item_id, -1", "pro_pick_items, item_id, 7",
            "pro_pick_runes, rune_id, -1", "pro_pick_runes, rune_id, 9"})
    @DisplayName("칸 범위(주문 0~1, 아이템 0~6, 룬 0~8)를 벗어나면 거부한다")
    void insert_WhenChildSlotOutOfRange_Reject(String table, String column, int slot) {
        // given
        long pickId = insertPick(FAKER, GAME, "BLUE", "MID");

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO " + table + " (pick_id, slot, " + column + ") VALUES (?, ?, 9001)", pickId, slot))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("한 픽의 같은 칸에 두 행을 넣으면 거부한다")
    void insert_WhenSameSlotTwice_Reject() {
        // given
        long pickId = insertPick(FAKER, GAME, "BLUE", "MID");
        insertItem(pickId, 0, TEST_ITEM);

        // when & then
        assertThatThrownBy(() -> insertItem(pickId, 0, TEST_ITEM))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("픽을 지우면 주문·아이템·룬 자식 행도 지워진다")
    void delete_WhenPickDeleted_CascadeChildren() {
        // given
        long pickId = insertPick(FAKER, GAME, "BLUE", "MID");
        insertSpell(pickId, 0, TEST_SPELL);
        insertItem(pickId, 0, TEST_ITEM);
        insertRune(pickId, 0, TEST_RUNE);

        // when
        jdbcTemplate.update("DELETE FROM pro_picks WHERE id = ?", pickId);

        // then
        assertThat(countOf("pro_pick_spells") + countOf("pro_pick_items") + countOf("pro_pick_runes")).isZero();
    }

    @ParameterizedTest
    @CsvSource({"pro_pick_spells, spell_id", "pro_pick_items, item_id", "pro_pick_runes, rune_id"})
    @DisplayName("카탈로그에 없는 주문·아이템·룬은 거부한다")
    void insert_WhenChildNotInCatalog_Reject(String table, String column) {
        // given
        long pickId = insertPick(FAKER, GAME, "BLUE", "MID");

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO " + table + " (pick_id, slot, " + column + ") VALUES (?, 0, 987654)", pickId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("카탈로그에 없는 챔피언의 픽은 거부한다")
    void insert_WhenChampionNotInCatalog_Reject() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO pro_picks (tournament_id, player_id, source_game_id, played_at, team, side, position,
                                       champion_id, win)
                VALUES (1, 1, 'game', '2026-10-25 08:00:00+00', 'T1', 'BLUE', 'MID', 987654, true)
                """))
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
        return jdbcTemplate.queryForObject(INSERT_PICK, Long.class, playerId, gameId, side, position);
    }

    private void insertSpell(long pickId, int slot, int spellId) {
        jdbcTemplate.update("INSERT INTO pro_pick_spells (pick_id, slot, spell_id) VALUES (?, ?, ?)",
                pickId, slot, spellId);
    }

    private void insertItem(long pickId, int slot, long itemId) {
        jdbcTemplate.update("INSERT INTO pro_pick_items (pick_id, slot, item_id) VALUES (?, ?, ?)",
                pickId, slot, itemId);
    }

    private void insertRune(long pickId, int slot, int runeId) {
        jdbcTemplate.update("INSERT INTO pro_pick_runes (pick_id, slot, rune_id) VALUES (?, ?, ?)",
                pickId, slot, runeId);
    }

    private int countOf(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
