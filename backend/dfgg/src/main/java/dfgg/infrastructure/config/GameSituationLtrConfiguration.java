package dfgg.infrastructure.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.v3.feature.FeatureExtractionPipeline;
import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.FeatureSchemaFingerprint;
import dfgg.application.recommend.v3.ranker.CoreRanking;
import dfgg.application.recommend.v3.ranker.GameSituationRanking;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.application.recommend.v3.ranker.LambdaMartRanker;
import dfgg.application.recommend.v3.ranker.LightGbmModelLoader;
import dfgg.application.recommend.v3.ranker.TreeShapCalculator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * 게임 상황까지 읽는 새 Core 모델을 기동 시점에 읽는다. 지금 모델({@link LtrModelConfiguration})과 달리 없어도 뜬다.
 * <p>
 * 없어도 뜨는 이유: 모델 묶음을 쓰는 슬롯의 경로는 모든 묶음이 같이 쓰는 파일에서 온다.
 * 새 모델이 없는 옛 묶음으로 배포하거나 되돌릴 때도 경로는 설정돼 있으므로, 파일이 없다고 멈추면 그 배포가 실패한다.
 * <p>
 * 파일이 있는데 계약이 틀리면 뜨지 않는다. 틀린 모델을 "없음"으로 넘기면
 * 새 API가 지금 모델로 답하고 있다는 것을 아무도 모르게 된다.
 */
@Configuration
public class GameSituationLtrConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GameSituationLtrConfiguration.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Bean
    public GameSituationRanking gameSituationRanking(
            @Value("${recommendation.game-situation-ltr.model-path:}") String modelPath,
            ResourceLoader resources,
            FeatureExtractionPipeline pipeline) {
        if (modelPath.isBlank()) {
            log.warn("게임 상황 Core 모델 경로가 설정되지 않았습니다. 새 추천 API는 지금 모델로 답합니다.");
            return GameSituationRanking.absent();
        }
        // 지금 모델의 경로와 같은 규칙이다. 접두사 없는 경로는 classpath로 본다.
        Resource resource = resources.getResource(modelPath.contains(":") ? modelPath : "classpath:" + modelPath);
        if (!resource.exists()) {
            log.warn("게임 상황 Core 모델 파일이 없습니다: {} ({}). 새 추천 API는 지금 모델로 답합니다.",
                    modelPath, resource.getDescription());
            return GameSituationRanking.absent();
        }
        byte[] content = read(resource, modelPath);
        ModelContract contract = ModelContract.of(fingerprintOf(content));
        GradientBoostedTrees model = LightGbmModelLoader.load(
                new ByteArrayInputStream(content), contract.names(), contract.fingerprint());
        return GameSituationRanking.available(rankingOf(model, contract, pipeline));
    }

    private byte[] read(Resource resource, String modelPath) {
        try (var input = resource.getInputStream()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("게임 상황 Core 모델을 읽지 못했습니다: " + modelPath, exception);
        }
    }

    /** 모델 파일에 적힌 지문. 읽지 못하면 빈 값을 돌려주고, 판단은 로더의 검사에 맡긴다. */
    private String fingerprintOf(byte[] content) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(content);
            return root == null ? "" : root.path("schema_fingerprint").asText("");
        } catch (IOException exception) {
            return "";
        }
    }

    private CoreRanking rankingOf(GradientBoostedTrees model, ModelContract contract, FeatureExtractionPipeline pipeline) {
        return new CoreRanking(
                new LambdaMartRanker(pipeline, model, contract.fingerprint()),
                new TreeShapCalculator(model, FeatureName.values().length));
    }

    /**
     * 새 모델 자리에서 받아 주는 계약. 지금 feature 목록 전체이거나, dev에 먼저 올라간 상황 v1(앞 70칸)이다.
     * <p>
     * 상황 v1 모델은 뒤에 붙은 칸을 읽지 않으므로 목록이 길어져도 점수가 같다. 그래서 feature를 더한 코드를
     * 새 모델보다 먼저 배포할 수 있다. 둘 다 아니면 전체 계약으로 읽어 로더가 지문이 다르다고 기동을 멈춘다.
     */
    private record ModelContract(List<String> names, String fingerprint) {

        static ModelContract of(String modelFingerprint) {
            if (FeatureSchemaFingerprint.SITUATION_V1.equals(modelFingerprint)) {
                return new ModelContract(FeatureName.situationV1ExportNames(), FeatureSchemaFingerprint.SITUATION_V1);
            }
            return new ModelContract(FeatureName.exportNames(), FeatureName.schemaFingerprint());
        }
    }
}
