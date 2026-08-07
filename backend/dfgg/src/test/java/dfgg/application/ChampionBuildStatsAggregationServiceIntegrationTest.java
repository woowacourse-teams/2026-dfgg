package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedParticipant;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CompositionStatsSampleRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChampionBuildStatsAggregationService.class)
class ChampionBuildStatsAggregationServiceIntegrationTest {

    @Autowired
    private ChampionBuildStatsAggregationService aggregationService;

    @Autowired
    private ChampionBuildStatsRepository statsRepository;

    @Autowired
    private CompositionStatsSampleRepository sampleRepository;

    @Autowired
    private ChampionRepository championRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    void 같은_정규화_매치를_두_번_집계해도_카운트가_중복되지_않는다() {
        championRepository.saveAll(List.of(
                new Champion(1L, "Aatrox", "아트록스", List.of(ChampionTag.FIGHTER)),
                new Champion(2L, "Ally", "아군", List.of(ChampionTag.FIGHTER)),
                new Champion(3L, "Enemy", "적군", List.of(ChampionTag.TANK))
        ));
        itemRepository.saveAll(List.of(
                new Item(3071L, "아이템 A"),
                new Item(6610L, "아이템 B")
        ));
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

        aggregationService.aggregate(match, "PLATINUM", List.of("p-focal"));
        aggregationService.aggregate(match, "PLATINUM", List.of("p-focal"));

        assertThat(statsRepository.count()).isEqualTo(32);
        assertThat(sampleRepository.count()).isEqualTo(32);
        assertThat(statsRepository.findAll())
                .allSatisfy(stats -> {
                    assertThat(stats.getGameCount()).isEqualTo(1);
                    assertThat(stats.getWinCount()).isEqualTo(1);
                });
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
