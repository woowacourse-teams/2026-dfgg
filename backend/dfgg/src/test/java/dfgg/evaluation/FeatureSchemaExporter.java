package dfgg.evaluation;

import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.FeatureSchemaFingerprint;
import dfgg.application.recommend.v3.feature.ReasonGroup;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

/**
 * feature 스키마를 Python이 읽을 수 있게 내보낸다.
 *
 * <p>학습 데이터(JSONL)의 feature는 이름 없는 배열이라, Python 혼자서는 각 칸이 무엇인지 알 수
 * 없다. LightGBM 모델에 {@code feature_names}를 넣으려면 이 파일이 필요하고, 그 이름 순서가
 * 곧 Java {@code FeatureName}의 순서라 T12의 로더가 대조해 어긋남을 잡을 수 있다.
 *
 * <p>지문도 함께 내보낸다. 이름 목록만 비교해도 되지만, 지문 한 값이면 로더가 짧게 검증하고
 * 로그에 남기기도 쉽다.
 *
 * <p>기준 계약(지금 운영 모델이 학습된 앞쪽 feature들)의 칸 수와 지문도 내보낸다. Python이 기준 계약으로
 * 모델을 학습해 내보낼 때 이 값을 그대로 쓴다. 지문 계산을 Python에도 두면 한쪽만 고쳐질 수 있다.
 */
public final class FeatureSchemaExporter {

    private static final String FILE_NAME = "feature_schema.json";

    public Path export(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path path = directory.resolve(FILE_NAME);

        StringJoiner names = new StringJoiner("\",\"", "[\"", "\"]");
        FeatureName.exportNames().forEach(names::add);

        // 묶음 정의를 Java와 Python 양쪽에 두면 한쪽만 고쳐 놓고 분석 결과를 믿게 된다.
        StringJoiner groups = new StringJoiner("\",\"", "[\"", "\"]");
        for (FeatureName feature : FeatureName.values()) {
            groups.add(ReasonGroup.of(feature).name());
        }

        String json = "{\n"
                + "  \"schema_fingerprint\": \"" + FeatureName.schemaFingerprint() + "\",\n"
                + "  \"feature_count\": " + FeatureName.values().length + ",\n"
                + "  \"baseline_schema_fingerprint\": \"" + FeatureSchemaFingerprint.BASELINE + "\",\n"
                + "  \"baseline_feature_count\": " + FeatureName.baselineExportNames().size() + ",\n"
                + "  \"feature_names\": " + names + ",\n"
                + "  \"feature_groups\": " + groups + "\n"
                + "}\n";
        Files.writeString(path, json);
        return path;
    }
}
