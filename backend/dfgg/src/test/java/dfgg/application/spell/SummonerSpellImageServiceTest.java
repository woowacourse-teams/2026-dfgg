package dfgg.application.spell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.storage.S3ImageStorage;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SummonerSpellImageServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("전체 빌드와 파일명으로 내려받아 영문 키로 저장한다")
    void store_WhenValidFilename_StoreByRiotKey() {
        // given
        DataDragonClient dragon = mock(DataDragonClient.class);
        S3ImageStorage storage = mock(S3ImageStorage.class);
        byte[] png = {1, 2};
        when(dragon.getSummonerSpellImage("16.20.1", "SummonerFlash.png")).thenReturn(png);
        doAnswer(call -> {
            Supplier<byte[]> content = call.getArgument(1);
            assertThat(content.get()).isEqualTo(png);
            return null;
        }).when(storage).store(eq("dfgg/images/16.20/summoner-spells/SummonerFlash.png"), any());

        // when & then
        new SummonerSpellImageService(dragon, storage).store("16.20", "16.20.1", "SummonerFlash", "SummonerFlash.png");
    }

    @Test
    @DisplayName("잘못된 파일명은 거부한다")
    void store_WhenFilenameInvalid_Reject() {
        // given
        DataDragonClient dragon = mock(DataDragonClient.class);
        S3ImageStorage storage = mock(S3ImageStorage.class);
        SummonerSpellImageService service = new SummonerSpellImageService(dragon, storage);

        // when & then
        assertThatThrownBy(() -> service.store("16.20", "16.20.1", "SummonerFlash", "../SummonerFlash.png"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(dragon, storage);
    }
}
