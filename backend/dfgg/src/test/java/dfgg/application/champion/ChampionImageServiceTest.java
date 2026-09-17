package dfgg.application.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.storage.S3ImageStorage;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ChampionImageServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void 메타데이터의_전체_버전과_파일명으로_이미지를_저장한다() {
        var dragon = mock(DataDragonClient.class);
        var storage = mock(S3ImageStorage.class);
        byte[] png = {1, 2};
        when(dragon.getChampionImage("16.15.1", "Aatrox.png")).thenReturn(png);
        doAnswer(call -> {
            Supplier<byte[]> content = call.getArgument(1);
            assertThat(content.get()).isEqualTo(png);
            return null;
        }).when(storage).store(eq("dfgg/images/16.15/champions/Aatrox.png"), any());
        new ChampionImageService(dragon, storage).store("16.15", "16.15.1", "Aatrox.png");
    }

    @Test
    void 잘못된_파일명을_거부한다() {
        var dragon = mock(DataDragonClient.class);
        var storage = mock(S3ImageStorage.class);
        var service = new ChampionImageService(dragon, storage);
        assertThatThrownBy(() -> service.store("16.15", "16.15.1", "../Aatrox.png")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(dragon, storage);
    }
}
