package dfgg.application.aram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.aram.AugmentCatalog;
import dfgg.infrastructure.external.client.CommunityDragonClient;
import dfgg.infrastructure.external.dto.CherryAugment;
import dfgg.infrastructure.external.dto.CommunityDragonAugments;
import dfgg.infrastructure.storage.S3ImageStorage;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AugmentCatalogServiceTest {

    private static final String ICON = "/lol-game-data/assets/ASSETS/UX/Kiwi/Augments/Icons/A_small.png";

    private CommunityDragonClient client;
    private S3ImageStorage storage;
    private AugmentCatalogService service;

    @BeforeEach
    void setUp() {
        client = mock(CommunityDragonClient.class);
        storage = mock(S3ImageStorage.class);
        service = new AugmentCatalogService(client, storage);
    }

    @Test
    @DisplayName("연결한 증강으로 카탈로그를 만든다")
    void link_BuildsCatalog() {
        // given
        given(new CherryAugment(1373, "축소 엔진", "kGold", ICON), new CherryAugment(1373, "Shrink Engine", "kGold", ICON));

        // when
        AugmentCatalog catalog = service.link("16.21", Set.of(1373));

        // then
        assertThat(catalog.contains(1373)).isTrue();
    }

    @Test
    @DisplayName("연결되지 않은 증강은 빼고 아이콘도 올리지 않는다")
    void link_ExcludesUnlinkedAugment() {
        // given
        given(new CherryAugment(1373, "축소 엔진", "kGold", ICON), new CherryAugment(1373, "Shrink Engine", "kGold", ICON));

        // when
        AugmentCatalog catalog = service.link("16.21", Set.of(1373, 9999));

        // then
        assertThat(catalog.contains(9999)).isFalse();
        verify(storage, never()).store(eq("dfgg/images/16.21/augments/9999.png"), any());
    }

    @Test
    @DisplayName("연결되지 않은 증강은 경고 로그로 남긴다")
    void link_WarnsUnlinkedAugment(CapturedOutput output) {
        // given
        given(new CherryAugment(1373, "축소 엔진", "kGold", ICON), new CherryAugment(1373, "Shrink Engine", "kGold", ICON));

        // when
        service.link("16.21", Set.of(1373, 9999));

        // then
        assertThat(output).contains("연결되지 않은 증강을 뺍니다").contains("9999");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("아이콘을 패치 폴더 아래 증강 id 키로 올린다")
    void link_StoresIconUnderPatchFolderByAugmentId() {
        // given
        given(new CherryAugment(1373, "축소 엔진", "kGold", ICON), new CherryAugment(1373, "Shrink Engine", "kGold", ICON));
        byte[] png = {1, 2};
        when(client.getAugmentIcon("16.21", ICON)).thenReturn(png);
        doAnswer(call -> {
            Supplier<byte[]> content = call.getArgument(1);
            assertThat(content.get()).isEqualTo(png);
            return null;
        }).when(storage).store(eq("dfgg/images/16.21/augments/1373.png"), any());

        // when
        service.link("16.21", Set.of(1373));

        // then
        verify(storage).store(eq("dfgg/images/16.21/augments/1373.png"), any());
    }

    @Test
    @DisplayName("카탈로그가 거부되면 아이콘을 하나도 올리지 않는다")
    void link_WhenCatalogRejected_StoresNoIcon() {
        // given
        when(client.getAugments("16.21")).thenReturn(CommunityDragonAugments.of(
                List.of(new CherryAugment(1, "같은 이름", "kGold", ICON), new CherryAugment(2, "같은 이름", "kGold", ICON)),
                List.of(new CherryAugment(1, "A", "kGold", ICON), new CherryAugment(2, "B", "kGold", ICON))));

        // when & then
        assertThatThrownBy(() -> service.link("16.21", Set.of(1, 2)))
                .isInstanceOf(AugmentDataRejectedException.class);
        verify(storage, never()).store(anyString(), any());
    }

    private void given(CherryAugment korean, CherryAugment english) {
        when(client.getAugments("16.21")).thenReturn(CommunityDragonAugments.of(List.of(korean), List.of(english)));
    }
}
