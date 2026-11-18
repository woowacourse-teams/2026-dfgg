package dfgg.application.recommend.v3.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeatureSchemaTest {

    @Test
    @DisplayName("feature 이름의 순서가 곧 벡터 인덱스다 — Python과 Java가 이 순서로 합의한다")
    void ordinal_IsTheVectorIndex() {
        // given & when & then
        for (FeatureName name : FeatureName.values()) {
            assertThat(name.index()).isEqualTo(name.ordinal());
        }
    }

    @Test
    @DisplayName("스키마 지문이 바뀌면 테스트가 깨진다 — feature를 추가·삭제·재배치하면 모델을 다시 학습해야 한다")
    void fingerprint_WhenSchemaChanges_MustBeUpdatedDeliberately() {
        // given: 지문은 순서를 포함한 이름 목록의 해시다.
        //        이 값을 고치는 행위 자체가 "모델 재학습이 필요하다"는 선언이 된다.
        String actual = FeatureName.schemaFingerprint();

        // then
        assertThat(actual).isEqualTo(FeatureSchemaFingerprint.EXPECTED);
    }

    @Test
    @DisplayName("기준 계약의 지문은 바뀌지 않는다 — 앞쪽 feature를 건드리면 지금 운영 모델이 다른 칸을 읽는다")
    void baselineSchemaFingerprint_WhenBaselineFeaturesUntouched_MatchesBaselineConstant() {
        // given: 기준 계약은 지금 운영 모델이 학습된 이름 목록이다. 뒤에 feature를 더해도 이 값은 그대로여야 한다.

        // when
        String actual = FeatureName.baselineSchemaFingerprint();

        // then
        assertThat(actual).isEqualTo(FeatureSchemaFingerprint.BASELINE);
    }

    @Test
    @DisplayName("기준 계약의 이름은 스키마 맨 앞 50개다 — 순서까지 같아야 벡터의 같은 칸을 읽는다")
    void baselineExportNames_WhenSchemaHasMoreFeatures_ReturnsTheFirstFifty() {
        // given
        int baselineCount = 50;

        // when
        var baseline = FeatureName.baselineExportNames();

        // then
        assertThat(baseline).hasSize(baselineCount);
        assertThat(baseline).isEqualTo(FeatureName.exportNames().subList(0, baselineCount));
        assertThat(baseline.getLast()).isEqualTo("tier_ordinal");
    }

    @Test
    @DisplayName("상황 v1 계약의 지문은 바뀌지 않는다 — 앞 70칸을 건드리면 dev v5에 올라간 70칸 모델이 다른 칸을 읽는다")
    void situationV1SchemaFingerprint_WhenFirstSeventyUntouched_MatchesSituationV1Constant() {
        // given: 상황 v1 계약은 게임 상황 값 20개까지 학습한 첫 v5 모델의 이름 목록이다. 뒤에 feature를 더해도 그대로여야 한다.

        // when
        String actual = FeatureName.situationV1SchemaFingerprint();

        // then
        assertThat(actual).isEqualTo(FeatureSchemaFingerprint.SITUATION_V1);
    }

    @Test
    @DisplayName("상황 v1 계약의 이름은 스키마 맨 앞 70개다 — 기준 계약 50개와 게임 상황 값 20개다")
    void situationV1ExportNames_WhenSchemaHasMoreFeatures_ReturnsTheFirstSeventy() {
        // given
        int situationV1Count = 70;

        // when
        var situationV1 = FeatureName.situationV1ExportNames();

        // then
        assertThat(situationV1).hasSize(situationV1Count);
        assertThat(situationV1).isEqualTo(FeatureName.exportNames().subList(0, situationV1Count));
        assertThat(situationV1.getLast()).isEqualTo("team_core_gold_lead");
    }

    @Test
    @DisplayName("후보별 상황 값 7개는 상황 v1 계약 바로 뒤에 온다 — 이 순서가 다음 게임 상황 모델의 칸 번호다")
    void exportNames_WhenReadAfterSituationV1_ListsCandidateSituationFeaturesInOrder() {
        // given
        int situationV1Count = FeatureName.situationV1ExportNames().size();

        // when
        var appended = FeatureName.exportNames().subList(situationV1Count, FeatureName.values().length);

        // then
        assertThat(appended).containsExactly(
                "candidate_spell_block_x_enemy_spell_damage_share",
                "candidate_spell_block_x_enemy_mage_kill_share",
                "candidate_armor_x_enemy_marksman_kill_share",
                "candidate_armor_x_enemy_assassin_kill_share",
                "candidate_magic_pen_x_enemy_spell_block_share",
                "candidate_armor_pen_x_enemy_armor_share",
                "candidate_defensive_x_my_level_lead");
    }

    @Test
    @DisplayName("게임 상황 feature 20개는 기준 계약 바로 뒤에 온다 — 이 순서가 새 모델의 칸 번호다")
    void exportNames_WhenReadAfterBaseline_ListsSituationFeaturesInOrder() {
        // given
        int baselineCount = FeatureName.baselineExportNames().size();

        // when
        var appended = FeatureName.exportNames().subList(baselineCount, FeatureName.situationV1ExportNames().size());

        // then
        assertThat(appended).containsExactly(
                "enemy_core_gold_sum", "enemy_spell_damage_gold_share",
                "enemy_armor_gold_share", "enemy_spell_block_gold_share",
                "enemy_kill_sum", "enemy_mage_kill_share", "enemy_assassin_kill_share",
                "enemy_marksman_kill_share", "enemy_top_killer_kill_share",
                "lane_level_lead", "lane_opponent_kills", "lane_core_gold_lead",
                "my_level_lead", "my_kills", "my_deaths", "my_assists",
                "team_kill_lead", "team_assist_lead", "team_level_lead", "team_core_gold_lead");
    }

    @Test
    @DisplayName("이름은 소문자 스네이크케이스로 내보낸다 — LightGBM feature_names와 그대로 대응한다")
    void featureNames_AreExportedAsSnakeCase() {
        assertThat(FeatureName.SOURCE_BUILD.exportName()).isEqualTo("source_build");
        assertThat(FeatureName.COUNTER_LIFT_MAX.exportName()).isEqualTo("counter_lift_max");
    }

    @Test
    @DisplayName("이름이 중복되지 않는다")
    void exportNames_AreUnique() {
        long distinct = Arrays.stream(FeatureName.values())
                .map(FeatureName::exportName)
                .distinct()
                .count();
        assertThat(distinct).isEqualTo(FeatureName.values().length);
    }

    @Test
    @DisplayName("7개 feature 군을 모두 포함한다 — Build/Self/Ally/Counter/Meta/Team/GameState")
    void schema_CoversEveryFeatureGroup() {
        String names = Arrays.stream(FeatureName.values())
                .map(FeatureName::exportName)
                .collect(Collectors.joining(","));

        assertThat(names)
                .contains("build_score")
                .contains("self_synergy_score")
                .contains("ally_score_max")
                .contains("counter_lift_max")
                .contains("item_pick_rate_current_patch")
                .contains("enemy_tank_count")
                .contains("purchased_item_count");
    }

    @Test
    @DisplayName("counter의 lift·원 확률·base rate가 각각 별도 feature다 — 셋을 구분해야 실패 유형을 잡는다")
    void schema_KeepsPairLiftAndBaseRateSeparate() {
        String names = Arrays.stream(FeatureName.values())
                .map(FeatureName::exportName)
                .collect(Collectors.joining(","));

        assertThat(names)
                .contains("counter_lift_max")
                .contains("counter_pair_probability_max")
                .contains("champion_base_rate_all");
    }

    @Test
    @DisplayName("벡터 길이는 feature 수와 같다")
    void vector_LengthMatchesSchemaSize() {
        FeatureVector vector = FeatureVector.empty();
        assertThat(vector.values()).hasSize(FeatureName.values().length);
    }

    @Test
    @DisplayName("설정하지 않은 feature는 NaN이다 — '값이 0'과 '데이터가 없다'는 다른 정보다")
    void vector_WhenNotSet_IsNaN() {
        // given
        FeatureVector vector = FeatureVector.empty();

        // when & then
        assertThat(vector.get(FeatureName.COUNTER_LIFT_MAX)).isNaN();
    }

    @Test
    @DisplayName("0.0을 명시적으로 넣으면 NaN과 구분된다")
    void vector_WhenExplicitlySetToZero_IsNotNaN() {
        // given
        FeatureVector vector = FeatureVector.empty();

        // when
        vector.set(FeatureName.COUNTER_LIFT_MAX, 0.0);

        // then
        assertThat(vector.get(FeatureName.COUNTER_LIFT_MAX)).isZero().isNotNaN();
    }

    @Test
    @DisplayName("무한대는 거부한다 — 트리 분기에서 조용히 이상한 결과를 낸다")
    void vector_WhenInfinite_ThrowsException() {
        FeatureVector vector = FeatureVector.empty();
        assertThatThrownBy(() -> vector.set(FeatureName.COUNTER_LIFT_MAX, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
