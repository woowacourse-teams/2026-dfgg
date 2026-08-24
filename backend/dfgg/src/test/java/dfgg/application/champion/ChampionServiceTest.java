package dfgg.application.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.common.ChampionNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ChampionData;
import dfgg.infrastructure.external.dto.ChampionResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChampionServiceTest {

    @Mock
    private DataDragonClient dataDragonClient;

    @Mock
    private ChampionRepository championRepository;

    @InjectMocks
    private ChampionService championService;

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
        championService.syncChampions();

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
        assertThatThrownBy(championService::syncChampions)
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
        assertThatThrownBy(championService::syncChampions)
                .isInstanceOf(NumberFormatException.class);
        verifyNoInteractions(championRepository);
    }

    @Test
    void 라이엇_키로_챔피언을_찾는다() {
        // given
        Champion champion = new Champion(266L, "Aatrox", "아트록스", List.of(ChampionTag.FIGHTER));
        when(championRepository.findByRiotKeyIgnoreCase("Aatrox"))
                .thenReturn(Optional.of(champion));

        // when
        Champion foundChampion = championService.findChampionByName("  Aatrox  ");

        // then
        assertThat(foundChampion).isSameAs(champion);
    }

    @Test
    void 챔피언_이름이_비어_있으면_예외가_발생한다() {
        assertThatThrownBy(() -> championService.findChampionByName(" "))
                .isInstanceOf(ChampionNotFoundException.class)
                .hasMessageContaining("빈 이름");

        verifyNoInteractions(championRepository, dataDragonClient);
    }

    @Test
    void 챔피언을_찾을_수_없으면_예외가_발생한다() {
        // given
        when(championRepository.findByRiotKeyIgnoreCase("Unknown"))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> championService.findChampionByName("Unknown"))
                .isInstanceOf(ChampionNotFoundException.class)
                .hasMessageContaining("Unknown");
    }
}
