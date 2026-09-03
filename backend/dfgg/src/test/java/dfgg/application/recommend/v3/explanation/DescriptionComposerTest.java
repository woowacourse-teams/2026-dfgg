package dfgg.application.recommend.v3.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.recommend.v3.feature.ReasonGroup;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 고른 이유를 한 문장으로 옮긴다.
 * <p>
 * 문구는 각 generator가 실제로 무엇을 재는지에 맞춘다. 특히 BUILD와 SELF_SYNERGY를 뭉뚱그리면 안 된다
 * — BUILD는 "지금까지 산 것 다음에 무엇을 사는가"이고,
 * SELF_SYNERGY는 구매 이력을 아예 보지 않고
 * "이 아이템이 이 챔피언에게 본질적으로 맞는가"를 본다. 둘은 다른 말이다.
 */
class DescriptionComposerTest {

    private static final String RENEKTON = "레넥톤";

    private final DescriptionComposer composer = new DescriptionComposer();

    private static SelectedReasons reasons(GroupWeight... highlights) {
        return new SelectedReasons(List.of(highlights), Optional.empty());
    }

    private static GroupWeight weight(ReasonGroup group, double value) {
        return new GroupWeight(group, value);
    }

    @Nested
    @DisplayName("문장 만들기")
    class Sentence {

        @Test
        @DisplayName("두 이유를 연결해 한 문장으로 만든다")
        void compose_JoinsTwoReasonsIntoOneSentence() {
            SelectedReasons selected = reasons(
                    weight(ReasonGroup.BUILD, 2.45), weight(ReasonGroup.COUNTER, 1.02));

            String description = composer.compose(selected, RENEKTON);

            assertThat(description)
                    .isEqualTo("현재 빌드 흐름에 잘 맞고 상대 조합에 대응하기 좋아 추천했어요.");
        }

        @Test
        @DisplayName("이유가 하나면 종결형만 쓴다 — 연결어미가 남으면 문장이 끊긴다")
        void compose_WhenSingleReason_UsesTheTerminalForm() {
            String description = composer.compose(reasons(weight(ReasonGroup.BUILD, 2.0)), RENEKTON);

            assertThat(description).isEqualTo("현재 빌드 흐름에 잘 맞아 추천했어요.");
        }

        @Test
        @DisplayName("고른 순서를 그대로 문장 순서로 쓴다 — 가장 큰 이유가 앞에 온다")
        void compose_KeepsTheOrderOfTheSelectedReasons() {
            SelectedReasons selected = reasons(
                    weight(ReasonGroup.COUNTER, 1.09), weight(ReasonGroup.PATCH_META, 0.55));

            String description = composer.compose(selected, RENEKTON);

            assertThat(description)
                    .isEqualTo("상대 조합에 대응하기 좋고 현재 패치에서 효율이 좋아 추천했어요.");
        }

        @Test
        @DisplayName("말할 이유가 없어도 문장은 낸다 — 빈 설명을 내보내지 않는다")
        void compose_WhenNoReasonQualifies_StillProducesASentence() {
            String description = composer.compose(reasons(), RENEKTON);

            assertThat(description).isNotBlank().endsWith("추천했어요.");
        }

        @Test
        @DisplayName("챔피언 특성 이유에는 챔피언 이름이 들어간다")
        void compose_NamesTheChampionForItsOwnAffinity() {
            String description = composer.compose(
                    reasons(weight(ReasonGroup.SELF_SYNERGY, 1.0)), RENEKTON);

            assertThat(description).contains("레넥톤");
        }

        @Test
        @DisplayName("BUILD와 SELF_SYNERGY는 다른 말을 한다 — 재는 대상이 다르다")
        void compose_DistinguishesBuildOrderFromChampionAffinity() {
            String build = composer.compose(reasons(weight(ReasonGroup.BUILD, 1.0)), RENEKTON);
            String self = composer.compose(reasons(weight(ReasonGroup.SELF_SYNERGY, 1.0)), RENEKTON);

            assertThat(build).isNotEqualTo(self);
        }
    }

    @Nested
    @DisplayName("모든 묶음에 문구가 있다")
    class EveryGroupHasPhrases {

        @ParameterizedTest
        @EnumSource(ReasonGroup.class)
        @DisplayName("어떤 묶음이 뽑혀도 온전한 문장이 나온다")
        void compose_ForEveryGroup_ProducesACompleteSentence(ReasonGroup group) {
            String description = composer.compose(reasons(weight(group, 1.0)), RENEKTON);

            assertThat(description)
                    .isNotBlank()
                    .doesNotContain("null")
                    .endsWith("추천했어요.");
        }

        @ParameterizedTest
        @EnumSource(ReasonGroup.class)
        @DisplayName("어떤 묶음이든 연결형으로 이어붙일 수 있다")
        void compose_ForEveryGroup_CanBeJoinedAsTheLeadingClause(ReasonGroup group) {
            SelectedReasons selected = reasons(weight(group, 2.0), weight(ReasonGroup.COUNTER, 1.0));

            String description = composer.compose(selected, RENEKTON);

            assertThat(description).doesNotContain("null").endsWith("추천했어요.");
        }

        @ParameterizedTest
        @EnumSource(ReasonGroup.class)
        @DisplayName("어떤 묶음이든 단서 문구가 있다")
        void compose_ForEveryGroup_HasACaveatPhrase(ReasonGroup group) {
            SelectedReasons selected = new SelectedReasons(
                    List.of(weight(ReasonGroup.BUILD, 1.0)),
                    Optional.of(weight(group, -0.5)));

            String description = composer.compose(selected, RENEKTON);

            assertThat(description).doesNotContain("null").contains("다만");
        }
    }

    @Nested
    @DisplayName("단서 붙이기")
    class Caveat {

        @Test
        @DisplayName("단서가 있으면 문장 뒤에 덧붙인다")
        void compose_WhenCaveatIsPresent_AppendsIt() {
            SelectedReasons selected = new SelectedReasons(
                    List.of(weight(ReasonGroup.BUILD, 1.0)),
                    Optional.of(weight(ReasonGroup.CONTEXT, -0.6)));

            String description = composer.compose(selected, RENEKTON);

            assertThat(description)
                    .startsWith("현재 빌드 흐름에 잘 맞아 추천했어요.")
                    .contains("다만");
        }

        @Test
        @DisplayName("단서가 없으면 덧붙이지 않는다 — 추천해놓고 깎는 말을 기본값으로 두지 않는다")
        void compose_WhenNoCaveat_SaysNothingExtra() {
            String description = composer.compose(reasons(weight(ReasonGroup.BUILD, 1.0)), RENEKTON);

            assertThat(description).doesNotContain("다만");
        }
    }

    @Nested
    @DisplayName("문장 형식")
    class Formatting {

        @Test
        @DisplayName("앞뒤 공백이나 이중 공백이 없다")
        void compose_HasNoStrayWhitespace() {
            String description = composer.compose(
                    reasons(weight(ReasonGroup.BUILD, 2.0), weight(ReasonGroup.COUNTER, 1.0)),
                    RENEKTON);

            assertThat(description).isEqualTo(description.trim()).doesNotContain("  ");
        }

        @Test
        @DisplayName("마침표로 끝난다")
        void compose_EndsWithAPeriod() {
            SelectedReasons selected = new SelectedReasons(
                    List.of(weight(ReasonGroup.BUILD, 1.0)),
                    Optional.of(weight(ReasonGroup.CONTEXT, -0.6)));

            assertThat(composer.compose(selected, RENEKTON)).endsWith(".");
        }
    }
}
