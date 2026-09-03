package dfgg.application.recommend.v3.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.recommend.v3.feature.ReasonGroup;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 그룹 기여도 중 사용자에게 설명할 것을 고른다.
 * <p>
 * 7개를 다 늘어놓으면 설명이 아니라 표가 된다.
 * 그렇다고 상위 2개를 그냥 자르면 노이즈까지 설명으로 승격된다
 * — 실제 응답에서 CONTEXT는 +0.0006 같은 값이 나온다.
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
    @DisplayName("무엇을 말할지 고르기")
    class Highlights {

        @Test
        @DisplayName("가장 크게 밀어올린 두 묶음을 고른다")
        void select_PicksTheTwoLargestQualifyingGroups() {
            SelectedReasons reasons = selector.select(platedSteelcaps(), 1);

            assertThat(reasons.highlights())
                    .extracting(GroupWeight::group)
                    .containsExactly(ReasonGroup.BUILD, ReasonGroup.COUNTER);
        }

        @Test
        @DisplayName("BUILD가 낮은 아이템은 다른 묶음이 올라온다 — 같은 문장이 반복되지 않는다")
        void select_WhenBuildIsWeak_PicksWhatActuallyDroveTheItem() {
            SelectedReasons reasons = selector.select(sterakspage(), 5);

            assertThat(reasons.highlights())
                    .extracting(GroupWeight::group)
                    .containsExactly(ReasonGroup.COUNTER, ReasonGroup.PATCH_META);
        }

        @Test
        @DisplayName("노이즈가 2위여도 고르지 않는다 — 두 번째 자리를 채우려고 아무거나 쓰지 않는다")
        void select_WhenTheSecondLargestIsNegligible_LeavesItOut() {
            // BUILD 하나가 이 아이템을 끌어올렸고 나머지는 사실상 0이다.
            // 상위 2개를 그냥 자르면 CONTEXT(+0.003, 전체의 0.15%)가 이유로 승격된다.
            Map<ReasonGroup, Double> onlyBuildMatters =
                    contributions(2.0, 0.0, 0.0, 0.0, 0.0, 0.003, -0.01);

            SelectedReasons reasons = selector.select(onlyBuildMatters, 1);

            assertThat(reasons.highlights())
                    .extracting(GroupWeight::group)
                    .containsExactly(ReasonGroup.BUILD);
        }

        @Test
        @DisplayName("실제 응답에서도 노이즈 묶음은 들어가지 않는다")
        void select_ForARealResponse_ExcludesNoiseGroups() {
            // 칠흑의 양날 도끼. CONTEXT는 +0.0030으로 양수지만 전체의 0.1%다.
            Map<ReasonGroup, Double> blackCleaver =
                    contributions(1.7262, 0.8037, 0.3531, 0.2871, 0.1109, 0.0030, -0.0053);

            SelectedReasons reasons = selector.select(blackCleaver, 3);

            assertThat(reasons.highlights())
                    .extracting(GroupWeight::group)
                    .doesNotContain(ReasonGroup.CONTEXT, ReasonGroup.TEAM_COMPOSITION);
        }

        @Test
        @DisplayName("최대 두 개까지만 말한다")
        void select_NeverReturnsMoreThanTwoHighlights() {
            assertThat(selector.select(platedSteelcaps(), 1).highlights()).hasSizeLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("양수 기여가 하나라도 있으면 반드시 말한다 — 이유 없는 추천은 없다")
        void select_WhenAnyGroupIsPositive_AlwaysExplains() {
            // 이 불변식은 문턱값에 달려 있다. 묶음이 7개면 지분 합이 100%라 최댓값은 항상
            // 14.3% 이상이고, 그래서 10% 문턱은 절대 전부를 막지 못한다. 문턱을 이 선 위로
            // 올리면 설명 없는 추천이 생기는데, 그때 이 테스트가 걸린다.
            Map<ReasonGroup, Double> spreadThin =
                    contributions(0.15, 0.14, 0.14, 0.14, 0.14, 0.14, 0.14);

            SelectedReasons reasons = selector.select(spreadThin, 1);

            assertThat(reasons.highlights()).isNotEmpty();
            assertThat(reasons.highlights().getFirst().group()).isEqualTo(ReasonGroup.BUILD);
        }

        @Test
        @DisplayName("점수를 끌어내린 묶음은 이유로 쓰지 않는다")
        void select_NeverHighlightsAGroupThatLoweredTheScore() {
            SelectedReasons reasons = selector.select(mercurysTreads(), 4);

            assertThat(reasons.highlights())
                    .allSatisfy(weight -> assertThat(weight.value()).isPositive());
        }

        @Test
        @DisplayName("전부 0 이하면 말할 이유가 없다")
        void select_WhenNothingIsPositive_HasNoHighlight() {
            Map<ReasonGroup, Double> allNegative =
                    contributions(-0.5, -0.2, -0.1, -0.1, -0.1, -0.1, -0.1);

            assertThat(selector.select(allNegative, 5).highlights()).isEmpty();
        }

        @Test
        @DisplayName("값이 같으면 매번 같은 순서를 낸다 — 순서가 흔들리면 문장이 흔들린다")
        void select_BreaksTiesDeterministically() {
            Map<ReasonGroup, Double> tied = contributions(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);

            assertThat(selector.select(tied, 1).highlights())
                    .isEqualTo(selector.select(tied, 1).highlights());
        }
    }

    @Nested
    @DisplayName("모든 묶음을 같은 문턱으로 본다")
    class EveryGroupSharesTheSameBar {

        @Test
        @DisplayName("패치 메타도 다른 묶음과 같은 기준으로 뽑힌다")
        void select_HoldsPatchMetaToTheSameThresholdAsOtherGroups() {
            // 트위스티드 페이트 MID 실제 응답의 리치베인. COUNTER가 1위이고 PATCH_META가
            // 2위(전체의 20.3%)다.
            //
            // 한때 PATCH_META에만 25% 문턱을 걸었다가 되돌렸다. 근거로 삼았던 T14 ablation은
            // "패치 feature 없이 재학습해도 정확도가 비슷하다"는 중복성 이야기지, "이 아이템의
            // 점수를 밀어올렸는가"와는 다른 질문이다. 게다가 T15의 permutation 중요도에서
            // PATCH_META(+0.0148)는 ALLY_SYNERGY(+0.0116)보다 높은데, ALLY는 기본 문턱으로
            // 통과시키면서 PATCH만 막는 것은 앞뒤가 맞지 않았다.
            Map<ReasonGroup, Double> lichBane =
                    contributions(0.5060, 1.0862, 0.5954, 0.4046, 0.1938, 0.1456, -0.0040);

            SelectedReasons reasons = selector.select(lichBane, 3);

            assertThat(reasons.highlights())
                    .extracting(GroupWeight::group)
                    .containsExactly(ReasonGroup.COUNTER, ReasonGroup.PATCH_META);
        }

        @Test
        @DisplayName("지분이 작으면 어느 묶음이든 똑같이 빠진다")
        void select_DropsAnyGroupBelowTheShare() {
            // 영겁의 지팡이(실제 응답). PATCH_META는 7.4%로 문턱 미달이다.
            Map<ReasonGroup, Double> archangels =
                    contributions(2.8658, 0.7907, 0.3277, 0.3022, 0.1221, 0.0265, 0.0060);

            SelectedReasons reasons = selector.select(archangels, 1);

            assertThat(reasons.highlights())
                    .extracting(GroupWeight::group)
                    .containsExactly(ReasonGroup.BUILD, ReasonGroup.COUNTER);
        }
    }

    @Nested
    @DisplayName("단서(caveat)는 아낀다")
    class Caveat {

        @Test
        @DisplayName("음수가 작으면 단서를 달지 않는다 — 추천해놓고 깎는 문장이 된다")
        void select_WhenTheNegativeIsSmall_AddsNoCaveat() {
            // 헤르메스의 발걸음(4위). CONTEXT −0.1006은 양수 총합 2.16의 4.7%에 불과하다.
            SelectedReasons reasons = selector.select(mercurysTreads(), 4);

            assertThat(reasons.caveat()).isEmpty();
        }

        @Test
        @DisplayName("하위 순위이면서 음수가 클 때만 단서를 단다")
        void select_WhenRankIsLowAndTheNegativeIsLarge_AddsACaveat() {
            Map<ReasonGroup, Double> heldBack =
                    contributions(1.0, 0.2, 0.0, 0.0, 0.0, -0.6, 0.0);

            SelectedReasons reasons = selector.select(heldBack, 5);

            assertThat(reasons.caveat()).isPresent();
            assertThat(reasons.caveat().orElseThrow().group()).isEqualTo(ReasonGroup.CONTEXT);
        }

        @Test
        @DisplayName("상위 순위면 음수가 커도 단서를 달지 않는다 — 1위 추천에 붙일 말이 아니다")
        void select_WhenRankIsHigh_AddsNoCaveatEvenForALargeNegative() {
            Map<ReasonGroup, Double> heldBack =
                    contributions(1.0, 0.2, 0.0, 0.0, 0.0, -0.6, 0.0);

            assertThat(selector.select(heldBack, 1).caveat()).isEmpty();
        }

        @Test
        @DisplayName("단서는 가장 크게 끌어내린 묶음 하나만 단다")
        void select_ReportsOnlyTheLargestNegative() {
            Map<ReasonGroup, Double> twoNegatives =
                    contributions(1.0, 0.2, 0.0, 0.0, 0.0, -0.6, -0.5);

            SelectedReasons reasons = selector.select(twoNegatives, 5);

            assertThat(reasons.caveat().orElseThrow().group()).isEqualTo(ReasonGroup.CONTEXT);
        }
    }
}
