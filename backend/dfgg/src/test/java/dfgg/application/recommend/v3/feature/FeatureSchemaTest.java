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
    void schema_KeepsCounterLiftAndBaseRateSeparate() {
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
