package dfgg.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.domain.rune.Rune;
import dfgg.domain.spell.SummonerSpell;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EnglishNameIndexTest {

    private static final int PRECISION = 8000;
    private static final int GRASP = 8437;
    private static final int HEALTH = 5011;
    private static final int HEALTH_SCALING = 5001;

    private final EnglishNameIndex runes = EnglishNameIndex.ofRunes(List.of(
            rune(PRECISION, PRECISION, Rune.TREE_SLOT, "Precision"),
            rune(GRASP, 8400, 0, "Grasp of the Undying"),
            rune(HEALTH, null, Rune.SHARD_SLOT, "Health"),
            rune(HEALTH_SCALING, null, Rune.SHARD_SLOT, "Health Scaling")));

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

    @Test
    @DisplayName("정규화하면 같아지는 이름이 둘이면 색인을 만들 때 거부한다")
    void ofRunes_WhenNormalizedNamesCollide_Throw() {
        // given
        List<Rune> colliding = List.of(
                rune(GRASP, 8400, 0, "Grasp of the Undying"),
                rune(9999, 8400, 1, "Grasp Of The-Undying"));

        // when & then
        assertThatThrownBy(() -> EnglishNameIndex.ofRunes(colliding))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("8437")
                .hasMessageContaining("9999");
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

    private static Rune rune(int runeId, Integer treeId, int slot, String englishName) {
        return new Rune(runeId, treeId, slot, Map.of("ko-KR", "룬" + runeId, "en-US", englishName), "icon.png");
    }
}
