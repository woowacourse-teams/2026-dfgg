package dfgg.application.recommend.v3.feature;

import java.util.EnumMap;
import java.util.Map;

/**
 * feature를 사용자가 읽을 수 있는 묶음으로 접는다.
 *
 * <p>SHAP은 가산적이라 그룹 기여도는 구성원 기여도의 <b>합</b>이고, 그 합에 기준값을 더하면
 * 여전히 예측값이 된다. 그래서 그룹 단위로 말해도 수학적으로 정확하다.
 * {@code counter_lift_max +0.31} 같은 원시 값은 디버깅용이지 추천 이유가 아니다.
 *
 * <p>이름 접두사로 자동 분류하지 않는다. {@code ALLY_SCORE_MAX}(아군 시너지 점수)와
 * {@code ALLY_TANK_COUNT}(아군 조합 구성)는 접두사가 같지만 뜻이 다르다.
 */
public enum ReasonGroup {

    /** 이 챔피언이 원래 이 아이템을 사는가. */
    BUILD,
    /** 이미 산 아이템들과 맞물리는가. */
    SELF_SYNERGY,
    /** 아군 챔피언들과 맞물리는가. */
    ALLY_SYNERGY,
    /** 상대 챔피언 때문에 가치가 오르는가. */
    COUNTER,
    /** 최근 패치에서 뜨는가/지는가. */
    PATCH_META,
    /** 양 팀 조합이 어떤 성향인가. */
    TEAM_COMPOSITION,
    /** 포지션·티어·진행도 등 질의 자체의 맥락. */
    CONTEXT;

    private static final Map<FeatureName, ReasonGroup> GROUPS = buildGroups();

    public static ReasonGroup of(FeatureName feature) {
        return GROUPS.get(feature);
    }

    private static Map<FeatureName, ReasonGroup> buildGroups() {
        Map<FeatureName, ReasonGroup> groups = new EnumMap<>(FeatureName.class);

        assign(groups, BUILD,
                FeatureName.SOURCE_BUILD, FeatureName.BUILD_SCORE, FeatureName.BUILD_RANK,
                FeatureName.BUILD_BACKOFF_LEVEL, FeatureName.CHAMPION_BASE_RATE_ALL);

        assign(groups, SELF_SYNERGY,
                FeatureName.SOURCE_SELF_SYNERGY, FeatureName.SELF_SYNERGY_SCORE,
                FeatureName.SELF_SYNERGY_RANK, FeatureName.SELF_SYNERGY_BACKOFF_LEVEL);

        assign(groups, ALLY_SYNERGY,
                FeatureName.SOURCE_ALLY_SYNERGY, FeatureName.ALLY_SYNERGY_SCORE,
                FeatureName.ALLY_SYNERGY_RANK, FeatureName.ALLY_SYNERGY_BACKOFF_LEVEL,
                FeatureName.ALLY_SCORE_MAX, FeatureName.ALLY_SCORE_MEAN,
                FeatureName.ALLY_SCORE_SUM, FeatureName.ALLY_SCORE_TOP1, FeatureName.ALLY_SCORE_TOP2);

        assign(groups, COUNTER,
                FeatureName.SOURCE_COUNTER, FeatureName.COUNTER_SCORE, FeatureName.COUNTER_RANK,
                FeatureName.COUNTER_BACKOFF_LEVEL, FeatureName.COUNTER_LIFT_MAX,
                FeatureName.COUNTER_LIFT_MEAN, FeatureName.COUNTER_LIFT_TOP1,
                FeatureName.COUNTER_LIFT_TOP2, FeatureName.COUNTER_PAIR_PROBABILITY_MAX);

        assign(groups, PATCH_META,
                FeatureName.CHAMPION_BASE_RATE_RECENT, FeatureName.CHAMPION_BASE_RATE_RECENT_VS_ALL,
                FeatureName.ITEM_PICK_RATE_CURRENT_PATCH, FeatureName.ITEM_PICK_RATE_DELTA_PREV_PATCH,
                FeatureName.ITEM_PICK_RATE_DELTA_3PATCH, FeatureName.ITEM_WIN_RATE_CURRENT_PATCH,
                FeatureName.ITEM_WIN_RATE_DELTA_PREV_PATCH);

        assign(groups, TEAM_COMPOSITION,
                FeatureName.ENEMY_TANK_COUNT, FeatureName.ENEMY_MAGE_COUNT,
                FeatureName.ENEMY_MARKSMAN_COUNT, FeatureName.ENEMY_ASSASSIN_COUNT,
                FeatureName.ENEMY_FIGHTER_COUNT, FeatureName.ALLY_TANK_COUNT,
                FeatureName.ALLY_MAGE_COUNT, FeatureName.ALLY_MARKSMAN_COUNT,
                FeatureName.ALLY_SUPPORT_COUNT);

        assign(groups, CONTEXT,
                FeatureName.PURCHASED_ITEM_COUNT, FeatureName.POSITION_TOP,
                FeatureName.POSITION_JUNGLE, FeatureName.POSITION_MID, FeatureName.POSITION_BOTTOM,
                FeatureName.POSITION_SUPPORT, FeatureName.TIER_ORDINAL);

        return groups;
    }

    private static void assign(
            Map<FeatureName, ReasonGroup> groups, ReasonGroup group, FeatureName... features) {
        for (FeatureName feature : features) {
            ReasonGroup previous = groups.put(feature, group);
            if (previous != null) {
                throw new IllegalStateException("feature가 두 그룹에 들어갔습니다: " + feature);
            }
        }
    }
}
