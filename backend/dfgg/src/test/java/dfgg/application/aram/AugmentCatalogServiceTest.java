package dfgg.application.aram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.aram.Augment;
import dfgg.domain.aram.AugmentCatalog;
import dfgg.domain.language.Language;
import dfgg.infrastructure.external.client.CommunityDragonClient;
import dfgg.infrastructure.external.dto.CherryAugment;
import dfgg.infrastructure.external.dto.CommunityDragonAugments;
import dfgg.infrastructure.external.dto.CommunityDragonDescriptions;
import dfgg.infrastructure.storage.S3ImageStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(OutputCaptureExtension.class)
class AugmentCatalogServiceTest {

    private static final Set<Integer> LINKABLE_IDS = idsUpTo(21);
    private static final String[] RARITIES = {"kSilver", "kGold", "kPrismatic"};

    private CommunityDragonClient client;
    private S3ImageStorage storage;
    private CommunityDragonDescriptions descriptions;
    private AugmentCatalogService service;

    @BeforeEach
    void setUp() {
        client = mock(CommunityDragonClient.class);
        storage = mock(S3ImageStorage.class);
        descriptions = mock(CommunityDragonDescriptions.class);
        when(client.getAugmentDescriptions("16.21")).thenReturn(descriptions);
        service = new AugmentCatalogService(client, storage);
    }

    @Test
    @DisplayName("연결한 증강에 언어별 설명을 붙인다")
    void link_AttachesDescriptions() {
        // given
        linkable(21);
        when(descriptions.descriptionOf(1)).thenReturn(Map.of("ko-KR", "설명", "en-US", "Description"));

        // when
        AugmentCatalog catalog = service.link("16.21", LINKABLE_IDS);

        // then
        assertThat(augmentOf(catalog, 1).descriptionIn(Language.EN_US)).contains("Description");
        assertThat(augmentOf(catalog, 2).descriptionIn(Language.EN_US)).isEmpty();
    }

    @Test
    @DisplayName("설명 파일을 받지 못해도 설명 없이 카탈로그를 만들고 경고를 남긴다")
    void link_WhenDescriptionsFail_LinksWithoutDescriptions(CapturedOutput output) {
        // given
        linkable(21);
        when(client.getAugmentDescriptions("16.21")).thenThrow(new AugmentDataRejectedException("문자열 파일 없음"));

        // when
        AugmentCatalog catalog = service.link("16.21", LINKABLE_IDS);

        // then
        assertThat(catalog.augments()).hasSize(21)
                .allSatisfy(augment -> assertThat(augment.descriptionIn(Language.KO_KR)).isEmpty());
        assertThat(output).contains("증강 설명 없이 저장합니다").contains("문자열 파일 없음");
    }

    @Test
    @DisplayName("설명을 붙인 증강 수를 로그로 남긴다")
    void link_LogsDescribedCount(CapturedOutput output) {
        // given
        linkable(21);
        when(descriptions.descriptionOf(1)).thenReturn(Map.of("ko-KR", "설명"));
        when(descriptions.descriptionOf(2)).thenReturn(Map.of("ko-KR", "설명"));

        // when
        service.link("16.21", LINKABLE_IDS);

        // then
        assertThat(output).contains("described=2/21");
    }

    @Test
    @DisplayName("연결한 증강으로 카탈로그를 만든다")
    void link_BuildsCatalog() {
        // given
        linkable(21);

        // when
        AugmentCatalog catalog = service.link("16.21", LINKABLE_IDS);

        // then
        assertThat(catalog.augments()).hasSize(21);
    }

    @Test
    @DisplayName("연결되지 않은 증강은 빼고 아이콘도 올리지 않는다")
    void link_ExcludesUnlinkedAugment() {
        // given
        linkable(21);

        // when
        AugmentCatalog catalog = service.link("16.21", with9999());

        // then
        assertThat(catalog.contains(9999)).isFalse();
        verify(storage, never()).store(eq("dfgg/images/16.21/augments/9999.png"), any());
    }

    @Test
    @DisplayName("연결되지 않은 증강은 경고 로그로 남긴다")
    void link_WarnsUnlinkedAugment(CapturedOutput output) {
        // given
        linkable(21);

        // when
        service.link("16.21", with9999());

        // then
        assertThat(output).contains("연결되지 않은 증강을 뺍니다").contains("9999");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("아이콘을 패치 폴더 아래 증강 id 키로 올린다")
    void link_StoresIconUnderPatchFolderByAugmentId() {
        // given
        linkable(21);
        byte[] png = {1, 2};
        when(client.getAugmentIcon("16.21", iconOf(1))).thenReturn(png);
        doAnswer(call -> {
            Supplier<byte[]> content = call.getArgument(1);
            assertThat(content.get()).isEqualTo(png);
            return null;
        }).when(storage).store(eq("dfgg/images/16.21/augments/1.png"), any());

        // when
        service.link("16.21", LINKABLE_IDS);

        // then
        verify(storage).store(eq("dfgg/images/16.21/augments/1.png"), any());
    }

    @Test
    @DisplayName("아이콘은 동시에 8개까지 올린다")
    void link_StoresIconsEightAtATime() {
        // given
        linkable(21);
        CountDownLatch allEightStarted = new CountDownLatch(8);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger mostRunning = new AtomicInteger();
        doAnswer(call -> {
            mostRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            allEightStarted.countDown();
            allEightStarted.await(5, TimeUnit.SECONDS);
            Thread.sleep(20);
            running.decrementAndGet();
            return null;
        }).when(storage).store(anyString(), any());

        // when
        service.link("16.21", LINKABLE_IDS);

        // then
        assertThat(mostRunning).hasValue(8);
    }

    @Test
    @DisplayName("이름이 겹쳐 거부되면 아이콘을 하나도 올리지 않는다")
    void link_WhenNameDuplicated_StoresNoIcon() {
        // given
        List<CherryAugment> korean = new ArrayList<>(cherries(20, "증강"));
        korean.add(new CherryAugment(21, "증강1", "kGold", iconOf(21)));
        when(client.getAugments("16.21")).thenReturn(CommunityDragonAugments.of(korean, cherries(21, "Augment")));

        // when & then
        assertThatThrownBy(() -> service.link("16.21", LINKABLE_IDS))
                .isInstanceOf(AugmentDataRejectedException.class);
        verify(storage, never()).store(anyString(), any());
    }

    @Test
    @DisplayName("연결률이 하한보다 낮으면 아이콘을 하나도 올리지 않는다")
    void link_WhenCoverageBelowLimit_StoresNoIcon() {
        // given
        linkable(10);

        // when & then
        assertThatThrownBy(() -> service.link("16.21", LINKABLE_IDS))
                .isInstanceOf(AugmentDataRejectedException.class)
                .hasMessageContaining("연결률");
        verify(storage, never()).store(anyString(), any());
    }

    @Test
    @DisplayName("아이콘을 받지 못한 증강만 빼고 경고 로그를 남긴다")
    void link_WhenIconRejected_ExcludesOnlyThatAugment(CapturedOutput output) {
        // given
        linkable(21);
        runIconSuppliers();
        when(client.getAugmentIcon("16.21", iconOf(3))).thenThrow(new AugmentDataRejectedException("아이콘 거부"));

        // when
        AugmentCatalog catalog = service.link("16.21", LINKABLE_IDS);

        // then
        assertThat(catalog.contains(3)).isFalse();
        assertThat(catalog.augments()).hasSize(20);
        assertThat(output).contains("아이콘을 저장하지 못한 증강을 뺍니다").contains("augmentId=3");
    }

    @Test
    @DisplayName("S3가 실패한 증강만 뺀다")
    void link_WhenS3Fails_ExcludesOnlyThatAugment() {
        // given
        linkable(21);
        doThrow(S3Exception.builder().message("S3 실패").build())
                .when(storage).store(eq("dfgg/images/16.21/augments/3.png"), any());

        // when
        AugmentCatalog catalog = service.link("16.21", LINKABLE_IDS);

        // then
        assertThat(catalog.contains(3)).isFalse();
    }

    @Test
    @DisplayName("아이콘이 대부분 실패하면 전체를 거부한다")
    void link_WhenMostIconsFail_Rejects() {
        // given
        linkable(21);
        doThrow(S3Exception.builder().message("S3 실패").build()).when(storage).store(anyString(), any());

        // when & then
        assertThatThrownBy(() -> service.link("16.21", LINKABLE_IDS))
                .isInstanceOf(AugmentDataRejectedException.class);
    }

    @Test
    @DisplayName("저장소 설정 오류는 증강을 빼지 않고 그대로 실패한다")
    void link_WhenStorageMisconfigured_Fails() {
        // given
        linkable(21);
        doThrow(new IllegalStateException("버킷 없음")).when(storage).store(anyString(), any());

        // when & then
        assertThatThrownBy(() -> service.link("16.21", LINKABLE_IDS))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @SuppressWarnings("unchecked")
    private void runIconSuppliers() {
        doAnswer(call -> ((Supplier<byte[]>) call.getArgument(1)).get()).when(storage).store(anyString(), any());
    }

    private void linkable(int count) {
        when(client.getAugments("16.21")).thenReturn(
                CommunityDragonAugments.of(cherries(count, "증강"), cherries(count, "Augment")));
    }

    private List<CherryAugment> cherries(int count, String namePrefix) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(id -> new CherryAugment(id, namePrefix + id, RARITIES[id % 3], iconOf(id)))
                .toList();
    }

    private static String iconOf(int augmentId) {
        return "/lol-game-data/assets/ASSETS/UX/Kiwi/Augments/Icons/A" + augmentId + "_small.png";
    }

    private static Augment augmentOf(AugmentCatalog catalog, int augmentId) {
        return catalog.augments().stream()
                .filter(augment -> augment.augmentId() == augmentId)
                .findFirst()
                .orElseThrow();
    }

    private static Set<Integer> idsUpTo(int count) {
        return IntStream.rangeClosed(1, count).boxed().collect(Collectors.toSet());
    }

    private Set<Integer> with9999() {
        Set<Integer> ids = idsUpTo(21);
        ids.add(9999);
        return ids;
    }
}
