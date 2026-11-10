package dfgg.application.patch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.application.champion.ChampionService;
import dfgg.application.item.ItemService;
import dfgg.application.rune.RuneService;
import dfgg.application.spell.SummonerSpellService;
import dfgg.domain.match.PatchVersion;
import dfgg.infrastructure.external.client.DataDragonClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PatchCatalogSyncTest {

    private static final PatchVersion PATCH = PatchVersion.of("16.22");

    private final ChampionService champions = mock(ChampionService.class);
    private final ItemService items = mock(ItemService.class);
    private final SummonerSpellService spells = mock(SummonerSpellService.class);
    private final RuneService runes = mock(RuneService.class);
    private final DataDragonClient dataDragon = mock(DataDragonClient.class);
    private final PatchImageChecker images = mock(PatchImageChecker.class);
    private final PatchCatalogSync catalogSync =
            new PatchCatalogSync(champions, items, spells, runes, dataDragon, images);

    @Test
    @DisplayName("응답 URL이 버전 폴더를 쓰는 카탈로그를 모두 동기화한 뒤 완료 표시를 남긴다")
    void syncFor_WhenAllSucceed_SyncsCatalogsThenMarksReady() {
        // given
        when(dataDragon.getLatestVersion()).thenReturn("16.22");

        // when
        catalogSync.syncFor(PATCH);

        // then
        InOrder order = inOrder(champions, items, spells, runes, images);
        order.verify(champions).syncChampions();
        order.verify(items).syncItems();
        order.verify(spells).syncSpells();
        order.verify(runes).syncRunes();
        order.verify(images).markReady(PATCH);
    }

    @Test
    @DisplayName("동기화 중 하나라도 실패하면 완료 표시를 남기지 않는다")
    void syncFor_WhenSyncFails_DoesNotMarkReady() {
        // given
        when(dataDragon.getLatestVersion()).thenReturn("16.22");
        doThrow(new IllegalStateException("upload failed")).when(items).syncItems();

        // when & then
        assertThatThrownBy(() -> catalogSync.syncFor(PATCH)).isInstanceOf(IllegalStateException.class);
        verify(images, never()).markReady(any());
    }

    @Test
    @DisplayName("동기화하는 사이 Data Dragon 패치가 바뀌면 완료 표시를 남기지 않는다")
    void syncFor_WhenLatestMovedDuringSync_DoesNotMarkReady() {
        // given
        when(dataDragon.getLatestVersion()).thenReturn("16.23");

        // when & then
        assertThatThrownBy(() -> catalogSync.syncFor(PATCH)).isInstanceOf(IllegalStateException.class);
        verify(images, never()).markReady(any());
    }
}
