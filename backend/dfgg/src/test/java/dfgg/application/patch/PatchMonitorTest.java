package dfgg.application.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.domain.match.PatchVersion;
import dfgg.infrastructure.external.client.DataDragonClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PatchMonitorTest {

    private static final PatchVersion SERVING = PatchVersion.of("16.21");
    private static final PatchVersion NEW = PatchVersion.of("16.22");

    private final DataDragonClient dataDragon = mock(DataDragonClient.class);
    private final PatchImageChecker images = mock(PatchImageChecker.class);
    private final PatchCatalogSync catalogSync = mock(PatchCatalogSync.class);
    private final ServicePatch servicePatch = new ServicePatch(SERVING);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private PatchMonitor receiver() {
        return new PatchMonitor(dataDragon, servicePatch, images, Optional.empty(), meters);
    }

    private PatchMonitor uploader() {
        return new PatchMonitor(dataDragon, servicePatch, images, Optional.of(catalogSync), meters);
    }

    @Test
    @DisplayName("받기만 하는 서버는 새 패치 그림이 S3에 있으면 전환한다")
    void check_WhenReceiverAndImagesReady_Switches() {
        // given
        when(dataDragon.getLatestVersion()).thenReturn("16.22");
        when(images.isReady(NEW)).thenReturn(true);

        // when
        receiver().check();

        // then
        assertThat(servicePatch.current()).isEqualTo(NEW);
    }

    @Test
    @DisplayName("받기만 하는 서버는 그림이 아직 없으면 유지하고, 다시 확인해 생기면 전환한다")
    void retryPending_WhenImagesAppearLater_Switches() {
        // given
        when(dataDragon.getLatestVersion()).thenReturn("16.22");
        when(images.isReady(NEW)).thenReturn(false, true);
        PatchMonitor monitor = receiver();

        // when
        monitor.check();
        PatchVersion afterCheck = servicePatch.current();
        monitor.retryPending();

        // then
        assertThat(afterCheck).isEqualTo(SERVING);
        assertThat(servicePatch.current()).isEqualTo(NEW);
    }

    @Test
    @DisplayName("받기만 하는 서버는 동기화하지 않는다")
    void check_WhenReceiver_NeverSyncs() {
        // given
        when(dataDragon.getLatestVersion()).thenReturn("16.22");
        when(images.isReady(NEW)).thenReturn(false);

        // when
        receiver().check();

        // then
        verify(catalogSync, never()).syncFor(any());
        assertThat(servicePatch.current()).isEqualTo(SERVING);
    }

    @Test
    @DisplayName("올리는 서버는 그림이 없으면 동기화한 뒤 전환한다")
    void check_WhenUploaderAndImagesMissing_SyncsThenSwitches() {
        // given
        when(dataDragon.getLatestVersion()).thenReturn("16.22");
        when(images.isReady(NEW)).thenReturn(false, true);

        // when
        uploader().check();

        // then
        verify(catalogSync).syncFor(NEW);
        assertThat(servicePatch.current()).isEqualTo(NEW);
    }

    @Test
    @DisplayName("올리는 서버도 그림이 이미 있으면 다시 동기화하지 않는다")
    void check_WhenUploaderAndImagesReady_SkipsSync() {
        // given
        when(dataDragon.getLatestVersion()).thenReturn("16.22");
        when(images.isReady(NEW)).thenReturn(true);

        // when
        uploader().check();

        // then
        verify(catalogSync, never()).syncFor(any());
        assertThat(servicePatch.current()).isEqualTo(NEW);
    }

    @Test
    @DisplayName("동기화가 실패하면 기존 패치를 유지하고 다음 확인에서 다시 시도한다")
    void check_WhenSyncFails_KeepsServingAndRetries() {
        // given
        when(dataDragon.getLatestVersion()).thenReturn("16.22");
        when(images.isReady(NEW)).thenReturn(false, false, true);
        doThrow(new IllegalStateException("upload failed")).doNothing().when(catalogSync).syncFor(NEW);
        PatchMonitor monitor = uploader();

        // when
        monitor.check();
        PatchVersion afterFailure = servicePatch.current();
        monitor.retryPending();

        // then
        assertThat(afterFailure).isEqualTo(SERVING);
        assertThat(servicePatch.current()).isEqualTo(NEW);
    }

    @Test
    @DisplayName("기동 직후 감지는 그림이 있으면 전환하지만 동기화는 하지 않는다")
    void detect_WhenUploaderAndImagesMissing_DoesNotSyncButKeepsPending() {
        // given
        when(dataDragon.getLatestVersion()).thenReturn("16.22");
        when(images.isReady(NEW)).thenReturn(false);
        PatchMonitor monitor = uploader();

        // when
        monitor.detect();

        // then
        verify(catalogSync, never()).syncFor(any());
        assertThat(servicePatch.current()).isEqualTo(SERVING);
        assertThat(meters.get("dfgg.patch.pending").gauge().value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Data Dragon 조회가 실패하면 기존 패치를 유지한다")
    void check_WhenDataDragonFails_KeepsServing() {
        // given
        when(dataDragon.getLatestVersion()).thenThrow(new IllegalStateException("timeout"));

        // when
        uploader().check();

        // then
        verify(catalogSync, never()).syncFor(any());
        assertThat(servicePatch.current()).isEqualTo(SERVING);
    }

    @Test
    @DisplayName("S3 확인이 실패하면 기존 패치를 유지한다")
    void check_WhenImageCheckFails_KeepsServing() {
        // given
        when(dataDragon.getLatestVersion()).thenReturn("16.22");
        when(images.isReady(NEW)).thenThrow(new IllegalStateException("403"));

        // when
        receiver().check();

        // then
        assertThat(servicePatch.current()).isEqualTo(SERVING);
    }

    @Test
    @DisplayName("Data Dragon이 같거나 낮은 패치를 주면 아무것도 하지 않고 대기도 없앤다")
    void check_WhenLatestIsNotNewer_DoesNothing() {
        // given
        when(dataDragon.getLatestVersion()).thenReturn("16.21");

        // when
        uploader().check();

        // then
        verify(images, never()).isReady(any());
        verify(catalogSync, never()).syncFor(any());
        assertThat(servicePatch.current()).isEqualTo(SERVING);
        assertThat(meters.get("dfgg.patch.pending").gauge().value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("대기 중인 패치가 없으면 다시 확인할 때 외부를 부르지 않는다")
    void retryPending_WhenNothingPending_CallsNothing() {
        // when
        uploader().retryPending();

        // then
        verify(dataDragon, never()).getLatestVersion();
        verify(images, never()).isReady(any());
    }
}
