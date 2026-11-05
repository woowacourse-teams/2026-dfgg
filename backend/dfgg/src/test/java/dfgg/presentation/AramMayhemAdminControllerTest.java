package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.application.aram.AramMayhemQueryService;
import dfgg.application.aram.AramMayhemSyncService;
import dfgg.application.aram.AugmentSnapshotWriter;
import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.aram.Augment;
import dfgg.domain.aram.AugmentCatalog;
import dfgg.domain.aram.AugmentRarity;
import dfgg.domain.aram.AugmentRepository;
import dfgg.domain.aram.ChampionAugment;
import dfgg.domain.aram.ChampionAugmentRepository;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.language.Language;
import dfgg.infrastructure.external.client.AramhexClient;
import dfgg.infrastructure.external.client.CommunityDragonClient;
import dfgg.infrastructure.external.dto.AramhexGames;
import dfgg.infrastructure.external.dto.AramhexPatchData;
import dfgg.infrastructure.external.dto.CherryAugment;
import dfgg.infrastructure.external.dto.CommunityDragonAugments;
import dfgg.infrastructure.storage.S3ImageStorage;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 수집 요청부터 실제 DB 저장, 조회 응답까지 연결한다. 외부 서버와 S3만 가짜로 둔다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AramMayhemAdminControllerTest {

    private static final int ASHE = 22;
    private static final int SHRINK_ENGINE = 1373;
    private static final int OTHER_AUGMENTS = 20;
    private static final int ALL_AUGMENTS = OTHER_AUGMENTS + 1;
    private static final String ICON = "/lol-game-data/assets/ASSETS/UX/Kiwi/Augments/Icons/A_small.png";

    @LocalServerPort
    private int port;

    @Autowired
    private ChampionRepository championRepository;

    @Autowired
    private AugmentRepository augmentRepository;

    @Autowired
    private ChampionAugmentRepository championAugmentRepository;

    @Autowired
    private AugmentSnapshotWriter writer;

    @Autowired
    private AramMayhemSyncService syncService;

    @Autowired
    private AramMayhemQueryService queryService;

    @MockitoBean
    private AramhexClient aramhexClient;

    @MockitoBean
    private CommunityDragonClient communityDragonClient;

    @MockitoBean
    private S3ImageStorage storage;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        championRepository.save(new Champion((long) ASHE, "Ashe", Map.of("ko-KR", "애쉬", "en-US", "Ashe"), List.of()));
    }

    /** {@code @SpringBootTest}는 롤백하지 않는다. 커밋된 채 남으면 다른 테스트가 깨진다. */
    @AfterEach
    void cleanUp() {
        augmentRepository.deleteAllInBatch();
        championRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("수집하면 조회 API가 저장된 픽률을 준다")
    void sync_ThenAugmentsAreServed() {
        // given
        patchAvailable("16.21", 1679);

        // when
        sync().then().statusCode(204);

        // then
        given().accept(ContentType.JSON).header("Accept-Language", "ko")
                .when().get("/api/aram-mayhem/champions/22/augments")
                .then().statusCode(200)
                .body("patch", equalTo("16.21"))
                .body("augments", hasSize(ALL_AUGMENTS))
                .body("augments.find { it.augmentId == 1373 }.name", equalTo("축소 엔진"))
                .body("augments.find { it.augmentId == 1373 }.pickRate", equalTo(16.8f));
    }

    @Test
    @DisplayName("같은 패치를 다시 수집하면 그 패치를 교체한다")
    void sync_WhenSamePatch_ReplacesPatch() {
        // given
        patchAvailable("16.21", 1679);
        sync().then().statusCode(204);
        patchAvailable("16.21", 2000);

        // when
        sync().then().statusCode(204);

        // then
        assertThat(augmentRepository.count()).isEqualTo(ALL_AUGMENTS);
        assertThat(championAugmentRepository.count()).isEqualTo(ALL_AUGMENTS);
        assertServedPickRate("16.21", 20.0f);
    }

    @Test
    @DisplayName("새 패치를 수집하면 이전 패치를 남기고 새 패치로 응답한다")
    void sync_WhenNewerPatch_KeepsOldPatchAndServesNew() {
        // given
        patchAvailable("16.9", 1679);
        sync().then().statusCode(204);
        patchAvailable("16.21", 2000);

        // when
        sync().then().statusCode(204);

        // then
        assertThat(augmentRepository.findDistinctPatches()).containsExactlyInAnyOrder("16.9", "16.21");
        assertServedPickRate("16.21", 20.0f);
    }

    @Test
    @DisplayName("저장된 패치보다 낮은 패치는 거부하고 아무것도 바꾸지 않는다")
    void sync_WhenOlderPatch_Rejects() {
        // given
        patchAvailable("16.21", 1679);
        sync().then().statusCode(204);
        when(aramhexClient.getLatestPatchData()).thenReturn(patchData("16.9", 2000));

        // when & then
        sync().then().statusCode(502);
        verify(communityDragonClient, never()).getAugments("16.9");
        assertThat(augmentRepository.findDistinctPatches()).containsExactly("16.21");
        assertServedPickRate("16.21", 16.8f);
    }

    @Test
    @DisplayName("연결되지 않은 증강의 픽률은 저장하지 않는다")
    void sync_ExcludesPicksOfUnlinkedAugment() {
        // given
        Map<Integer, AramhexGames> augments = new HashMap<>(picks(1679));
        augments.put(9999, new AramhexGames(10));
        when(aramhexClient.getLatestPatchData()).thenReturn(new AramhexPatchData("16.21",
                Map.of(ASHE, new AramhexGames(10000)), Map.of(ASHE, augments)));
        linkable("16.21");

        // when
        sync().then().statusCode(204);

        // then
        assertThat(championAugmentRepository.count()).isEqualTo(ALL_AUGMENTS);
    }

    @Test
    @DisplayName("일부만 연결된 데이터는 502이고 직전 데이터로 계속 응답한다")
    void sync_WhenCoverageBelowLimit_KeepsPreviousData() {
        // given
        patchAvailable("16.21", 1679);
        sync().then().statusCode(204);
        when(communityDragonClient.getAugments("16.21")).thenReturn(CommunityDragonAugments.of(
                List.of(new CherryAugment(SHRINK_ENGINE, "축소 엔진", "kGold", ICON)),
                List.of(new CherryAugment(SHRINK_ENGINE, "Shrink Engine", "kGold", ICON))));
        when(aramhexClient.getLatestPatchData()).thenReturn(patchData("16.21", 2000));

        // when & then
        sync().then().statusCode(502);
        assertServedPickRate("16.21", 16.8f);
    }

    @Test
    @DisplayName("판 수가 잘못된 데이터는 아이콘을 올리기 전에 거부한다")
    void sync_WhenGamesInvalid_RejectsBeforeUpload() {
        // given
        when(aramhexClient.getLatestPatchData()).thenReturn(new AramhexPatchData("16.21",
                Map.of(ASHE, new AramhexGames(100)), Map.of(ASHE, picks(101))));
        linkable("16.21");

        // when & then
        sync().then().statusCode(502);
        verify(storage, never()).store(anyString(), any());
    }

    @Test
    @DisplayName("픽률 파일이 거부되면 502이고 직전 데이터로 계속 응답한다")
    void sync_WhenPatchDataRejected_KeepsPreviousData() {
        // given
        patchAvailable("16.21", 1679);
        sync().then().statusCode(204);
        when(aramhexClient.getLatestPatchData()).thenThrow(new AugmentDataRejectedException("거부"));

        // when & then
        sync().then().statusCode(502);
        assertServedPickRate("16.21", 16.8f);
    }

    @Test
    @DisplayName("이름 연결이 거부되면 502이고 직전 데이터로 계속 응답한다")
    void sync_WhenLinkRejected_KeepsPreviousData() {
        // given
        patchAvailable("16.21", 1679);
        sync().then().statusCode(204);
        when(aramhexClient.getLatestPatchData()).thenReturn(patchData("16.21", 2000));
        when(communityDragonClient.getAugments("16.21")).thenThrow(new AugmentDataRejectedException("거부"));

        // when & then
        sync().then().statusCode(502);
        assertServedPickRate("16.21", 16.8f);
    }

    @Test
    @DisplayName("아이콘 하나가 실패하면 그 증강과 픽률만 빼고 저장한다")
    void sync_WhenOneIconFails_ExcludesThatAugment() {
        // given
        patchAvailable("16.21", 1679);
        doThrow(new AugmentDataRejectedException("아이콘 거부"))
                .when(storage).store(eq("dfgg/images/16.21/augments/1373.png"), any());

        // when
        sync().then().statusCode(204);

        // then
        assertThat(augmentRepository.count()).isEqualTo(ALL_AUGMENTS - 1);
        assertThat(championAugmentRepository.count()).isEqualTo(ALL_AUGMENTS - 1);
        given().accept(ContentType.JSON)
                .when().get("/api/aram-mayhem/champions/22/augments")
                .then().statusCode(200)
                .body("augments.augmentId", not(hasItem(SHRINK_ENGINE)));
    }

    @Test
    @DisplayName("아이콘이 대부분 실패하면 502이고 직전 데이터로 계속 응답한다")
    void sync_WhenMostIconsFail_KeepsPreviousData() {
        // given
        patchAvailable("16.21", 1679);
        sync().then().statusCode(204);
        patchAvailable("16.21", 2000);
        doThrow(new AugmentDataRejectedException("아이콘 거부")).when(storage).store(anyString(), any());

        // when & then
        sync().then().statusCode(502);
        assertServedPickRate("16.21", 16.8f);
    }

    @Test
    @DisplayName("외부 호출과 아이콘 업로드는 DB 트랜잭션 밖에서 한다")
    void sync_LinksOutsideTransaction() {
        // given
        patchAvailable("16.21", 1679);
        CommunityDragonAugments source = communityDragonClient.getAugments("16.21");
        List<Boolean> inTransaction = new ArrayList<>();
        when(communityDragonClient.getAugments("16.21")).thenAnswer(call -> {
            inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            return source;
        });

        // when
        sync().then().statusCode(204);

        // then
        assertThat(inTransaction).containsExactly(false);
    }

    @Test
    @DisplayName("저장 중 실패하면 지운 패치도 되돌린다")
    void replace_WhenInsertFails_RollsBackDelete() {
        // given
        patchAvailable("16.21", 1679);
        sync().then().statusCode(204);
        AugmentCatalog catalog = AugmentCatalog.of(List.of(
                new Augment("16.21", 1, Map.of("ko-KR", "가", "en-US", "A"), AugmentRarity.SILVER),
                new Augment("16.21", SHRINK_ENGINE, Map.of("ko-KR", "나", "en-US", "B"), AugmentRarity.GOLD),
                new Augment("16.21", 2, Map.of("ko-KR", "다", "en-US", "C"), AugmentRarity.PRISMATIC)), 3);
        List<ChampionAugment> outOfRange = List.of(new ChampionAugment("16.21", ASHE, SHRINK_ENGINE, 1, 101.0));

        // when & then
        assertThatThrownBy(() -> writer.replace("16.21", catalog, outOfRange)).isInstanceOf(RuntimeException.class);
        assertServedPickRate("16.21", 16.8f);
    }

    @Test
    @DisplayName("같은 패치의 증강 구성이 바뀌는 재수집 중에도 조회가 실패하지 않는다")
    void sync_WhenAugmentsChangeDuringReads_ReadsNeverFail() throws InterruptedException {
        // given
        List<CherryAugment> korean = new ArrayList<>();
        List<CherryAugment> english = new ArrayList<>();
        for (int augmentId = 1; augmentId <= 80; augmentId++) {
            korean.add(new CherryAugment(augmentId, "증강" + augmentId, rarityOf(augmentId), ICON));
            english.add(new CherryAugment(augmentId, "Augment" + augmentId, rarityOf(augmentId), ICON));
        }
        when(communityDragonClient.getAugments("16.21")).thenReturn(CommunityDragonAugments.of(korean, english));
        when(aramhexClient.getLatestPatchData()).thenReturn(patchDataOf(1, 40));
        syncService.sync();
        AtomicBoolean finished = new AtomicBoolean();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        List<Thread> readers = Stream.generate(() -> new Thread(() -> readUntil(finished, failures)))
                .limit(3)
                .toList();
        readers.forEach(Thread::start);

        // when
        for (int round = 0; round < 60; round++) {
            when(aramhexClient.getLatestPatchData())
                    .thenReturn(round % 2 == 0 ? patchDataOf(41, 80) : patchDataOf(1, 40));
            syncService.sync();
        }
        finished.set(true);
        for (Thread reader : readers) {
            reader.join();
        }

        // then
        assertThat(failures).isEmpty();
    }

    private void readUntil(AtomicBoolean finished, Queue<Throwable> failures) {
        while (!finished.get()) {
            try {
                queryService.findByChampion(ASHE, Language.KO_KR);
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
    }

    private AramhexPatchData patchDataOf(int firstAugmentId, int lastAugmentId) {
        Map<Integer, AramhexGames> augments = IntStream.rangeClosed(firstAugmentId, lastAugmentId).boxed()
                .collect(Collectors.toMap(Function.identity(), augmentId -> new AramhexGames(10)));
        return new AramhexPatchData("16.21", Map.of(ASHE, new AramhexGames(1000)), Map.of(ASHE, augments));
    }

    private Response sync() {
        return given().when().post("/admin/aram-mayhem/sync");
    }

    private void assertServedPickRate(String patch, float pickRate) {
        given().accept(ContentType.JSON)
                .when().get("/api/aram-mayhem/champions/22/augments")
                .then().statusCode(200)
                .body("patch", equalTo(patch))
                .body("augments.find { it.augmentId == 1373 }.pickRate", equalTo(pickRate));
    }

    private void patchAvailable(String patch, long games) {
        when(aramhexClient.getLatestPatchData()).thenReturn(patchData(patch, games));
        linkable(patch);
    }

    private AramhexPatchData patchData(String patch, long games) {
        return new AramhexPatchData(patch, Map.of(ASHE, new AramhexGames(10000)), Map.of(ASHE, picks(games)));
    }

    private Map<Integer, AramhexGames> picks(long shrinkEngineGames) {
        Map<Integer, AramhexGames> picks = IntStream.rangeClosed(1, OTHER_AUGMENTS).boxed()
                .collect(Collectors.toMap(Function.identity(), augmentId -> new AramhexGames(10)));
        picks.put(SHRINK_ENGINE, new AramhexGames(shrinkEngineGames));
        return picks;
    }

    private void linkable(String patch) {
        List<CherryAugment> korean = new ArrayList<>(List.of(new CherryAugment(SHRINK_ENGINE, "축소 엔진", "kGold", ICON)));
        List<CherryAugment> english = new ArrayList<>(
                List.of(new CherryAugment(SHRINK_ENGINE, "Shrink Engine", "kGold", ICON)));
        for (int augmentId = 1; augmentId <= OTHER_AUGMENTS; augmentId++) {
            korean.add(new CherryAugment(augmentId, "증강" + augmentId, rarityOf(augmentId), ICON));
            english.add(new CherryAugment(augmentId, "Augment" + augmentId, rarityOf(augmentId), ICON));
        }
        when(communityDragonClient.getAugments(patch)).thenReturn(CommunityDragonAugments.of(korean, english));
    }

    private String rarityOf(int augmentId) {
        return List.of("kSilver", "kGold", "kPrismatic").get(augmentId % 3);
    }
}
