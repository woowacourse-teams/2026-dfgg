package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.match.ComponentTrainingDataService;
import dfgg.application.recommend.MultiBuildRecommendationService;
import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.application.recommend.RecommendationService;
import dfgg.application.recommend.component.ComponentCandidateFeatures;
import dfgg.application.recommend.component.ComponentRecommendationQuery;
import dfgg.application.recommend.component.ComponentRecommendationRuntime;
import dfgg.application.recommend.component.ComponentRecommendationService;
import dfgg.application.recommend.component.CoreComponentRecommendationService;
import dfgg.application.recommend.context.CompositionFeatures;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.ItemInventory;
import dfgg.presentation.RecommendationController;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ComponentRuntimeConfigurationTest {
    private static final String BASE_URL = "https://ddragon.leagueoflegends.com";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TempDir Path directory;
    private MockRestServiceServer server;
    private RestClient.Builder builders;
    private ApplicationContextRunner runner;

    @BeforeEach
    void setUp() {
        RestClient.Builder http = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(http).build();
        builders = mock(RestClient.Builder.class, RETURNS_SELF);
        when(builders.clone()).thenReturn(builders);
        when(builders.build()).thenReturn(http.build());
        var core = mock(NextItemRecommendationService.class);
        when(core.recommendNextItem(null)).thenReturn(new NextItemRecommendationResponse(List.of()));
        runner = new ApplicationContextRunner()
                .withUserConfiguration(ComponentLtrConfiguration.class, PreparationLookupConfiguration.class,
                        ComponentRuntimeConfiguration.class, CoreComponentRecommendationService.class,
                        ComponentTrainingDataService.class, RecommendationController.class)
                .withBean(RestClient.Builder.class, () -> builders)
                .withBean(LeagueOfLegendsVersionProperties.class, () -> new LeagueOfLegendsVersionProperties("16.18"))
                .withBean(NextItemRecommendationService.class, () -> core)
                .withBean(RecommendationService.class, () -> mock(RecommendationService.class))
                .withBean(MultiBuildRecommendationService.class, () -> mock(MultiBuildRecommendationService.class));
    }

    @Test
    void 모델_미설정이면_외부_조회_없이_v3가_기동한다() {
        assertUnavailable(runner);
        verifyNoInteractions(builders);
    }

    @Test
    void 모델_미설정이면_사용하지_않는_조회표_오류도_v3를_막지_않는다() {
        assertUnavailable(runner.withPropertyValues(
                "recommendation.component-ltr.preparation-lookup-path=classpath:missing-lookup.json"));
        verifyNoInteractions(builders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-model", "invalid-json", "wrong-schema", "wrong-hash", "missing-lookup", "invalid-lookup", "wrong-patch"})
    void 모델이나_조회표_준비_실패는_Component만_비활성화한다(String failure) throws Exception {
        Map<String, Object> model = model();
        String lookupPath = "";
        if (failure.equals("wrong-schema")) model.put("schema_fingerprint", "invalid");
        if (failure.equals("wrong-hash")) model.put("preparation_lookup_sha256", "a".repeat(64));
        if (failure.equals("missing-lookup")) lookupPath = "classpath:missing-lookup.json";
        if (failure.equals("invalid-lookup")) lookupPath = write("lookup.json", "{}");
        if (failure.equals("wrong-patch")) {
            byte[] lookup = lookup("16.17");
            lookupPath = write("lookup.json", new String(lookup, java.nio.charset.StandardCharsets.UTF_8));
            attachLookup(model, lookup);
        }
        String modelPath = failure.equals("missing-model") ? "classpath:missing-model.json"
                : write("model.json", failure.equals("invalid-json") ? "broken" : MAPPER.writeValueAsString(model));

        assertUnavailable(runner.withPropertyValues("recommendation.component-ltr.model-path=" + modelPath,
                "recommendation.component-ltr.preparation-lookup-path=" + lookupPath));
        verifyNoInteractions(builders);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 기동_시_한번_준비한_카탈로그를_반복_추천에_재사용한다(boolean withLookup) throws Exception {
        Map<String, Object> model = model();
        String lookupPath = "";
        if (withLookup) {
            byte[] lookup = lookup("16.18");
            lookupPath = write("lookup.json", new String(lookup, java.nio.charset.StandardCharsets.UTF_8));
            attachLookup(model, lookup);
        }
        expectVersions();
        expectCatalog("en_US", "16.18.1");
        expectCatalog("ko_KR", "16.18.1");
        runner.withPropertyValues("recommendation.component-ltr.model-path=" + write("model.json", MAPPER.writeValueAsString(model)),
                        "recommendation.component-ltr.preparation-lookup-path=" + lookupPath)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var runtime = context.getBean(ComponentRecommendationRuntime.class);
                    assertThat(runtime.isAvailable()).isTrue();
                    assertThat(runtime.catalog().dataVersion()).isEqualTo("16.18.1");
                    assertThat(runtime.catalog().localizedName("1036")).containsEntry("ko-KR", "롱소드");
                    var catalog = runtime.catalog();
                    var first = runtime.recommend(query("16.18"));
                    assertThat(first.getFirst().status()).isEqualTo(ComponentRecommendationService.Status.OK);
                    assertThat(first.getFirst().rankedPurchases()).isNotEmpty();
                    assertThat(runtime.recommend(query("16.18"))).isEqualTo(first);
                    assertThat(runtime.catalog()).isSameAs(catalog);
                    assertThatThrownBy(() -> runtime.recommend(query("16.17")))
                            .isInstanceOf(IllegalArgumentException.class);
                    assertV3(context.getBean(RecommendationController.class));
                    server.verify();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"versions", "en_US", "ko_KR", "wrong-version", "missing-patch"})
    void 카탈로그_준비_실패는_v3_기동을_막지_않는다(String failure) throws Exception {
        if (failure.equals("versions")) {
            server.expect(requestTo(BASE_URL + "/api/versions.json")).andRespond(withServerError());
        } else if (failure.equals("missing-patch")) {
            server.expect(requestTo(BASE_URL + "/api/versions.json"))
                    .andRespond(withSuccess("[\"16.19.1\"]", MediaType.APPLICATION_JSON));
        } else {
            expectVersions();
            if (failure.equals("en_US")) {
                server.expect(requestTo(catalogUrl("en_US"))).andRespond(withServerError());
            } else {
                expectCatalog("en_US", "16.18.1");
                if (failure.equals("ko_KR")) {
                    server.expect(requestTo(catalogUrl("ko_KR"))).andRespond(withServerError());
                } else {
                    expectCatalog("ko_KR", "16.19.1");
                }
            }
        }
        assertUnavailable(runner.withPropertyValues("recommendation.component-ltr.model-path="
                + write("model.json", MAPPER.writeValueAsString(model()))));
    }

    private void assertUnavailable(ApplicationContextRunner application) {
        application.run(context -> {
            assertThat(context).hasNotFailed();
            var runtime = context.getBean(ComponentRecommendationRuntime.class);
            assertThat(runtime.isAvailable()).isFalse();
            assertThatThrownBy(runtime::catalog).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> runtime.recommend(query("16.18"))).isInstanceOf(IllegalStateException.class);
            assertV3(context.getBean(RecommendationController.class));
            server.verify();
        });
    }

    private void assertV3(RecommendationController controller) {
        assertThat(controller.recommendV3(null).getBody().recommendedItems()).isEmpty();
    }

    private ComponentRecommendationQuery query(String patch) {
        return new ComponentRecommendationQuery(81, List.of(3133), ItemInventory.empty(), 350, 600_000,
                ChampionPosition.BOTTOM, patch, new CompositionFeatures(1, 2, 1, 0, 1, 1, 1, 1, 1));
    }

    private Map<String, Object> model() {
        return new HashMap<>(Map.of("schema_fingerprint", ComponentLtrConfiguration.SCHEMA_FINGERPRINT,
                "feature_names", ComponentCandidateFeatures.FEATURE_NAMES,
                "trees", List.of(Map.of("split_feature", List.of(), "threshold", List.of(), "default_left", List.of(),
                        "left", List.of(), "right", List.of(), "leaf_value", List.of(1.0),
                        "node_cover", List.of(), "leaf_cover", List.of(1.0)))));
    }

    private byte[] lookup(String patch) throws Exception {
        return MAPPER.writeValueAsBytes(Map.of("schemaVersion", "component-preparation-v2", "miningSplit", "train",
                "minPatternCount", 50, "minFutureRecipeLinkRate", .70, "maxCandidatesPerKey", 3,
                "entries", List.of(), "trainMatchIds", List.of("KR_TEST"), "splitManifestSha256", "a".repeat(64), "patch", patch));
    }

    private void attachLookup(Map<String, Object> model, byte[] lookup) throws Exception {
        model.put("preparation_lookup_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(lookup)));
        model.put("preparation_lookup_schema_version", "component-preparation-v2");
    }

    private String write(String name, String body) throws Exception {
        return Files.writeString(directory.resolve(name), body).toUri().toString();
    }

    private void expectVersions() {
        server.expect(requestTo(BASE_URL + "/api/versions.json"))
                .andRespond(withSuccess("[\"16.19.1\",\"16.18.1\"]", MediaType.APPLICATION_JSON));
    }

    private void expectCatalog(String locale, String version) {
        String name = locale.equals("ko_KR") ? "롱소드" : "Long Sword";
        server.expect(requestTo(catalogUrl(locale))).andRespond(withSuccess("""
                {"version":"%s","data":{
                  "1036":{"name":"%s","gold":{"total":350,"purchasable":true}},
                  "3133":{"name":"Caulfield","from":["1036","1036"],"gold":{"total":1100,"purchasable":true}}
                }}
                """.formatted(version, name), MediaType.APPLICATION_JSON));
    }

    private String catalogUrl(String locale) {
        return BASE_URL + "/cdn/16.18.1/data/" + locale + "/item.json";
    }
}
