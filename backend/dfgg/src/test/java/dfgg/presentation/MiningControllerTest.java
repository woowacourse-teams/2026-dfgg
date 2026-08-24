package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.mining.EmbeddingTrainingResult;
import dfgg.application.mining.SequentialPatternMiningResult;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.embedding.Embedding;
import dfgg.domain.embedding.EmbeddingEntityType;
import dfgg.domain.embedding.EmbeddingRepository;
import dfgg.domain.sequence.MinedSequentialPattern;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import io.restassured.RestAssured;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql("/sql/mining-controller-test-data.sql")
class MiningControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private EmbeddingRepository embeddingRepository;

    @Autowired
    private MinedSequentialPatternRepository minedSequentialPatternRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        RestAssured.port = port;
        embeddingRepository.deleteAllInBatch();
        minedSequentialPatternRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("기본 하이퍼파라미터로 호출하면 기본 차원 수(64)로 학습한 임베딩이 저장된다")
    void trainEmbeddings_WhenCalledWithDefaults_PersistsEmbeddingsWithDefaultDimensions() {
        // given & when
        EmbeddingTrainingResult result = given()
                .queryParam("algorithmVersion", "sgns-default")
                .when().post("/admin/mining/embeddings")
                .then()
                .statusCode(200)
                .extract().as(EmbeddingTrainingResult.class);

        // then
        assertThat(result.algorithmVersion()).isEqualTo("sgns-default");
        assertThat(result.persistedEmbeddingCount()).isGreaterThan(0);

        List<Embedding> saved = embeddingRepository.findAll();
        assertThat(saved).hasSize((int) result.persistedEmbeddingCount());
        assertThat(saved).allSatisfy(embedding -> assertThat(embedding.getVector()).hasSize(64));
        assertThat(saved).anySatisfy(embedding -> {
            assertThat(embedding.getEntityType()).isEqualTo(EmbeddingEntityType.CHAMPION);
            assertThat(embedding.getEntityId()).isEqualTo(1L);
        });
        assertThat(saved).anySatisfy(embedding -> {
            assertThat(embedding.getEntityType()).isEqualTo(EmbeddingEntityType.ITEM);
            assertThat(embedding.getEntityId()).isEqualTo(3071L);
        });
    }

    @Test
    @DisplayName("요청 파라미터로 차원 수를 지정하면 기본값 대신 지정한 차원 수로 학습한다")
    void trainEmbeddings_WhenDimensionsProvided_OverridesDefaultDimensions() {
        // given & when
        given()
                .queryParam("algorithmVersion", "sgns-override")
                .queryParam("dimensions", "4")
                .queryParam("epochs", "3")
                .when().post("/admin/mining/embeddings")
                .then()
                .statusCode(200);

        // then
        List<Embedding> saved = embeddingRepository.findAll();
        assertThat(saved).isNotEmpty();
        assertThat(saved).allSatisfy(embedding -> assertThat(embedding.getVector()).hasSize(4));
    }

    @Test
    @DisplayName("algorithmVersion이 없으면 요청을 거부하고 아무것도 저장하지 않는다")
    void trainEmbeddings_WhenAlgorithmVersionMissing_RejectsRequestAndPersistsNothing() {
        // given & when
        given()
                .when().post("/admin/mining/embeddings")
                .then()
                .statusCode(400);

        // then
        assertThat(embeddingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("dimensions가 상한을 초과하면 요청을 거부하고 아무것도 저장하지 않는다")
    void trainEmbeddings_WhenDimensionsExceedsMax_RejectsRequestAndPersistsNothing() {
        // given & when
        given()
                .queryParam("algorithmVersion", "sgns-invalid")
                .queryParam("dimensions", "257")
                .when().post("/admin/mining/embeddings")
                .then()
                .statusCode(400);

        // then
        assertThat(embeddingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("negativeSamples가 상한을 초과하면 요청을 거부하고 아무것도 저장하지 않는다")
    void trainEmbeddings_WhenNegativeSamplesExceedsMax_RejectsRequestAndPersistsNothing() {
        // given & when
        given()
                .queryParam("algorithmVersion", "sgns-invalid")
                .queryParam("negativeSamples", "21")
                .when().post("/admin/mining/embeddings")
                .then()
                .statusCode(400);

        // then
        assertThat(embeddingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("epochs가 상한을 초과하면 요청을 거부하고 아무것도 저장하지 않는다")
    void trainEmbeddings_WhenEpochsExceedsMax_RejectsRequestAndPersistsNothing() {
        // given & when
        given()
                .queryParam("algorithmVersion", "sgns-invalid")
                .queryParam("epochs", "101")
                .when().post("/admin/mining/embeddings")
                .then()
                .statusCode(400);

        // then
        assertThat(embeddingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("learningRate가 상한을 초과하면 요청을 거부하고 아무것도 저장하지 않는다")
    void trainEmbeddings_WhenLearningRateExceedsMax_RejectsRequestAndPersistsNothing() {
        // given & when
        given()
                .queryParam("algorithmVersion", "sgns-invalid")
                .queryParam("learningRate", "1.1")
                .when().post("/admin/mining/embeddings")
                .then()
                .statusCode(400);

        // then
        assertThat(embeddingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("learningRate가 하한 미만이면 요청을 거부하고 아무것도 저장하지 않는다")
    void trainEmbeddings_WhenLearningRateBelowMin_RejectsRequestAndPersistsNothing() {
        // given & when
        given()
                .queryParam("algorithmVersion", "sgns-invalid")
                .queryParam("learningRate", "0.0")
                .when().post("/admin/mining/embeddings")
                .then()
                .statusCode(400);

        // then
        assertThat(embeddingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("winWeight가 상한을 초과하면 요청을 거부하고 아무것도 저장하지 않는다")
    void trainEmbeddings_WhenWinWeightExceedsMax_RejectsRequestAndPersistsNothing() {
        // given & when
        given()
                .queryParam("algorithmVersion", "sgns-invalid")
                .queryParam("winWeight", "10.1")
                .when().post("/admin/mining/embeddings")
                .then()
                .statusCode(400);

        // then
        assertThat(embeddingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("winWeight가 하한 미만이면 요청을 거부하고 아무것도 저장하지 않는다")
    void trainEmbeddings_WhenWinWeightBelowMin_RejectsRequestAndPersistsNothing() {
        // given & when
        given()
                .queryParam("algorithmVersion", "sgns-invalid")
                .queryParam("winWeight", "0.0")
                .when().post("/admin/mining/embeddings")
                .then()
                .statusCode(400);

        // then
        assertThat(embeddingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("기본 최소 지지도(10)를 만족하는 패턴이 저장된다")
    void minePatterns_WhenCalledWithDefaults_PersistsPatternsMeetingDefaultMinSupport() {
        // given & when: 시드 데이터는 GOLD 스코프에서 [3071, 6653] 시퀀스를 정확히 10명이 만든다(기본 minSupport=10과 동일)
        SequentialPatternMiningResult result = given()
                .queryParam("algorithmVersion", "prefixspan-default")
                .when().post("/admin/mining/patterns")
                .then()
                .statusCode(200)
                .extract().as(SequentialPatternMiningResult.class);

        // then
        assertThat(result.algorithmVersion()).isEqualTo("prefixspan-default");
        assertThat(result.persistedPatternCount()).isGreaterThan(0);

        List<MinedSequentialPattern> saved = minedSequentialPatternRepository.findAll();
        assertThat(saved).hasSize((int) result.persistedPatternCount());
        assertThat(saved).anySatisfy(pattern -> {
            assertThat(pattern.getChampionId()).isEqualTo(1L);
            assertThat(pattern.getPosition()).isEqualTo(ChampionPosition.TOP);
            assertThat(pattern.getTier()).isEqualTo("GOLD");
            assertThat(pattern.getPatch()).isEqualTo("14.1");
            assertThat(pattern.getItems()).containsExactly(3071L, 6653L);
            assertThat(pattern.getSupportCount()).isEqualTo(10);
            assertThat(pattern.getScopeTotalCount()).isEqualTo(10);
            assertThat(pattern.getWinCount()).isEqualTo(10);
        });
    }

    @Test
    @DisplayName("요청 파라미터로 최소 지지도를 실제 지지도보다 높게 지정하면 그 패턴은 저장되지 않는다")
    void minePatterns_WhenMinSupportProvidedAboveActualSupport_ExcludesPatternFromPersistence() {
        // given & when: 시드 데이터의 [3071, 6653] 시퀀스 지지도는 10 — minSupport=11이면 기본값(10)과 달리 제외되어야 한다
        given()
                .queryParam("algorithmVersion", "prefixspan-override")
                .queryParam("minSupport", "11")
                .when().post("/admin/mining/patterns")
                .then()
                .statusCode(200);

        // then
        List<MinedSequentialPattern> saved = minedSequentialPatternRepository.findAll();
        assertThat(saved).noneMatch(pattern -> pattern.getItems().equals(List.of(3071L, 6653L)));
    }

    @Test
    @DisplayName("algorithmVersion이 없으면 마이닝 요청도 거부하고 아무것도 저장하지 않는다")
    void minePatterns_WhenAlgorithmVersionMissing_RejectsRequestAndPersistsNothing() {
        // given & when
        given()
                .when().post("/admin/mining/patterns")
                .then()
                .statusCode(400);

        // then
        assertThat(minedSequentialPatternRepository.findAll()).isEmpty();
    }
}
