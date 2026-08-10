package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedParticipant;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CompositionStatsSampleRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChampionBuildStatsAggregationServiceTest {

    @Mock
    private ChampionBuildStatsRepository statsRepository;

    @Mock
    private CompositionStatsSampleRepository sampleRepository;

    @Mock
    private ChampionRepository championRepository;

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ChampionBuildStatsAggregationService aggregationService;

    @Test
    void 대상_PUUID의_코어_아이템_구매_순서를_통계로_집계한다() {
        Champion focal = champion(1L, "FIGHTER");
        Champion ally = champion(2L, "MARKSMAN");
        Champion enemy = champion(3L, "TANK");
        when(championRepository.findAllById(any())).thenReturn(List.of(focal, ally, enemy));
        when(itemRepository.findAllById(any())).thenReturn(List.of(
                new Item(3071L, "아이템 A"),
                new Item(6610L, "아이템 B")
        ));
        when(statsRepository.insertIfAbsent(
                anyString(),
                anyInt(),
                anyLong(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(1);
        when(sampleRepository.insertAndIncrementIfAbsent(
                anyString(),
                anyString(),
                anyString(),
                eq(true)
        )).thenReturn(1);

        NormalizedMatch match = new NormalizedMatch(
                "KR_1",
                "16.15",
                420,
                List.of(
                        participant("p-focal", 1, 1, 100, "TOP", true),
                        participant("p-ally", 2, 2, 100, "JUNGLE", false),
                        participant("p-enemy", 3, 3, 200, "TOP", false)
                )
        );

        int recorded = aggregationService.aggregate(match, "PLATINUM", List.of("p-focal"));

        assertThat(recorded).isEqualTo(32);
        verify(statsRepository, times(32)).insertIfAbsent(
                eq("16.15"),
                eq(420),
                eq(1L),
                eq("TOP"),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq("PLATINUM"),
                eq("3071>6610"),
                anyString()
        );
        verify(statsRepository, times(64)).insertItem(anyString(), anyLong(), anyInt());
        ArgumentCaptor<String> statsKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(sampleRepository, times(32)).insertAndIncrementIfAbsent(
                statsKeyCaptor.capture(),
                eq("KR_1"),
                eq("p-focal"),
                eq(true)
        );
        assertThat(statsKeyCaptor.getAllValues())
                .hasSize(32)
                .allMatch(statsKey -> statsKey.startsWith("16.15|420|1|TOP|"))
                .allMatch(statsKey -> statsKey.endsWith("|PLATINUM|3071>6610"));
    }

    private Champion champion(Long id, String tag) {
        return new Champion(id, "champion-" + id, "챔피언" + id, List.of(ChampionTag.valueOf(tag)));
    }

    private NormalizedParticipant participant(
            String puuid,
            int participantId,
            int championId,
            int teamId,
            String position,
            boolean win
    ) {
        return new NormalizedParticipant(
                puuid,
                participantId,
                championId,
                teamId,
                position,
                win,
                List.of(3071, 6610),
                List.of(3071, 6610),
                true
        );
    }
}
