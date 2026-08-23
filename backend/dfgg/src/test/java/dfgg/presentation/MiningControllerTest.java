package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.application.mining.EmbeddingTrainingResult;
import dfgg.application.mining.MiningTriggerService;
import dfgg.application.mining.SequentialPatternMiningResult;
import dfgg.domain.embedding.TrainingConfig;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MiningControllerTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private MiningTriggerService miningTriggerService;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("기본 하이퍼파라미터로 임베딩 학습을 트리거한다")
    void trainEmbeddings_WhenCalledWithDefaults_TriggersTrainingWithDefaultHyperparameters() {
        // given
        when(miningTriggerService.trainEmbeddings(eq(2.0), any(TrainingConfig.class), eq("sgns-v1")))
                .thenReturn(new EmbeddingTrainingResult(11L, "sgns-v1"));

        // when & then
        given().queryParam("algorithmVersion", "sgns-v1")
                .when().post("/admin/mining/embeddings")
                .then()
                .statusCode(200)
                .body("persistedEmbeddingCount", equalTo(11))
                .body("algorithmVersion", equalTo("sgns-v1"));

        verify(miningTriggerService).trainEmbeddings(
                eq(2.0), eq(new TrainingConfig(64, 5, 20, 0.025, 42L)), eq("sgns-v1")
        );
    }

    @Test
    @DisplayName("요청 파라미터로 하이퍼파라미터를 지정한다")
    void trainEmbeddings_WhenParamsProvided_OverridesHyperparameters() {
        // given
        when(miningTriggerService.trainEmbeddings(eq(3.5), any(TrainingConfig.class), eq("sgns-v2")))
                .thenReturn(new EmbeddingTrainingResult(20L, "sgns-v2"));

        // when & then
        given()
                .queryParam("algorithmVersion", "sgns-v2")
                .queryParam("winWeight", "3.5")
                .queryParam("dimensions", "16")
                .queryParam("negativeSamples", "8")
                .queryParam("epochs", "50")
                .queryParam("learningRate", "0.01")
                .queryParam("randomSeed", "7")
                .when().post("/admin/mining/embeddings")
                .then()
                .statusCode(200);

        verify(miningTriggerService).trainEmbeddings(
                eq(3.5), eq(new TrainingConfig(16, 8, 50, 0.01, 7L)), eq("sgns-v2")
        );
    }

    @Test
    @DisplayName("algorithmVersion이 없으면 요청을 거부한다")
    void trainEmbeddings_WhenAlgorithmVersionMissing_RejectsRequest() {
        // when & then
        given()
                .when().post("/admin/mining/embeddings")
                .then()
                .statusCode(400);

        verifyNoInteractions(miningTriggerService);
    }

    @Test
    @DisplayName("기본 큐 타입과 최소 지지도로 순차 패턴 마이닝을 트리거한다")
    void minePatterns_WhenCalledWithDefaults_TriggersMiningWithDefaultQueueTypeAndMinSupport() {
        // given
        when(miningTriggerService.mineSequentialPatterns("RANKED_SOLO_5x5", 10, "prefixspan-v1"))
                .thenReturn(new SequentialPatternMiningResult(5, 42L, "prefixspan-v1"));

        // when & then
        given().queryParam("algorithmVersion", "prefixspan-v1")
                .when().post("/admin/mining/patterns")
                .then()
                .statusCode(200)
                .body("scopeCount", equalTo(5))
                .body("persistedPatternCount", equalTo(42))
                .body("algorithmVersion", equalTo("prefixspan-v1"));

        verify(miningTriggerService).mineSequentialPatterns("RANKED_SOLO_5x5", 10, "prefixspan-v1");
    }

    @Test
    @DisplayName("요청 파라미터로 큐 타입과 최소 지지도를 지정한다")
    void minePatterns_WhenParamsProvided_OverridesQueueTypeAndMinSupport() {
        // given
        when(miningTriggerService.mineSequentialPatterns("RANKED_FLEX_SR", 3, "prefixspan-v2"))
                .thenReturn(new SequentialPatternMiningResult(2, 9L, "prefixspan-v2"));

        // when & then
        given()
                .queryParam("algorithmVersion", "prefixspan-v2")
                .queryParam("queueType", "RANKED_FLEX_SR")
                .queryParam("minSupport", "3")
                .when().post("/admin/mining/patterns")
                .then()
                .statusCode(200);

        verify(miningTriggerService).mineSequentialPatterns("RANKED_FLEX_SR", 3, "prefixspan-v2");
    }

    @Test
    @DisplayName("algorithmVersion이 없으면 마이닝 요청도 거부한다")
    void minePatterns_WhenAlgorithmVersionMissing_RejectsRequest() {
        // when & then
        given()
                .when().post("/admin/mining/patterns")
                .then()
                .statusCode(400);

        verifyNoInteractions(miningTriggerService);
    }
}
