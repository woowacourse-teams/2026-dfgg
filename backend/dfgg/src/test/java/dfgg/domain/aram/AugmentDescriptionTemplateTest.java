package dfgg.domain.aram;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.language.Language;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 원문은 16.22 CommunityDragon 데이터 그대로다. */
class AugmentDescriptionTemplateTest {

    @Test
    @DisplayName("자리표시자가 없으면 게임 서식 태그만 지운다")
    void render_WhenNoPlaceholder_StripsTags() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.KO_KR,
                "스킬 피해량, <healing>체력 회복량</healing>, <shield>보호막 흡수량</shield> 및 "
                        + "<scaleAbilityHaste>스킬 가속</scaleAbilityHaste>이 증가하지만 "
                        + "<keywordMajor>궁극기를 사용할 수 없습니다</keywordMajor>.");

        // when
        Optional<String> description = template.render(Map.of(), new SpellValues(Map.of()));

        // then
        assertThat(description).contains("스킬 피해량, 체력 회복량, 보호막 흡수량 및 스킬 가속이 증가하지만 궁극기를 사용할 수 없습니다.");
    }

    @Test
    @DisplayName("데이터값 자리표시자에 배수를 곱해 넣고 아이콘 표시를 지운다")
    void render_WhenDataValueWithMultiplier_FillsNumber() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.KO_KR,
                "%i:scaleAD% <scaleAD>공격력이 @ADAmp*100@%</scaleAD> 증가합니다.");

        // when
        Optional<String> description = template.render(Map.of(),
                new SpellValues(Map.of("ADAmp", 0.20000000298023224)));

        // then
        assertThat(description).contains("공격력이 20% 증가합니다.");
    }

    @Test
    @DisplayName("영어 원문도 같은 규칙으로 만든다")
    void render_WhenEnglish_FillsNumber() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.EN_US,
                "Gain %i:scaleAD% <scaleAD>@ADAmp*100@% Attack Damage</scaleAD>.");

        // when
        Optional<String> description = template.render(Map.of(),
                new SpellValues(Map.of("ADAmp", 0.20000000298023224)));

        // then
        assertThat(description).contains("Gain 20% Attack Damage.");
    }

    @Test
    @DisplayName("배수 없는 자리표시자와 배수 있는 자리표시자를 함께 채운다")
    void render_WhenSeveralDataValues_FillsAll() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.KO_KR,
                "<scaleHealth>@Health@의 체력</scaleHealth>을 얻지만, 피해량이 @DamageReduction*100@% 감소합니다.");

        // when
        Optional<String> description = template.render(Map.of(),
                new SpellValues(Map.of("Health", 1500.0, "DamageReduction", 0.10000000149011612)));

        // then
        assertThat(description).contains("1500의 체력을 얻지만, 피해량이 10% 감소합니다.");
    }

    @Test
    @DisplayName("br 태그는 줄바꿈으로 바꾼다")
    void render_WhenLineBreakTag_ReplacesWithNewLine() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.KO_KR,
                "<scaleBonus>아이템 가속이 @ItemHaste@</scaleBonus> 증가합니다. "
                        + "(@TooltipCDR@%의 아이템 재사용 대기시간 감소 효과와 동일)<br><br>"
                        + "<rules>아이템 가속은 모든 아이템 스킬의 재사용 대기시간%i:cooldown%을 감소시킵니다.</rules>");

        // when
        Optional<String> description = template.render(Map.of(),
                new SpellValues(Map.of("ItemHaste", 100.0, "TooltipCDR", 50.0)));

        // then
        assertThat(description).contains("아이템 가속이 100 증가합니다. (50%의 아이템 재사용 대기시간 감소 효과와 동일)\n\n"
                + "아이템 가속은 모든 아이템 스킬의 재사용 대기시간을 감소시킵니다.");
    }

    @Test
    @DisplayName("끼워넣기는 같은 언어 문자열로 바꾼다")
    void render_WhenInclude_ReplacesWithString() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.KO_KR,
                "소환사 주문이 <spellName>{{Kiwi_Augment_Totentanz_Summoner_Name}}</spellName>으로 변경됩니다.<br><br>"
                        + "<spellName>{{Kiwi_Augment_Totentanz_Summoner_Name}}</spellName>: 전방으로 순간적으로 이동하여 "
                        + "<attention>불사</attention> 상태가 됩니다. @Buff_Duration@초 이내에 처치 관여를 달성하지 않으면<br>"
                        + "<danger>사망합니다</danger>.");
        Map<String, String> strings = Map.of("kiwi_augment_totentanz_summoner_name", "죽음과 함께하는 춤");

        // when
        Optional<String> description = template.render(strings, new SpellValues(Map.of("Buff_Duration", 5.0)));

        // then
        assertThat(description).contains("소환사 주문이 죽음과 함께하는 춤으로 변경됩니다.\n\n"
                + "죽음과 함께하는 춤: 전방으로 순간적으로 이동하여 불사 상태가 됩니다. 5초 이내에 처치 관여를 달성하지 않으면\n"
                + "사망합니다.");
    }

    @Test
    @DisplayName("스킬 증강의 스킬 이름 자리는 한국어로 '해당 스킬'이다")
    void render_WhenSpellNameInKorean_UsesGenericSkill() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.KO_KR,
                "{{SpellName}} 스킬 사용 시 <healing>체력</healing>을 회복합니다.");

        // when
        Optional<String> description = template.render(Map.of(), new SpellValues(Map.of()));

        // then
        assertThat(description).contains("해당 스킬 사용 시 체력을 회복합니다.");
    }

    @Test
    @DisplayName("스킬 증강의 스킬 이름 자리는 영어로 '[Ability]'다")
    void render_WhenSpellNameInEnglish_UsesAbilityMarker() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.EN_US,
                "Using {{SpellName}} restores <healing>Health</healing>.");

        // when
        Optional<String> description = template.render(Map.of(), new SpellValues(Map.of()));

        // then
        assertThat(description).contains("Using [Ability] restores Health.");
    }

    @Test
    @DisplayName("정의를 찾지 못한 자리표시자가 있으면 설명을 만들지 않는다")
    void render_WhenPlaceholderUndefined_ReturnsEmpty() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.KO_KR,
                "<font color='#F0C200'>퀘스트:</font> 처치 관여를 @QuestRequirement@회 달성해야 합니다.<br><br>보상: 황금 뒤집개");

        // when
        Optional<String> description = template.render(Map.of(), new SpellValues(Map.of()));

        // then
        assertThat(description).isEmpty();
    }

    @Test
    @DisplayName("찾지 못한 끼워넣기가 있으면 설명을 만들지 않는다")
    void render_WhenIncludeMissing_ReturnsEmpty() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.KO_KR,
                "{{ Item_Keyword_OnHit }} 효과를 적용합니다.");

        // when
        Optional<String> description = template.render(Map.of(), new SpellValues(Map.of()));

        // then
        assertThat(description).isEmpty();
    }

    @Test
    @DisplayName("서로를 끼워 넣는 문자열은 설명을 만들지 않는다")
    void render_WhenIncludeCycles_ReturnsEmpty() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.KO_KR, "{{ A }}");
        Map<String, String> strings = Map.of("a", "{{ B }}", "b", "{{ A }}");

        // when
        Optional<String> description = template.render(strings, new SpellValues(Map.of()));

        // then
        assertThat(description).isEmpty();
    }

    @Test
    @DisplayName("끼워 넣은 문자열 안의 스킬 이름 자리도 바꾼다")
    void render_WhenIncludeHasSpellName_UsesGenericSkill() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.KO_KR,
                "{{SpellName}} 강화!<br><br>{{Kiwi_GoH_PentakillAug_Generic_Tooltip}}");
        Map<String, String> strings = Map.of("kiwi_goh_pentakillaug_generic_tooltip",
                "<evolve>강화</evolve> {{SpellName}} 스킬은 <scaleAbilityHaste>스킬 가속이 20</scaleAbilityHaste> 증가합니다.");

        // when
        Optional<String> description = template.render(strings, new SpellValues(Map.of()));

        // then
        assertThat(description).contains("해당 스킬 강화!\n\n강화 해당 스킬은 스킬 가속이 20 증가합니다.");
    }

    @Test
    @DisplayName("끼워 넣는 문자열 키를 소문자로 알려 주고 스킬 이름 자리는 뺀다")
    void includedKeys_ReturnsLowerCaseKeysWithoutSpellName() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.KO_KR,
                "{{SpellName}} 스킬이 {{ Item_Keyword_OnHit }} 효과와 {{Kiwi_Augment_Totentanz_Summoner_Name}}을 얻습니다.");

        // when
        Set<String> keys = template.includedKeys();

        // then
        assertThat(keys).containsExactlyInAnyOrder("item_keyword_onhit", "kiwi_augment_totentanz_summoner_name");
    }

    @Test
    @DisplayName("끼워 넣은 문자열의 자리표시자와 서식도 함께 푼다")
    void render_WhenIncludeHasMarkup_ResolvesIt() {
        // given
        AugmentDescriptionTemplate template = new AugmentDescriptionTemplate(Language.EN_US,
                "Attacks apply {{ Item_Keyword_OnHit }} effects.");
        Map<String, String> strings = Map.of("item_keyword_onhit", "%i:OnHit% <OnHit>On-Hit</OnHit>");

        // when
        Optional<String> description = template.render(strings, new SpellValues(Map.of()));

        // then
        assertThat(description).contains("Attacks apply On-Hit effects.");
    }
}
