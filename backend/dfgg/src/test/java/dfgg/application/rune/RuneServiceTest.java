package dfgg.application.rune;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.domain.language.Language;
import dfgg.domain.rune.Rune;
import dfgg.domain.rune.RuneKind;
import dfgg.domain.rune.RuneRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.RuneResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuneServiceTest {

    @Mock
    private DataDragonClient dataDragonClient;

    @Mock
    private RuneRepository runeRepository;

    @Mock
    private RuneImageService runeImageService;

    @InjectMocks
    private RuneService runeService;

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("계열·룬·파편을 한·영 이름으로 저장하고 아이콘을 최신 패치 폴더에 올린다")
    void syncRunes_SavesStylesPerksAndShardsWithIcons() {
        // given
        String styleIcon = "perk-images/Styles/7204_Resolve.png";
        String perkIcon = "perk-images/Styles/Resolve/GraspOfTheUndying/GraspOfTheUndying.png";
        when(dataDragonClient.getRunes()).thenReturn(new RuneResponse("16.21", "16.21.1",
                List.of(new RuneResponse.Style(8400, styleIcon, "결의", List.of(new RuneResponse.Slot(
                        List.of(new RuneResponse.Perk(8437, perkIcon, "착취의 손아귀")))))),
                Map.of(8400, "Resolve", 8437, "Grasp of the Undying")));

        // when
        runeService.syncRunes();

        // then
        ArgumentCaptor<List<Rune>> saved = ArgumentCaptor.forClass(List.class);
        verify(runeRepository).saveAll(saved.capture());
        Map<Integer, Rune> byId = saved.getValue().stream()
                .collect(Collectors.toMap(Rune::runeId, rune -> rune));
        assertThat(byId.get(8400).kind()).isEqualTo(RuneKind.STYLE);
        assertThat(byId.get(8400).nameIn(Language.EN_US)).isEqualTo("Resolve");
        assertThat(byId.get(8437).kind()).isEqualTo(RuneKind.PERK);
        assertThat(byId.get(8437).nameIn(Language.KO_KR)).isEqualTo("착취의 손아귀");
        assertThat(byId.get(5008).kind()).isEqualTo(RuneKind.STAT_SHARD);
        assertThat(byId).hasSize(2 + StatShardCatalog.values().length);
        verify(runeImageService).store("16.21", 8400, styleIcon);
        verify(runeImageService).store("16.21", 8437, perkIcon);
        verify(runeImageService).store("16.21", 5008, "perk-images/StatMods/StatModsAdaptiveForceIcon.png");
    }

    @Test
    @DisplayName("영문 이름이 없는 룬이 있으면 아무것도 저장하지 않는다")
    void syncRunes_WhenEnglishNameMissing_SavesNothing() {
        // given
        when(dataDragonClient.getRunes()).thenReturn(new RuneResponse("16.21", "16.21.1",
                List.of(new RuneResponse.Style(8400, "perk-images/Styles/7204_Resolve.png", "결의", List.of())),
                Map.of()));

        // when & then
        assertThatThrownBy(() -> runeService.syncRunes())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("8400");
        verify(runeRepository, never()).saveAll(anyList());
        verify(runeImageService, never()).store(anyString(), anyInt(), anyString());
    }
}
