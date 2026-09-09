package dfgg.application.recommend.v3.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.recommend.v3.feature.ReasonGroup;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 그룹 기여도 중 근거로 쓸 만한 것을 고른다.
 * <p>
 * 문장을 만들던 시절에는 상위 2개만 골랐다 — 한 문장에 이유 셋을 넣으면 읽기 어렵기 때문이다.
 * 지금은 구조화된 키를 내보내므로 <b>무엇을 보여줄지는 클라이언트가 고른다</b>.
 * 여기 남는 규칙은 노이즈 차단 하나다 — 실제 응답에서 CONTEXT는 +0.0006 같은 값이 나온다.
 * <p>
 * 아래 숫자는 레넥톤 TOP 실제 응답에서 그대로 가져왔다.
 * 규칙이 실데이터에서 어떻게 동작하는지가 이 클래스의 관심사라, 합성값 대신 실제 값으로 고정한다.
 */
class ExplanationSelectorTest {

    private final ExplanationSelector selector = new ExplanationSelector();

    private static Map<ReasonGroup, Double> contributions(double build, double counter,
            double patchMeta, double allySynergy, double selfSynergy, double context, double team) {
        Map<ReasonGroup, Double> values = new EnumMap<>(ReasonGroup.class);
        values.put(ReasonGroup.BUILD, build);
        values.put(ReasonGroup.COUNTER, counter);
        values.put(ReasonGroup.PATCH_META, patchMeta);
        values.put(ReasonGroup.ALLY_SYNERGY, allySynergy);
        values.put(ReasonGroup.SELF_SYNERGY, selfSynergy);
        values.put(ReasonGroup.CONTEXT, context);
        values.put(ReasonGroup.TEAM_COMPOSITION, team);
        return values;
    }

    /** 판금 장화 (1위) */
    private static Map<ReasonGroup, Double> platedSteelcaps() {
        return contributions(2.4492, 1.0247, 0.5034, 0.2887, 0.1592, 0.0006, -0.0097);
    }

    /** 스테락의 도전 (5위) — BUILD가 유독 낮은 아이템이다. */
    private static Map<ReasonGroup, Double> sterakspage() {
        return contributions(0.2199, 0.8528, 0.4358, 0.3167, 0.0008, 0.0149, -0.0022);
    }

    /** 헤르메스의 발걸음 (4위) — 음수가 둘 있지만 크기가 작다. */
    private static Map<ReasonGroup, Double> mercurysTreads() {
        return contributions(1.3939, 0.3694, 0.1483, 0.2493, -0.0715, -0.1006, -0.0079);
    }

    @Nested
    @DisplayName("문턱을 넘은 묶음을 고른다")
    class Qualified {

        @Test
        @DisplayName("문턱을 넘은 묶음은 셋 이상이어도 모두 남는다 — 몇 개를 보여줄지는 클라이언트가 정한다")
        void select_WhenMoreThanTwoQualify_KeepsThemAll() {
            // given: 판금 장화는 BUILD 55%, COUNTER 23%, PATCH_META 11%로 셋이 문턱을 넘는다
            SelectedReasons reasons = selector.select(platedSteelcaps());

            // then
            assertThat(reasons.qualified()).extracting(GroupWeight::group)
                    .containsExactly(ReasonGroup.BUILD, ReasonGroup.COUNTER, ReasonGroup.PATCH_META);
        }

        @Test
        @DisplayName("큰 순서로 준다")
        void select_OrdersByContributionDescending() {
            SelectedReasons reasons = selector.select(sterakspage());

            assertThat(reasons.qualified()).extracting(GroupWeight::value).isSortedAccordingTo(
                    java.util.Comparator.<Double>reverseOrder());
        }

        @Test
        @DisplayName("노이즈는 순위와 무관하게 빠진다 — 상한을 없앤 것이지 문턱을 없앤 것이 아니다")
        void select_WhenBelowShareFloor_ExcludesRegardlessOfRank() {
            // given: CONTEXT(+0.0006)와 TEAM_COMPOSITION은 양수 총합의 1%도 안 된다
            SelectedReasons reasons = selector.select(platedSteelcaps());

            // then
            assertThat(reasons.qualified()).extracting(GroupWeight::group)
                    .doesNotContain(ReasonGroup.CONTEXT, ReasonGroup.TEAM_COMPOSITION);
        }

        @Test
        @DisplayName("지분 10% 미만은 3등이어도 빠진다")
        void select_WhenThirdButBelowFloor_IsExcluded() {
            // given: ALLY 0.09는 양수 총합 1.09의 8.3%다
            Map<ReasonGroup, Double> justBelow = contributions(0.8, 0.2, 0.0, 0.09, 0.0, 0.0, 0.0);

            // when & then
            assertThat(selector.select(justBelow).qualified()).extracting(GroupWeight::group)
                    .containsExactly(ReasonGroup.BUILD, ReasonGroup.COUNTER);
        }

        @Test
        @DisplayName("양수 기여가 하나라도 있으면 반드시 무언가 남는다 — 이유 없는 추천은 없다")
        void select_WhenAnyGroupIsPositive_AlwaysExplains() {
            // 이 불변식은 문턱값에 달려 있다. 묶음이 7개면 지분 합이 100%라 최댓값은 항상
            // 14.3% 이상이고, 그래서 10% 문턱은 절대 전부를 막지 못한다. 문턱을 이 선 위로
            // 올리면 설명 없는 추천이 생기는데, 그때 이 테스트가 걸린다.
            Map<ReasonGroup, Double> spreadThin =
                    contributions(0.15, 0.14, 0.14, 0.14, 0.14, 0.14, 0.14);

            SelectedReasons reasons = selector.select(spreadThin);

            assertThat(reasons.qualified()).isNotEmpty();
            assertThat(reasons.qualified().getFirst().group()).isEqualTo(ReasonGroup.BUILD);
        }

        @Test
        @DisplayName("점수를 끌어내린 묶음은 근거로 쓰지 않는다")
        void select_NeverPicksAGroupThatLoweredTheScore() {
            SelectedReasons reasons = selector.select(mercurysTreads());

            assertThat(reasons.qualified())
                    .allSatisfy(weight -> assertThat(weight.value()).isPositive());
        }

        @Test
        @DisplayName("전부 0 이하면 남는 것이 없다")
        void select_WhenNothingIsPositive_HasNothingQualified() {
            Map<ReasonGroup, Double> allNegative =
                    contributions(-0.5, -0.2, -0.1, -0.1, -0.1, -0.1, -0.1);

            assertThat(selector.select(allNegative).qualified()).isEmpty();
        }

        @Test
        @DisplayName("값이 같으면 매번 같은 순서를 낸다 — 순서가 흔들리면 응답이 흔들린다")
        void select_BreaksTiesDeterministically() {
            Map<ReasonGroup, Double> tied = contributions(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);

            assertThat(selector.select(tied).qualified())
                    .isEqualTo(selector.select(tied).qualified());
        }
    }

    @Nested
    @DisplayName("모든 묶음을 같은 문턱으로 본다")
    class EveryGroupSharesTheSameBar {

        @Test
        @DisplayName("패치 메타도 다른 묶음과 같은 기준으로 뽑힌다")
        void select_AppliesTheSameFloorToPatchMeta() {
            // given: 스테락의 도전에서 PATCH_META는 양수 총합의 24%다
            SelectedReasons reasons = selector.select(sterakspage());

            // then
            assertThat(reasons.qualified()).extracting(GroupWeight::group)
                    .contains(ReasonGroup.PATCH_META);
        }

        @Test
        @DisplayName("지분이 작으면 어느 묶음이든 똑같이 빠진다")
        void select_WhenShareIsSmall_ExcludesAnyGroupAlike() {
            Map<ReasonGroup, Double> tinyPatchMeta =
                    contributions(2.0, 1.0, 0.05, 0.05, 0.05, 0.05, 0.05);

            assertThat(selector.select(tinyPatchMeta).qualified()).extracting(GroupWeight::group)
                    .containsExactly(ReasonGroup.BUILD, ReasonGroup.COUNTER);
        }
    }
}
