package dfgg.application.propick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dfgg.domain.catalog.EnglishNameIndex;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.item.Item;
import dfgg.domain.propick.ProPickItem;
import dfgg.domain.propick.ProPickRune;
import dfgg.domain.propick.ProPickSide;
import dfgg.domain.propick.ProPickSpell;
import dfgg.domain.rune.Rune;
import dfgg.domain.spell.SummonerSpell;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProPickRowConverterTest {

    private static final int AHRI = 103;
    private static final int FLASH = 4;
    private static final int IGNITE = 14;
    private static final long DORANS_RING = 1056L;
    private static final long LUDENS = 6655L;
    private static final long STEALTH_WARD = 3340L;
    private static final int ELECTROCUTE = 8112;
    private static final int TASTE_OF_BLOOD = 8139;
    private static final int JACK_OF_ALL_TRADES = 8316;
    private static final int ADAPTIVE_FORCE = 5008;
    private static final int HEALTH = 5011;
    private static final int HEALTH_SCALING = 5001;

    private final ProPickCatalog catalog = new ProPickCatalog(
            EnglishNameIndex.ofChampions(List.of(champion(AHRI, "Ahri"))),
            EnglishNameIndex.ofSpells(List.of(spell(FLASH, "Flash"), spell(IGNITE, "Ignite"))),
            EnglishNameIndex.ofItems(List.of(item(DORANS_RING, "Doran's Ring"), item(LUDENS, "Luden's Companion"),
                    item(STEALTH_WARD, "Stealth Ward"))),
            EnglishNameIndex.ofRunes(List.of(rune(ELECTROCUTE, 0, "Electrocute"), rune(TASTE_OF_BLOOD, 1, "Taste of Blood"),
                    rune(JACK_OF_ALL_TRADES, 2, "Jack of All Trades"), shard(ADAPTIVE_FORCE, "Adaptive Force"),
                    shard(HEALTH, "Health"), shard(HEALTH_SCALING, "Health Scaling"))));

    private final ProPickRowConverter converter = new ProPickRowConverter();

    @Test
    @DisplayName("경기·대회·선수·팀·시각·승패를 옮긴다")
    void convert_WhenValidRow_CopyGameFields() {
        // when
        ProPickConversion conversion = converter.convert(row(), catalog);

        // then
        ProPickDraft draft = conversion.draft().orElseThrow();
        assertThat(draft.sourceGameId()).isEqualTo("2026 Season World Championship/Play-In_Round 3_1_3");
        assertThat(draft.overviewPage()).isEqualTo("2026 Season World Championship/Play-In");
        assertThat(draft.playerPage()).isEqualTo("Faker");
        assertThat(draft.team()).isEqualTo("T1");
        assertThat(draft.playedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 18, 19, 48, 0, 0, ZoneOffset.UTC));
        assertThat(draft.win()).isTrue();
        assertThat(draft.championId()).isEqualTo(AHRI);
    }

    @ParameterizedTest
    @CsvSource({"1, BLUE", "2, RED"})
    @DisplayName("Side 1은 BLUE, 2는 RED다")
    void convert_WhenSideNumber_MapToBlueOrRed(String side, ProPickSide expected) {
        // given
        Map<String, String> row = row();
        row.put("Side", side);

        // when & then
        assertThat(converter.convert(row, catalog).draft().orElseThrow().side()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"Top, TOP", "Jungle, JUNGLE", "Mid, MID", "Bot, BOTTOM", "Support, SUPPORT"})
    @DisplayName("Role은 서비스 포지션으로 바꾼다 — Bot은 BOTTOM")
    void convert_WhenRole_MapToPosition(String role, ChampionPosition expected) {
        // given
        Map<String, String> row = row();
        row.put("Role", role);

        // when & then
        assertThat(converter.convert(row, catalog).draft().orElseThrow().position()).isEqualTo(expected);
    }

    @Test
    @DisplayName("PlayerWin이 Yes가 아니면 패배다")
    void convert_WhenPlayerWinNo_Lose() {
        // given
        Map<String, String> row = row();
        row.put("PlayerWin", "No");

        // when & then
        assertThat(converter.convert(row, catalog).draft().orElseThrow().win()).isFalse();
    }

    @Test
    @DisplayName("소환사 주문은 목록 순서대로 칸 0·1에 넣는다")
    void convert_WhenTwoSpells_PutInListOrder() {
        // when
        ProPickDraft draft = converter.convert(row(), catalog).draft().orElseThrow();

        // then
        assertThat(draft.spells()).extracting(ProPickSpell::getSlot, ProPickSpell::getSpellId)
                .containsExactly(tuple(0, IGNITE), tuple(1, FLASH));
    }

    @Test
    @DisplayName("아이템은 ; 칸 위치를 지키고, 빈칸·카탈로그에 없는 아이템은 빼며, 장신구는 칸 6이다")
    void convert_WhenItemsHaveBlankAndUnknown_KeepSlotPositionAndSkipThem() {
        // when
        ProPickDraft draft = converter.convert(row(), catalog).draft().orElseThrow();

        // then
        assertThat(draft.items()).extracting(ProPickItem::getSlot, ProPickItem::getItemId)
                .containsExactly(tuple(1, DORANS_RING), tuple(3, LUDENS), tuple(6, STEALTH_WARD));
    }

    @Test
    @DisplayName("장신구가 카탈로그에 없으면(Oracle Lens) 칸 6을 만들지 않는다")
    void convert_WhenTrinketNotInCatalog_SkipSlotSix() {
        // given
        Map<String, String> row = row();
        row.put("Trinket", "Oracle Lens");

        // when & then
        assertThat(converter.convert(row, catalog).draft().orElseThrow().items())
                .extracting(ProPickItem::getSlot).doesNotContain(6);
    }

    @Test
    @DisplayName("룬은 9칸 순서대로 넣고, 이름 대소문자는 무시하며, Health는 8번 칸이면 고정 체력·그 밖이면 레벨당 체력이다")
    void convert_WhenNineRunes_PutBySlotAndResolveHealthByPosition() {
        // when
        ProPickDraft draft = converter.convert(row(), catalog).draft().orElseThrow();

        // then
        assertThat(draft.runes()).extracting(ProPickRune::getSlot, ProPickRune::getRuneId)
                .containsExactly(
                        tuple(0, ELECTROCUTE), tuple(1, TASTE_OF_BLOOD),
                        tuple(4, JACK_OF_ALL_TRADES),
                        tuple(6, ADAPTIVE_FORCE), tuple(7, HEALTH_SCALING), tuple(8, HEALTH));
    }

    @Test
    @DisplayName("주문·아이템·룬이 비어 있으면 빈 목록이다")
    void convert_WhenChildFieldsBlank_ReturnEmptyChildren() {
        // given
        Map<String, String> row = row();
        row.put("SummonerSpells", "");
        row.put("Items", "");
        row.put("Trinket", "");
        row.put("Runes", "");

        // when
        ProPickDraft draft = converter.convert(row, catalog).draft().orElseThrow();

        // then
        assertThat(draft.spells()).isEmpty();
        assertThat(draft.items()).isEmpty();
        assertThat(draft.runes()).isEmpty();
    }

    @Test
    @DisplayName("Cargo가 HTML 엔티티로 준 이름(&#039;·&amp;)도 풀어서 찾는다")
    void convert_WhenNamesHtmlEscaped_UnescapeBeforeLookup() {
        // given
        Map<String, String> row = row();
        row.put("Items", "Doran&#039;s Ring");

        // when & then
        assertThat(converter.convert(row, catalog).draft().orElseThrow().items())
                .extracting(ProPickItem::getItemId).contains(DORANS_RING);
    }

    @Test
    @DisplayName("모르는 챔피언이면 그 행을 건너뛰고 사유를 남긴다")
    void convert_WhenUnknownChampion_SkipWithReason() {
        // given
        Map<String, String> row = row();
        row.put("Champion", "Zaahen");

        // when
        ProPickConversion conversion = converter.convert(row, catalog);

        // then
        assertThat(conversion.draft()).isEmpty();
        assertThat(conversion.skipReason()).isEqualTo(ProPickSkipReason.UNKNOWN_CHAMPION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Coach", "", "MIDDLE"})
    @DisplayName("다섯 Role이 아니면 건너뛴다")
    void convert_WhenUnknownRole_SkipWithReason(String role) {
        // given
        Map<String, String> row = row();
        row.put("Role", role);

        // when & then
        assertThat(converter.convert(row, catalog).skipReason()).isEqualTo(ProPickSkipReason.UNKNOWN_ROLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "3", ""})
    @DisplayName("Side가 1·2가 아니면 건너뛴다")
    void convert_WhenUnknownSide_SkipWithReason(String side) {
        // given
        Map<String, String> row = row();
        row.put("Side", side);

        // when & then
        assertThat(converter.convert(row, catalog).skipReason()).isEqualTo(ProPickSkipReason.UNKNOWN_SIDE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2026-10-18", "yesterday"})
    @DisplayName("경기 시각을 읽을 수 없으면 건너뛴다")
    void convert_WhenInvalidDateTime_SkipWithReason(String dateTime) {
        // given
        Map<String, String> row = row();
        row.put("DateTime UTC", dateTime);

        // when & then
        assertThat(converter.convert(row, catalog).skipReason()).isEqualTo(ProPickSkipReason.INVALID_DATE_TIME);
    }

    private static Map<String, String> row() {
        Map<String, String> row = new HashMap<>();
        row.put("GameId", "2026 Season World Championship/Play-In_Round 3_1_3");
        row.put("OverviewPage", "2026 Season World Championship/Play-In");
        row.put("DateTime UTC", "2026-10-18 19:48:00");
        row.put("Team", "T1");
        row.put("Side", "1");
        row.put("PlayerWin", "Yes");
        row.put("Link", "Faker");
        row.put("Role", "Mid");
        row.put("Champion", "ahri");
        row.put("SummonerSpells", "Ignite,Flash");
        row.put("Items", ";Doran's Ring;;Luden's Companion;Unknown Relic;");
        row.put("Trinket", "Stealth Ward");
        row.put("Runes", "Electrocute,Taste of Blood,Unknown Rune,,Jack Of All Trades,,Adaptive Force,Health,Health");
        return row;
    }

    private static Champion champion(int id, String englishName) {
        return new Champion((long) id, "Key" + id, Map.of("ko-KR", "챔피언" + id, "en-US", englishName), List.of());
    }

    private static SummonerSpell spell(int id, String englishName) {
        return new SummonerSpell(id, "Summoner" + id, Map.of("ko-KR", "주문" + id, "en-US", englishName), "icon.png");
    }

    private static Item item(long id, String englishName) {
        return new Item(id, Map.of("ko-KR", "아이템" + id, "en-US", englishName));
    }

    private static Rune rune(int id, int slot, String englishName) {
        return new Rune(id, 8100, slot, Map.of("ko-KR", "룬" + id, "en-US", englishName), "icon.png");
    }

    private static Rune shard(int id, String englishName) {
        return new Rune(id, null, Rune.SHARD_SLOT, Map.of("ko-KR", "파편" + id, "en-US", englishName), "icon.png");
    }
}
