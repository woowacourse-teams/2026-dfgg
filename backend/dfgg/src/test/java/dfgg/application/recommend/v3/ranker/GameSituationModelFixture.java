package dfgg.application.recommend.v3.ranker;

import dfgg.application.recommend.v3.feature.FeatureName;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * 게임 상황까지 읽는 새 모델의 자리에 넣을 작은 모델 파일을 테스트가 실행 중에 만든다.
 *
 * <p>학습한 모델은 저장소에 두지 않고, 저장소의 씨앗 모델은 기준 계약(앞쪽 feature만)이라 새 모델 자리에 쓸 수 없다.
 * 이름과 지문을 {@link FeatureName}에서 가져오므로 feature 목록이 바뀌어도 이 도우미는 고치지 않는다.
 */
public final class GameSituationModelFixture {

    private static final String FILE_NAME = "game-situation-model.json";

    private GameSituationModelFixture() {
    }

    /** 전체 계약(지금 feature 목록 전체와 그 지문)에 맞는 모델을 폴더에 쓰고 그 경로를 돌려준다. */
    public static Path write(Path directory, String trees) {
        String names = FeatureName.exportNames().stream()
                .map(name -> "\"" + name + "\"")
                .collect(Collectors.joining(", "));
        String model = """
                {
                  "schema_fingerprint": "%s",
                  "feature_names": [%s],
                  "objective": "lambdarank",
                  "trees": %s
                }
                """.formatted(FeatureName.schemaFingerprint(), names, trees);
        Path file = directory.resolve(FILE_NAME);
        try {
            Files.writeString(file, model);
        } catch (IOException exception) {
            throw new UncheckedIOException("테스트용 모델을 쓰지 못했습니다: " + file, exception);
        }
        return file;
    }

    /**
     * 상황 값과 후보 값을 함께 보는 트리 하나. 상황 값은 한 요청의 모든 후보가 같아서, 상황 값만 보는 트리로는 순위가 바뀌지 않는다.
     * <p>
     * 상황 값이 threshold 이하이거나 비어 있으면 후보 값이 candidateThreshold 이하인 후보가 높은 점수(2.0)를 받고,
     * 넘으면 후보 값이 candidateThreshold를 넘거나 비어 있는 후보가 높은 점수를 받는다.
     */
    public static String interaction(
            FeatureName situation, double threshold, FeatureName candidate, double candidateThreshold) {
        return """
                [{"split_feature": [%d, %d, %d], "threshold": [%s, %s, %s], "default_left": [true, false, false],
                  "left": [1, -1, -3], "right": [2, -2, -4], "leaf_value": [2.0, 1.0, 1.0, 2.0],
                  "node_cover": [10.0, 5.0, 5.0], "leaf_cover": [3.0, 2.0, 2.0, 3.0]}]
                """.formatted(situation.ordinal(), candidate.ordinal(), candidate.ordinal(),
                threshold, candidateThreshold, candidateThreshold);
    }

    /** feature 하나만 보는 트리 하나. 값이 threshold 이하이거나 비어 있으면 atOrBelow, 넘으면 above를 점수로 낸다. */
    public static String stump(FeatureName feature, double threshold, double atOrBelow, double above) {
        return """
                [{"split_feature": [%d], "threshold": [%s], "default_left": [true],
                  "left": [-1], "right": [-2], "leaf_value": [%s, %s],
                  "node_cover": [10.0], "leaf_cover": [6.0, 4.0]}]
                """.formatted(feature.ordinal(), threshold, atOrBelow, above);
    }
}
