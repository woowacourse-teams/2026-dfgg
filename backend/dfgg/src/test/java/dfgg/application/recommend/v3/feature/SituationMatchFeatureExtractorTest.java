package dfgg.application.recommend.v3.feature;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.item.Item;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 상황 값은 한 요청의 모든 후보에게 같아서 순서를 바꾸지 못한다.
 * 후보의 대응 태그와 짝지어 "이 후보가 지금 상황에 맞는가"를 후보마다 다른 값으로 만든다.
 */
class SituationMatchFeatureExtractorTest {

    private static final long CANDIDATE = 3102L;

    private final SituationMatchFeatureExtractor extractor = new SituationMatchFeatureExtractor();

    private static Item itemWithTags(String... tags) {
        return new Item(CANDIDATE, Map.of("ko-KR", "후보"), List.of(tags));
    }

    @ParameterizedTest(name = "{0} = {2} 태그 × {1}")
    @CsvSource({
            "CANDIDATE_SPELL_BLOCK_X_ENEMY_SPELL_DAMAGE_SHARE, ENEMY_SPELL_DAMAGE_GOLD_SHARE, SpellBlock",
            "CANDIDATE_SPELL_BLOCK_X_ENEMY_MAGE_KILL_SHARE, ENEMY_MAGE_KILL_SHARE, SpellBlock",
            "CANDIDATE_ARMOR_X_ENEMY_MARKSMAN_KILL_SHARE, ENEMY_MARKSMAN_KILL_SHARE, Armor",
            "CANDIDATE_ARMOR_X_ENEMY_ASSASSIN_KILL_SHARE, ENEMY_ASSASSIN_KILL_SHARE, Armor",
            "CANDIDATE_MAGIC_PEN_X_ENEMY_SPELL_BLOCK_SHARE, ENEMY_SPELL_BLOCK_GOLD_SHARE, MagicPenetration",
            "CANDIDATE_ARMOR_PEN_X_ENEMY_ARMOR_SHARE, ENEMY_ARMOR_GOLD_SHARE, ArmorPenetration",
            "CANDIDATE_DEFENSIVE_X_MY_LEVEL_LEAD, MY_LEVEL_LEAD, Armor",
            "CANDIDATE_DEFENSIVE_X_MY_LEVEL_LEAD, MY_LEVEL_LEAD, SpellBlock",
    })
    @DisplayName("후보가 짝의 태그를 가졌으면 그 칸에 짝의 상황 값을 그대로 쓴다")
    void extract_WhenCandidateHasMatchingTag_WritesSituationValue(
            FeatureName feature, FeatureName situation, String tag) {
        // given
        FeatureVector vector = FeatureVector.empty();
        vector.set(situation, 0.3);

        // when
        extractor.extract(CANDIDATE, Map.of(CANDIDATE, itemWithTags(tag)), vector);

        // then
        assertThat(vector.get(feature)).isEqualTo(0.3);
    }

    @Test
    @DisplayName("후보가 짝의 태그를 갖지 않으면 0이다 — 상황은 알지만 이 후보는 맞지 않는다")
    void extract_WhenCandidateLacksTag_WritesZero() {
        // given: 상대 마법 피해 비중은 높지만 후보는 공격 아이템이다
        FeatureVector vector = FeatureVector.empty();
        vector.set(FeatureName.ENEMY_SPELL_DAMAGE_GOLD_SHARE, 0.6);

        // when
        extractor.extract(CANDIDATE, Map.of(CANDIDATE, itemWithTags("Damage", "CriticalStrike")), vector);

        // then
        assertThat(vector.get(FeatureName.CANDIDATE_SPELL_BLOCK_X_ENEMY_SPELL_DAMAGE_SHARE)).isZero();
    }

    @Test
    @DisplayName("짝의 상황 값이 비어 있으면 태그가 있어도 비워 둔다 — 0을 넣으면 \"상대가 마법 피해를 안 샀다\"로 읽힌다")
    void extract_WhenSituationValueEmpty_LeavesCellEmpty() {
        // given: 상대 Core가 없어 마법 피해 비중을 못 냈다. 맞라인 레벨 차이는 안다
        FeatureVector vector = FeatureVector.empty();
        vector.set(FeatureName.MY_LEVEL_LEAD, 1.0);

        // when
        extractor.extract(CANDIDATE, Map.of(CANDIDATE, itemWithTags("SpellBlock")), vector);

        // then
        assertThat(vector.get(FeatureName.CANDIDATE_SPELL_BLOCK_X_ENEMY_SPELL_DAMAGE_SHARE)).isNaN();
        assertThat(vector.get(FeatureName.CANDIDATE_DEFENSIVE_X_MY_LEVEL_LEAD)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("후보 아이템을 찾지 못하면 일곱 칸을 모두 비워 둔다 — 태그를 모르면 맞는지도 모른다")
    void extract_WhenCandidateItemUnknown_LeavesAllCellsEmpty() {
        // given
        FeatureVector vector = FeatureVector.empty();
        Arrays.stream(SituationMatch.values()).forEach(match -> vector.set(match.situation(), 0.5));

        // when
        extractor.extract(CANDIDATE, Map.of(), vector);

        // then
        for (SituationMatch match : SituationMatch.values()) {
            assertThat(vector.get(match.feature())).isNaN();
        }
    }

    @Test
    @DisplayName("방어구와 마법 저항을 함께 가진 후보는 방어 칸에 상황 값이 한 번만 들어간다")
    void extract_WhenCandidateHasArmorAndSpellBlock_WritesDefensiveValueOnce() {
        // given: 내가 레벨 2 뒤처졌다
        FeatureVector vector = FeatureVector.empty();
        vector.set(FeatureName.MY_LEVEL_LEAD, -2.0);

        // when
        extractor.extract(CANDIDATE, Map.of(CANDIDATE, itemWithTags("Armor", "SpellBlock", "Health")), vector);

        // then
        assertThat(vector.get(FeatureName.CANDIDATE_DEFENSIVE_X_MY_LEVEL_LEAD)).isEqualTo(-2.0);
    }

    @Test
    @DisplayName("규칙은 일곱 개이고 새 feature 일곱 개에 하나씩 대응한다 — 빠진 칸은 아무도 채우지 않는다")
    void situationMatches_CoverEveryCandidateSituationFeatureOnce() {
        // given
        List<FeatureName> appended = Arrays.stream(FeatureName.values())
                .skip(FeatureName.situationV1ExportNames().size())
                .toList();

        // when
        List<FeatureName> covered = Arrays.stream(SituationMatch.values()).map(SituationMatch::feature).toList();

        // then
        assertThat(covered).containsExactlyElementsOf(appended);
    }
}
