package dfgg.application.recommend.v3.feature;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * LTR feature 스키마의 단일 원천. 선언 순서가 곧 벡터 인덱스이고, 그 순서로 Python과 Java가 합의한다.
 * <p>
 * 중간에 삽입하거나 재배치하지 말 것.
 * 기존 모델은 인덱스로 학습돼 있어서 순서가 바뀌면 전혀 다른 feature를 읽으면서도 조용히 동작한다.
 * 추가는 반드시 맨 뒤에 하고, {@link FeatureSchemaFingerprint}를 갱신한 뒤 모델을 다시 학습한다.
 * <p>
 * 모든 feature는 numeric이다.
 * LightGBM의 categorical split은 Java 추론에서 다루지 않기로 해 범주형은 one-hot으로 편다.
 */
public enum FeatureName {

    // ─── 어느 generator가 이 후보를 찾았는가 (0/1) ───────────────────────────
    SOURCE_BUILD,
    SOURCE_SELF_SYNERGY,
    SOURCE_ALLY_SYNERGY,
    SOURCE_COUNTER,

    // ─── Build: "지금 build에서 다음에 무엇을 사는가" ────────────────────────
    BUILD_SCORE,
    BUILD_RANK,
    /**
     * 0=정확 prefix, 1=마지막 아이템 전개, 2=챔피언 전반. 클수록 근거가 옅다.
     */
    BUILD_BACKOFF_LEVEL,

    // ─── Self-Synergy: "이 아이템이 내 챔피언과 맞는가" ──────────────────────
    SELF_SYNERGY_SCORE,
    SELF_SYNERGY_RANK,
    SELF_SYNERGY_BACKOFF_LEVEL,

    // ─── Ally-Synergy: "우리 팀 조합 때문에 좋은가" ─────────────────────────
    ALLY_SYNERGY_SCORE,
    ALLY_SYNERGY_RANK,
    ALLY_SYNERGY_BACKOFF_LEVEL,
    ALLY_SCORE_MAX,
    ALLY_SCORE_MEAN,
    ALLY_SCORE_SUM,
    ALLY_SCORE_TOP1,
    ALLY_SCORE_TOP2,

    // ─── Counter: "적 조합 때문에 가치가 오르는가" ──────────────────────────
    COUNTER_SCORE,
    COUNTER_RANK,
    COUNTER_BACKOFF_LEVEL,
    COUNTER_LIFT_MAX,
    COUNTER_LIFT_MEAN,
    COUNTER_LIFT_TOP1,
    COUNTER_LIFT_TOP2,
    /**
     * 스무딩 전 {@code P(item | 나, 적)}의 최댓값. lift와 따로 두는 이유는
     * "원래 안 사는데 lift만 높은 후보"를 모델이 구분할 수 있어야 하기 때문이다.
     */
    COUNTER_PAIR_PROBABILITY_MAX,

    // ─── 챔피언 base rate: 이번 작업의 실패 지표를 가르는 신호 ──────────────
    /**
     * {@code P(item | 내 챔피언)}. 야스오의 존야 base rate는 9,343판 중 0회로 0.000%다.
     * lift가 아무리 높아도 이 값이 바닥이면 모델이 눌러줄 수 있어야 하고,
     * 그게 AD/AP hard filter 없이 문제를 푸는 지점이다.
     */
    CHAMPION_BASE_RATE_ALL,
    CHAMPION_BASE_RATE_RECENT,
    /**
     * 최근/전체 비율. 갓 버프돼 급등 중인지, 사양길인지를 한 값으로 표현한다.
     */
    CHAMPION_BASE_RATE_RECENT_VS_ALL,

    // ─── Meta: 패치별 인기·승률과 그 변화 ───────────────────────────────────
    ITEM_PICK_RATE_CURRENT_PATCH,
    ITEM_PICK_RATE_DELTA_PREV_PATCH,
    ITEM_PICK_RATE_DELTA_3PATCH,
    ITEM_WIN_RATE_CURRENT_PATCH,
    ITEM_WIN_RATE_DELTA_PREV_PATCH,

    // ─── Team: 양 팀 조합 구성 ──────────────────────────────────────────────
    ENEMY_TANK_COUNT,
    ENEMY_MAGE_COUNT,
    ENEMY_MARKSMAN_COUNT,
    ENEMY_ASSASSIN_COUNT,
    ENEMY_FIGHTER_COUNT,
    ALLY_TANK_COUNT,
    ALLY_MAGE_COUNT,
    ALLY_MARKSMAN_COUNT,
    ALLY_SUPPORT_COUNT,

    // ─── Game State ────────────────────────────────────────────────────────
    PURCHASED_ITEM_COUNT,
    POSITION_TOP,
    POSITION_JUNGLE,
    POSITION_MID,
    POSITION_BOTTOM,
    POSITION_SUPPORT,
    TIER_ORDINAL,

    // ═══ 여기까지가 기준 계약이다. 지금 운영 모델은 위 feature만 읽는다 (baselineExportNames) ═══

    // ─── Game Situation: 상대의 아이템 — "상대가 무엇을 들고 있는가" ─────────
    ENEMY_CORE_GOLD_SUM,
    ENEMY_SPELL_DAMAGE_GOLD_SHARE,
    ENEMY_ARMOR_GOLD_SHARE,
    ENEMY_SPELL_BLOCK_GOLD_SHARE,

    // ─── Game Situation: 상대 중 누가 컸나 ──────────────────────────────────
    ENEMY_KILL_SUM,
    ENEMY_MAGE_KILL_SHARE,
    ENEMY_ASSASSIN_KILL_SHARE,
    ENEMY_MARKSMAN_KILL_SHARE,
    ENEMY_TOP_KILLER_KILL_SHARE,

    // ─── Game Situation: 맞라인 ────────────────────────────────────────────
    LANE_LEVEL_LEAD,
    LANE_OPPONENT_KILLS,
    LANE_CORE_GOLD_LEAD,

    // ─── Game Situation: 내 상태 ───────────────────────────────────────────
    MY_LEVEL_LEAD,
    MY_KILLS,
    MY_DEATHS,
    MY_ASSISTS,

    // ─── Game Situation: 팀 상태 ───────────────────────────────────────────
    TEAM_KILL_LEAD,
    TEAM_ASSIST_LEAD,
    TEAM_LEVEL_LEAD,
    TEAM_CORE_GOLD_LEAD,

    // ═══ 여기까지가 상황 v1 계약이다. dev v5에 올라간 첫 게임 상황 모델은 위 feature만 읽는다 (situationV1ExportNames) ═══

    // ─── Game Situation: 후보가 지금 상황에 맞는가 — 후보 태그 × 위의 상황 값 ────
    // 위의 상황 값은 한 요청의 모든 후보에게 같아서 순서를 바꾸지 못한다. 후보마다 다른 값으로 만든다.
    CANDIDATE_SPELL_BLOCK_X_ENEMY_SPELL_DAMAGE_SHARE,
    CANDIDATE_SPELL_BLOCK_X_ENEMY_MAGE_KILL_SHARE,
    CANDIDATE_ARMOR_X_ENEMY_MARKSMAN_KILL_SHARE,
    CANDIDATE_ARMOR_X_ENEMY_ASSASSIN_KILL_SHARE,
    CANDIDATE_MAGIC_PEN_X_ENEMY_SPELL_BLOCK_SHARE,
    CANDIDATE_ARMOR_PEN_X_ENEMY_ARMOR_SHARE,
    CANDIDATE_DEFENSIVE_X_MY_LEVEL_LEAD,
    ;

    public int index() {
        return ordinal();
    }

    /**
     * LightGBM의 {@code feature_names}에 그대로 들어가는 이름.
     */
    public String exportName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<String> exportNames() {
        return Arrays.stream(values()).map(FeatureName::exportName).toList();
    }

    /**
     * 기준 계약의 이름 목록 — 지금 운영 모델이 학습된 feature들로, 맨 앞부터 {@link #TIER_ORDINAL}까지다.
     * <p>
     * 그 뒤에 feature를 더해도 이 목록은 바뀌지 않는다. 기준 계약으로 학습된 모델은
     * 벡터가 길어져도 이 칸들만 읽으므로 점수가 그대로다.
     */
    public static List<String> baselineExportNames() {
        return exportNames().subList(0, TIER_ORDINAL.ordinal() + 1);
    }

    /**
     * 순서를 포함한 스키마의 지문. feature를 추가·삭제·재배치하면 값이 바뀌고
     * {@link FeatureSchemaFingerprint}와 어긋나 테스트가 깨진다 — 그 실패가
     * "모델을 다시 학습해야 한다"는 신호다.
     */
    public static String schemaFingerprint() {
        return fingerprintOf(exportNames());
    }

    /**
     * 기준 계약의 지문. {@link FeatureSchemaFingerprint#BASELINE}과 어긋나면
     * 앞쪽 feature를 건드린 것이고, 지금 운영 모델이 다른 칸을 읽게 된다.
     */
    public static String baselineSchemaFingerprint() {
        return fingerprintOf(baselineExportNames());
    }

    /**
     * 상황 v1 계약의 이름 목록 — 기준 계약 뒤에 게임 상황 값 20개까지, 맨 앞부터 {@link #TEAM_CORE_GOLD_LEAD}까지다.
     * <p>
     * 첫 게임 상황 모델이 이 목록으로 학습됐다. 그 뒤에 feature를 더해도 이 목록은 바뀌지 않으므로,
     * 벡터가 길어져도 그 모델을 계속 읽을 수 있다.
     */
    public static List<String> situationV1ExportNames() {
        return exportNames().subList(0, TEAM_CORE_GOLD_LEAD.ordinal() + 1);
    }

    /**
     * 상황 v1 계약의 지문. {@link FeatureSchemaFingerprint#SITUATION_V1}과 어긋나면
     * 앞 70칸을 건드린 것이고, 첫 게임 상황 모델이 다른 칸을 읽게 된다.
     */
    public static String situationV1SchemaFingerprint() {
        return fingerprintOf(situationV1ExportNames());
    }

    private static String fingerprintOf(List<String> exportNames) {
        String ordered = String.join(",", exportNames);
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(ordered.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
