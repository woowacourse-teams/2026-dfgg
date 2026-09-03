package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.FeatureVector;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrainingRowWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TrainingRow row(int label) {
        FeatureVector vector = FeatureVector.empty();
        vector.set(FeatureName.BUILD_SCORE, 0.81);
        vector.set(FeatureName.CHAMPION_BASE_RATE_ALL, 0.0);
        return new TrainingRow(
                "KR_123#5#2", label, 3031L, vector,
                "KR_123", "16.16", 157, "MID", 2, "train", "test");
    }

    private JsonNode writeAndParse(List<TrainingRow> rows) throws IOException {
        StringWriter out = new StringWriter();
        try (TrainingRowWriter writer = new TrainingRowWriter(out)) {
            for (TrainingRow row : rows) {
                writer.write(row);
            }
        }
        String[] lines = out.toString().strip().split("\n");
        return objectMapper.readTree(lines[0]);
    }

    @Test
    @DisplayName("한 줄에 한 행씩 쓴다 — JSONL이라 스트리밍으로 읽고 쓸 수 있다")
    void write_WhenMultipleRows_WritesOnePerLine() throws IOException {
        // given
        StringWriter out = new StringWriter();

        // when
        try (TrainingRowWriter writer = new TrainingRowWriter(out)) {
            writer.write(row(3));
            writer.write(row(0));
        }

        // then
        assertThat(out.toString().strip().split("\n")).hasSize(2);
    }

    @Test
    @DisplayName("qid·label·features를 담는다 — LightGBM lambdarank가 요구하는 최소 형태다")
    void write_WhenRowWritten_ContainsQidLabelAndFeatures() throws IOException {
        // when
        JsonNode parsed = writeAndParse(List.of(row(3)));

        // then
        assertThat(parsed.get("qid").asText()).isEqualTo("KR_123#5#2");
        assertThat(parsed.get("label").asInt()).isEqualTo(3);
        assertThat(parsed.get("features").isArray()).isTrue();
    }

    @Test
    @DisplayName("feature 배열 길이가 스키마 크기와 같다 — Python이 이 길이를 그대로 신뢰한다")
    void write_WhenRowWritten_FeatureArrayMatchesSchemaSize() throws IOException {
        JsonNode parsed = writeAndParse(List.of(row(3)));
        assertThat(parsed.get("features")).hasSize(FeatureName.values().length);
    }

    @Test
    @DisplayName("NaN을 null로 내보낸다 — JSON에 NaN 리터럴은 없고, Python은 null을 NaN으로 읽는다")
    void write_WhenFeatureIsNaN_WritesNull() throws IOException {
        // given: COUNTER_LIFT_MAX는 설정하지 않아 NaN이다
        JsonNode parsed = writeAndParse(List.of(row(3)));

        // when
        JsonNode counterLift = parsed.get("features").get(FeatureName.COUNTER_LIFT_MAX.index());

        // then
        assertThat(counterLift.isNull()).isTrue();
    }

    @Test
    @DisplayName("명시적으로 넣은 0.0은 null이 아니다 — 결측과 0의 구분이 파일에서도 유지된다")
    void write_WhenFeatureIsExplicitZero_WritesZeroNotNull() throws IOException {
        JsonNode parsed = writeAndParse(List.of(row(3)));

        JsonNode baseRate = parsed.get("features").get(FeatureName.CHAMPION_BASE_RATE_ALL.index());

        assertThat(baseRate.isNull()).isFalse();
        assertThat(baseRate.asDouble()).isZero();
    }

    @Test
    @DisplayName("game split과 patch split을 둘 다 기록한다 — 두 관점으로 평가한다")
    void write_WhenRowWritten_ContainsBothSplits() throws IOException {
        JsonNode parsed = writeAndParse(List.of(row(3)));

        assertThat(parsed.get("split_game").asText()).isEqualTo("train");
        assertThat(parsed.get("split_patch").asText()).isEqualTo("test");
    }

    @Test
    @DisplayName("추적에 필요한 meta를 담는다 — 이상한 예측을 원본 매치까지 되짚을 수 있어야 한다")
    void write_WhenRowWritten_ContainsTraceableMetadata() throws IOException {
        JsonNode parsed = writeAndParse(List.of(row(3)));

        assertThat(parsed.get("match_id").asText()).isEqualTo("KR_123");
        assertThat(parsed.get("patch").asText()).isEqualTo("16.16");
        assertThat(parsed.get("champion_id").asInt()).isEqualTo(157);
        assertThat(parsed.get("position").asText()).isEqualTo("MID");
        assertThat(parsed.get("purchase_step").asInt()).isEqualTo(2);
        assertThat(parsed.get("item_id").asLong()).isEqualTo(3031L);
    }

    @Test
    @DisplayName("feature 이름 순서를 헤더로 함께 내보낸다 — Python이 인덱스 대응을 검증할 수 있다")
    void featureNamesHeader_MatchesSchemaOrder() {
        assertThat(TrainingRowWriter.featureNamesHeader())
                .isEqualTo(FeatureName.exportNames());
    }
}
