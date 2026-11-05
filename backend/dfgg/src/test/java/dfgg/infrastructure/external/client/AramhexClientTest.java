package dfgg.infrastructure.external.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.json.JsonMapper;
import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.aram.ChampionAugment;
import dfgg.infrastructure.external.dto.AramhexGames;
import dfgg.infrastructure.external.dto.AramhexPatchData;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class AramhexClientTest {

    private static final String BASE_URL = "https://raw.githubusercontent.com/czgreat/aramhex-data/main";
    private static final String META = """
            {"license": "CC BY-NC 4.0", "schema_version": 1, "mode": "normal", "patch": "16.21", "heroes": 1,
             "low_sample_threshold": 3000}
            """;
    private static final String HEROES = """
            {"22": {"games": 10000, "name": "寒冰射手", "pick_rate": 0.02, "win_rate": 0.51, "wins": 5100}}
            """;
    private static final String HERO_AUGMENTS = """
            {"22": {"1373": {"games": 1679, "low_sample": false, "win_rate": 0.55, "wins": 923}}}
            """;

    private MockRestServiceServer server;
    private AramhexClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AramhexClient(builder, JsonMapper.builder().build());
    }

    @Test
    @DisplayName("최신 패치 파일을 읽어 챔피언별 픽률을 계산한다")
    void getLatestPatchData_CalculatesChampionAugmentPickRates() {
        // given
        expectLatest(latest("16.21", "normal", 1, sha256(META), sha256(HEROES), sha256(HERO_AUGMENTS)));
        expectFile("meta.json", META);
        expectFile("heroes.json", HEROES);
        expectFile("hero_augments.json", HERO_AUGMENTS);

        // when
        AramhexPatchData data = client.getLatestPatchData();

        // then
        List<ChampionAugment> championAugments = data.toChampionAugments();
        assertThat(data.patch()).isEqualTo("16.21");
        assertThat(championAugments).hasSize(1);
        assertThat(championAugments.getFirst().augmentId()).isEqualTo(1373);
        assertThat(championAugments.getFirst().pickRate()).isCloseTo(16.79, within(0.0001));
        server.verify();
    }

    @Test
    @DisplayName("증강 값을 파싱해도 승률은 어디에도 남지 않는다")
    void aramhexGames_HoldsNoWinRate() {
        // when
        List<String> fields = Arrays.stream(AramhexGames.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();

        // then
        assertThat(fields).containsExactly("games");
    }

    @Test
    @DisplayName("SHA-256이 다르면 거부한다")
    void getLatestPatchData_WhenHashDiffers_Rejects() {
        // given
        expectLatest(latest("16.21", "normal", 1, sha256(META), sha256(HEROES), sha256("다른 내용")));
        expectFile("meta.json", META);
        expectFile("heroes.json", HEROES);
        expectFile("hero_augments.json", HERO_AUGMENTS);

        // when & then
        assertRejected("SHA-256");
    }

    @Test
    @DisplayName("해시 목록에 없는 파일은 거부한다")
    void getLatestPatchData_WhenFileNotListed_Rejects() {
        // given
        expectLatest("""
                {"patch": "16.21", "mode": "normal", "schema_version": 1, "files": {}}
                """);
        expectFile("meta.json", META);

        // when & then
        assertRejected("해시 목록");
    }

    @Test
    @DisplayName("mode가 normal이 아니면 파일을 받기 전에 거부한다")
    void getLatestPatchData_WhenModeNotNormal_Rejects() {
        // given
        expectLatest(latest("16.21", "first-prismatic", 1, sha256(META), sha256(HEROES), sha256(HERO_AUGMENTS)));

        // when & then
        assertRejected("mode");
    }

    @Test
    @DisplayName("schema_version이 바뀌면 파일을 받기 전에 거부한다")
    void getLatestPatchData_WhenSchemaChanged_Rejects() {
        // given
        expectLatest(latest("16.21", "normal", 2, sha256(META), sha256(HEROES), sha256(HERO_AUGMENTS)));

        // when & then
        assertRejected("schema_version");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../16.21", "16.05", "016.21", "99999999999.1", "16.21.1"})
    @DisplayName("패치 형식이 다르면 거부한다")
    void getLatestPatchData_WhenPatchMalformed_Rejects(String patch) {
        // given
        expectLatest(latest(patch, "normal", 1, sha256(META), sha256(HEROES), sha256(HERO_AUGMENTS)));

        // when & then
        assertRejected("패치 형식");
    }

    @Test
    @DisplayName("schema_version이 소수면 잘라서 통과시키지 않는다")
    void getLatestPatchData_WhenSchemaVersionFractional_Rejects() {
        // given
        expectLatest("""
                {"patch": "16.21", "mode": "normal", "schema_version": 1.9, "files": {}}
                """);

        // when & then
        assertRejected("읽지 못했습니다");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inconsistentMeta")
    @DisplayName("meta.json이 latest.json과 다르면 거부한다")
    void getLatestPatchData_WhenMetaInconsistent_Rejects(String reason, String meta) {
        // given
        expectLatest(latest("16.21", "normal", 1, sha256(meta), sha256(HEROES), sha256(HERO_AUGMENTS)));
        expectFile("meta.json", meta);

        // when & then
        assertRejected(reason);
    }

    private static Stream<Arguments> inconsistentMeta() {
        return Stream.of(
                Arguments.of("schema_version", """
                        {"license": "CC BY-NC 4.0", "schema_version": 2, "mode": "normal", "patch": "16.21",
                         "heroes": 1}
                        """),
                Arguments.of("mode", """
                        {"license": "CC BY-NC 4.0", "schema_version": 1, "mode": "first", "patch": "16.21", "heroes": 1}
                        """),
                Arguments.of("패치", """
                        {"license": "CC BY-NC 4.0", "schema_version": 1, "mode": "normal", "patch": "16.20",
                         "heroes": 1}
                        """)
        );
    }

    @Test
    @DisplayName("챔피언 수가 meta.json과 다르면 거부한다")
    void getLatestPatchData_WhenChampionCountDiffers_Rejects() {
        // given
        String heroAugments = """
                {"22": {"1373": {"games": 10}}, "86": {"1373": {"games": 10}}}
                """;
        String heroes = """
                {"22": {"games": 100}, "86": {"games": 100}}
                """;
        expectLatest(latest("16.21", "normal", 1, sha256(META), sha256(heroes), sha256(heroAugments)));
        expectFile("meta.json", META);
        expectFile("heroes.json", heroes);
        expectFile("hero_augments.json", heroAugments);

        // when & then
        assertRejected("챔피언 수");
    }

    @Test
    @DisplayName("라이선스가 바뀌면 거부한다")
    void getLatestPatchData_WhenLicenseChanged_Rejects() {
        // given
        String meta = """
                {"license": "All Rights Reserved", "schema_version": 1, "mode": "normal", "patch": "16.21", "heroes": 1}
                """;
        expectLatest(latest("16.21", "normal", 1, sha256(meta), sha256(HEROES), sha256(HERO_AUGMENTS)));
        expectFile("meta.json", meta);

        // when & then
        assertRejected("라이선스");
    }

    @Test
    @DisplayName("JSON을 읽지 못하면 거부한다")
    void getLatestPatchData_WhenJsonMalformed_Rejects() {
        // given
        expectLatest("not json");

        // when & then
        assertRejected("latest.json");
    }

    @Test
    @DisplayName("파일을 받지 못하면 거부한다")
    void getLatestPatchData_WhenDownloadFails_Rejects() {
        // given
        server.expect(requestTo(BASE_URL + "/latest.json")).andRespond(withServerError());

        // when & then
        assertRejected("받지 못했습니다");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null"})
    @DisplayName("latest.json이 비었거나 null이면 거부한다")
    void getLatestPatchData_WhenLatestEmpty_Rejects(String body) {
        // given
        expectLatest(body);

        // when & then
        assertRejected("latest.json");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedFiles")
    @DisplayName("파일 내용이 비정상이면 거부한다")
    void toChampionAugments_WhenFileMalformed_Rejects(String label, String heroes, String heroAugments) {
        // given
        expectLatest(latest("16.21", "normal", 1, sha256(META), sha256(heroes), sha256(heroAugments)));
        expectFile("meta.json", META);
        expectFile("heroes.json", heroes);
        expectFile("hero_augments.json", heroAugments);

        // when & then
        assertThatThrownBy(() -> client.getLatestPatchData().toChampionAugments())
                .isInstanceOf(AugmentDataRejectedException.class);
    }

    private static Stream<Arguments> malformedFiles() {
        String heroes = """
                {"22": {"games": 100}}
                """;
        String heroAugments = """
                {"22": {"1373": {"games": 10}}}
                """;
        return Stream.of(
                Arguments.of("heroes가 null", "null", heroAugments),
                Arguments.of("hero_augments가 null", heroes, "null"),
                Arguments.of("챔피언 값이 null", heroes, """
                        {"22": null}
                        """),
                Arguments.of("증강 값이 null", heroes, """
                        {"22": {"1373": null}}
                        """),
                Arguments.of("증강 games 누락", heroes, """
                        {"22": {"1373": {"count": 10}}}
                        """),
                Arguments.of("증강 games가 null", heroes, """
                        {"22": {"1373": {"games": null}}}
                        """),
                Arguments.of("증강 games가 빈 문자열", heroes, """
                        {"22": {"1373": {"games": ""}}}
                        """),
                Arguments.of("증강 games가 문자열 숫자", heroes, """
                        {"22": {"1373": {"games": "7"}}}
                        """),
                Arguments.of("증강 games가 소수", heroes, """
                        {"22": {"1373": {"games": 7.9}}}
                        """),
                Arguments.of("JSON 뒤에 다른 내용", heroes, """
                        {"22": {"1373": {"games": 10}}} trailing
                        """),
                Arguments.of("챔피언 games 누락", """
                        {"22": {"count": 100}}
                        """, heroAugments),
                Arguments.of("증강 판 수가 챔피언 판 수보다 많음", heroes, """
                        {"22": {"1373": {"games": 101}}}
                        """)
        );
    }

    @Test
    @DisplayName("챔피언들의 증강 ID를 중복 없이 모은다")
    void augmentIds_CollectsDistinctIds() {
        // given
        AramhexPatchData data = new AramhexPatchData("16.21", Map.of(), Map.of(
                22, Map.of(1373, new AramhexGames(1), 1402, new AramhexGames(1)),
                86, Map.of(1373, new AramhexGames(1))));

        // when & then
        assertThat(data.augmentIds()).containsExactlyInAnyOrder(1373, 1402);
    }

    @Test
    @DisplayName("증강 값이 있는 챔피언의 판 수가 없으면 거부한다")
    void toChampionAugments_WhenChampionGamesMissing_Rejects() {
        // given
        AramhexPatchData data = new AramhexPatchData("16.21", Map.of(),
                Map.of(22, Map.of(1373, new AramhexGames(100))));

        // when & then
        assertThatThrownBy(data::toChampionAugments).isInstanceOf(AugmentDataRejectedException.class);
    }

    private void assertRejected(String reason) {
        assertThatThrownBy(() -> client.getLatestPatchData())
                .isInstanceOf(AugmentDataRejectedException.class)
                .hasMessageContaining(reason);
    }

    private void expectLatest(String body) {
        server.expect(requestTo(BASE_URL + "/latest.json")).andRespond(respond(body));
    }

    private void expectFile(String file, String body) {
        server.expect(requestTo(BASE_URL + "/patches/16.21/normal/" + file)).andRespond(respond(body));
    }

    private ResponseCreator respond(String body) {
        return withSuccess(body.getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN);
    }

    private String latest(String patch, String mode, int schemaVersion, String metaHash, String heroesHash,
                          String heroAugmentsHash) {
        return """
                {"patch": "%s", "mode": "%s", "schema_version": %d, "path": "ignored", "seq": 12, "files": {
                  "meta.json": {"sha256": "%s", "size": 1},
                  "heroes.json": {"sha256": "%s", "size": 1},
                  "hero_augments.json": {"sha256": "%s", "size": 1}
                }}
                """.formatted(patch, mode, schemaVersion, metaHash, heroesHash, heroAugmentsHash);
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
