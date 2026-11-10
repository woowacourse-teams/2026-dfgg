package dfgg.application.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.domain.match.PatchVersion;
import dfgg.infrastructure.storage.S3ImageStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PatchImageCheckerTest {

    private final S3ImageStorage storage = mock(S3ImageStorage.class);
    private final PatchImageChecker images = new PatchImageChecker(storage);

    @Test
    @DisplayName("패치 폴더의 완료 표시가 있어야 그림이 준비된 것으로 본다")
    void isReady_WhenMarkerExists_ReturnsTrue() {
        // given
        when(storage.exists("dfgg/images/16.22/ready")).thenReturn(true);

        // when
        boolean ready = images.isReady(PatchVersion.of("16.22"));

        // then
        assertThat(ready).isTrue();
    }

    @Test
    @DisplayName("완료 표시는 패치 폴더 아래에 남긴다")
    void markReady_WhenCalled_WritesMarkerInPatchFolder() {
        // when
        images.markReady(PatchVersion.of("16.22"));

        // then
        verify(storage).mark("dfgg/images/16.22/ready", "16.22");
    }
}
