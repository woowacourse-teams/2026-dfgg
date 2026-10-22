package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.v3.feature.FeatureName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Python은 JSONL에서 feature 이름을 알 수 없다 — 벡터가 배열일 뿐이다.
 * 학습 스크립트가 모델에 {@code feature_names}를 넣으려면 Java가 스키마를 함께 내보내야 한다.
 */
class FeatureSchemaExporterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode exportAndRead(Path directory) throws IOException {
        Path path = new FeatureSchemaExporter().export(directory);
        return objectMapper.readTree(Files.readString(path));
    }

    @Test
    @DisplayName("feature 이름을 스키마 순서 그대로 내보낸다 — 배열 인덱스가 곧 벡터 인덱스다")
    void export_WritesFeatureNamesInSchemaOrder(@TempDir Path directory) throws IOException {
        // when
        JsonNode schema = exportAndRead(directory);

        // then
        assertThat(schema.get("feature_names")).hasSize(FeatureName.values().length);
        assertThat(schema.get("feature_names").get(0).asText())
                .isEqualTo(FeatureName.values()[0].exportName());
        assertThat(schema.get("feature_names").get(FeatureName.values().length - 1).asText())
                .isEqualTo(FeatureName.values()[FeatureName.values().length - 1].exportName());
    }

    @Test
    @DisplayName("스키마 지문을 함께 내보낸다 — Java 로더가 모델과 대조해 순서 어긋남을 잡는다")
    void export_WritesSchemaFingerprint(@TempDir Path directory) throws IOException {
        JsonNode schema = exportAndRead(directory);

        assertThat(schema.get("schema_fingerprint").asText())
                .isEqualTo(FeatureName.schemaFingerprint());
    }

    @Test
    @DisplayName("디렉터리가 없으면 만든다")
    void export_WhenDirectoryMissing_CreatesIt(@TempDir Path directory) throws IOException {
        // given
        Path nested = directory.resolve("does/not/exist");

        // when
        Path path = new FeatureSchemaExporter().export(nested);

        // then
        assertThat(Files.exists(path)).isTrue();
    }

    @Test
    @DisplayName("파일 이름이 고정돼 있다 — Python이 이 경로를 그대로 읽는다")
    void export_UsesFixedFileName(@TempDir Path directory) throws IOException {
        Path path = new FeatureSchemaExporter().export(directory);

        assertThat(path.getFileName().toString()).isEqualTo("feature_schema.json");
    }

    @Test
    @DisplayName("feature가 어느 묶음에 속하는지도 함께 내보낸다 — Python이 정의를 따로 갖지 않게 한다")
    void export_IncludesTheReasonGroupOfEachFeature(@TempDir Path directory) throws IOException {
        // 그룹 정의가 Java와 Python 양쪽에 있으면 한쪽만 고쳐 놓고 분석 결과를 믿게 된다.
        Path path = new FeatureSchemaExporter().export(directory);

        String json = Files.readString(path);
        assertThat(json).contains("\"feature_groups\"");
        for (dfgg.application.recommend.v3.feature.ReasonGroup group
                : dfgg.application.recommend.v3.feature.ReasonGroup.values()) {
            assertThat(json).contains(group.name());
        }
    }

    @Test
    @DisplayName("묶음 목록의 길이가 feature 개수와 같다 — 하나라도 빠지면 분석에서 사라진다")
    void export_ListsOneGroupPerFeature(@TempDir Path directory) throws IOException {
        Path path = new FeatureSchemaExporter().export(directory);

        String groups = Files.readString(path).split("\"feature_groups\": \\[")[1].split("]")[0];
        assertThat(groups.split(",")).hasSize(FeatureName.values().length);
    }
}
