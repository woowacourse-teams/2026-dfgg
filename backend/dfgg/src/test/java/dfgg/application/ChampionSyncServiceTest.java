package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ChampionData;
import dfgg.infrastructure.external.dto.ChampionResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChampionSyncServiceTest {

    @Mock
    private DataDragonClient dataDragonClient;

    @Mock
    private ChampionRepository championRepository;

    @InjectMocks
    private ChampionSyncService championSyncService;

    @Test
    void 데이터_드래곤_응답을_챔피언으로_변환해_저장한다() {
        // given
        ChampionResponse response = new ChampionResponse(Map.of(
                "Aatrox", new ChampionData(
                        "266",
                        "아트록스",
                        List.of("Fighter", "Tank")
                )
        ));
        when(dataDragonClient.getChampions()).thenReturn(response);

        // when
        championSyncService.syncChampions();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Champion>> captor = ArgumentCaptor.forClass(List.class);
        verify(championRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).singleElement().satisfies(champion -> {
            assertThat(champion.getChampionId()).isEqualTo(266L);
            assertThat(champion.getRiotKey()).isEqualTo("Aatrox");
            assertThat(champion.getName()).isEqualTo("아트록스");
            assertThat(champion.getChampionTags())
                    .containsExactly(ChampionTag.FIGHTER, ChampionTag.TANK);
        });
    }

    @Test
    void 데이터_드래곤_조회가_실패하면_저장하지_않는다() {
        // given
        IllegalStateException exception = new IllegalStateException("API failure");
        when(dataDragonClient.getChampions()).thenThrow(exception);

        // when & then
        assertThatThrownBy(championSyncService::syncChampions)
                .isSameAs(exception);
        verifyNoInteractions(championRepository);
    }

    @Test
    void 챔피언_ID가_숫자가_아니면_저장하지_않는다() {
        // given
        ChampionResponse response = new ChampionResponse(Map.of(
                "Aatrox", new ChampionData(
                        "invalid-id",
                        "아트록스",
                        List.of("Fighter")
                )
        ));
        when(dataDragonClient.getChampions()).thenReturn(response);

        // when & then
        assertThatThrownBy(championSyncService::syncChampions)
                .isInstanceOf(NumberFormatException.class);
        verifyNoInteractions(championRepository);
    }
}
