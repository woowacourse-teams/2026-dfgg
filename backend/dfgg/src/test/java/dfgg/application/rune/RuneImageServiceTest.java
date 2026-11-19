package dfgg.application.rune;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.storage.S3ImageStorage;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuneImageServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Data Dragon 아이콘을 패치 폴더 아래 룬 id 키로 저장한다")
    void store_SavesIconUnderPatchFolderByRuneId() {
        // given
        DataDragonClient dragon = mock(DataDragonClient.class);
        S3ImageStorage storage = mock(S3ImageStorage.class);
        byte[] png = {1, 2};
        String iconPath = "perk-images/Styles/Resolve/GraspOfTheUndying/GraspOfTheUndying.png";
        when(dragon.getRuneImage(iconPath)).thenReturn(png);
        doAnswer(call -> {
            Supplier<byte[]> content = call.getArgument(1);
            assertThat(content.get()).isEqualTo(png);
            return null;
        }).when(storage).store(eq("dfgg/images/16.21/runes/8437.png"), any());

        // when
        new RuneImageService(dragon, storage).store("16.21", 8437, iconPath);

        // then
        verify(storage).store(eq("dfgg/images/16.21/runes/8437.png"), any());
    }
}
