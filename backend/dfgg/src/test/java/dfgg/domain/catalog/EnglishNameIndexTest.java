package dfgg.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.Champion;
import dfgg.domain.item.Item;
import dfgg.domain.rune.Rune;
import dfgg.domain.rune.RuneKind;
import dfgg.domain.spell.SummonerSpell;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EnglishNameIndexTest {

    private static final int PRECISION = 8000;
    private static final int GRASP = 8437;
    private static final int HEALTH = 5011;
    private static final int HEALTH_SCALING = 5001;

    private final EnglishNameIndex runes = EnglishNameIndex.ofRunes(List.of(
            rune(PRECISION, RuneKind.STYLE, "Precision"),
            rune(GRASP, RuneKind.PERK, "Grasp of the Undying"),
            rune(HEALTH, RuneKind.STAT_SHARD, "Health"),
            rune(HEALTH_SCALING, RuneKind.STAT_SHARD, "Health Scaling")));

    @ParameterizedTest
    @ValueSource(strings = {"Grasp of the Undying", "Grasp Of The Undying", "grasp-of-the-undying", " GraspoftheUndying "})
    @DisplayName("대소문자·공백·구두점이 달라도 같은 룬 id를 찾는다")
    void idOf_WhenCaseSpacePunctuationDiffer_FindSameRune(String englishName) {
        assertThat(runes.idOf(englishName)).hasValue(GRASP);
    }

    @Test
    @DisplayName("트리 이름으로도 트리 id를 찾는다")
    void idOf_WhenTreeName_FindTreeId() {
        assertThat(runes.idOf("precision")).hasValue(PRECISION);
    }

    @Test
    @DisplayName("Health와 Health Scaling은 서로 다른 파편이다")
    void idOf_WhenHealthShards_DistinguishFlatAndScaling() {
        assertThat(runes.idOf("Health")).hasValue(HEALTH);
        assertThat(runes.idOf("Health Scaling")).hasValue(HEALTH_SCALING);
    }

    @Test
    @DisplayName("모르는 이름이면 비어 있다")
    void idOf_WhenUnknownName_ReturnEmpty() {
        assertThat(runes.idOf("Lethal Tempo Classic")).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "--"})
    @DisplayName("비었거나 글자가 없는 이름이면 비어 있다")
    void idOf_WhenBlankName_ReturnEmpty(String englishName) {
        assertThat(runes.idOf(englishName)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"3075, 323075", "323075, 3075"})
    @DisplayName("영어 이름이 같은 아이템이 여럿이면 넣은 순서와 상관없이 가장 작은 id를 쓴다 — 접두사 없는 협곡 랭크 id다")
    void ofItems_WhenEnglishNamesCollide_UseSmallestId(long firstId, long secondId) {
        // given
        List<Item> items = List.of(
                new Item(firstId, Map.of("ko-KR", "가시 갑옷", "en-US", "Thornmail")),
                new Item(secondId, Map.of("ko-KR", "가시 갑옷", "en-US", "Thornmail")));

        // when
        EnglishNameIndex index = EnglishNameIndex.ofItems(items);

        // then
        assertThat(index.idOf("Thornmail")).hasValue(3075);
    }

    @Test
    @DisplayName("소환사 주문도 영어 이름으로 id를 찾는다")
    void ofSpells_WhenEnglishName_FindSpellId() {
        // given
        EnglishNameIndex spells = EnglishNameIndex.ofSpells(List.of(
                new SummonerSpell(4, "SummonerFlash", Map.of("ko-KR", "점멸", "en-US", "Flash"), "SummonerFlash.png"),
                new SummonerSpell(14, "SummonerDot", Map.of("ko-KR", "점화", "en-US", "Ignite"), "SummonerDot.png")));

        // when & then
        assertThat(spells.idOf("ignite")).hasValue(14);
    }

    @Test
    @DisplayName("챔피언도 영어 이름으로 id를 찾는다 — 기호가 든 이름도 맞춘다")
    void ofChampions_WhenEnglishName_FindChampionId() {
        // given
        EnglishNameIndex champions = EnglishNameIndex.ofChampions(List.of(
                new Champion(20L, "Nunu", Map.of("ko-KR", "누누와 윌럼프", "en-US", "Nunu & Willump"), List.of()),
                new Champion(62L, "MonkeyKing", Map.of("ko-KR", "오공", "en-US", "Wukong"), List.of())));

        // when & then
        assertThat(champions.idOf("nunu & willump")).hasValue(20);
        assertThat(champions.idOf("Wukong")).hasValue(62);
    }

    @Test
    @DisplayName("아이템도 영어 이름으로 id를 찾는다 — 작은따옴표는 무시한다")
    void ofItems_WhenEnglishName_FindItemId() {
        // given
        EnglishNameIndex items = EnglishNameIndex.ofItems(List.of(
                new Item(1056L, Map.of("ko-KR", "도란의 반지", "en-US", "Doran's Ring"))));

        // when & then
        assertThat(items.idOf("Dorans Ring")).hasValue(1056);
    }

    @Test
    @DisplayName("영어 이름이 없는 행(한국어 이름만 있던 예전 아이템)은 색인에서 뺀다")
    void ofItems_WhenEnglishNameMissing_SkipEntry() {
        // given
        EnglishNameIndex items = EnglishNameIndex.ofItems(List.of(
                new Item(1001L, Map.of("ko-KR", "장화")),
                new Item(1002L, Map.of("ko-KR", "망토", "en-US", " ")),
                new Item(1056L, Map.of("ko-KR", "도란의 반지", "en-US", "Doran's Ring"))));

        // when & then
        assertThat(items.idOf("Doran's Ring")).hasValue(1056);
        assertThat(items.idOf("")).isEmpty();
    }

    private static Rune rune(int runeId, RuneKind kind, String englishName) {
        return new Rune(runeId, kind, Map.of("ko-KR", "룬" + runeId, "en-US", englishName));
    }
}
