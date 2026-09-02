package dfgg.evaluation;

import dfgg.application.recommend.v3.feature.FeatureName;
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
 */
public final class FeatureSchemaExporter {

    private static final String FILE_NAME = "feature_schema.json";

    public Path export(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path path = directory.resolve(FILE_NAME);

        StringJoiner names = new StringJoiner("\",\"", "[\"", "\"]");
        FeatureName.exportNames().forEach(names::add);

        String json = "{\n"
                + "  \"schema_fingerprint\": \"" + FeatureName.schemaFingerprint() + "\",\n"
                + "  \"feature_count\": " + FeatureName.values().length + ",\n"
                + "  \"feature_names\": " + names + "\n"
                + "}\n";
        Files.writeString(path, json);
        return path;
    }
}
