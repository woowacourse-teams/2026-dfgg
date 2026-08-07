package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.MatchParticipantCohort;
import dfgg.domain.match.MatchParticipantCohortRepository;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
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
@Import({
        ChampionBuildStatsRebuildService.class,
        ChampionBuildStatsAggregationService.class,
        NormalizedMatchPersistenceService.class,
        MatchNormalizer.class,
        CoreItemPurchaseOrderCalculator.class
})
class ChampionBuildStatsRebuildServiceIntegrationTest {

    @Autowired
    private ChampionBuildStatsRebuildService rebuildService;

    @Autowired
    private RawMatchRepository rawMatchRepository;

    @Autowired
    private RawMatchTimelineRepository rawMatchTimelineRepository;

    @Autowired
    private ChampionRepository championRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private MatchParticipantCohortRepository cohortRepository;

    @Autowired
    private NormalizedMatchParticipantRepository normalizedRepository;

    @Autowired
    private ChampionBuildStatsRepository statsRepository;

    @Autowired
    private CompositionStatsSampleRepository sampleRepository;

    @Test
    void raw_데이터만으로_정규화와_통계를_삭제_후_재생성한다() {
        championRepository.saveAll(List.of(
                new Champion(1L, "Aatrox", "아트록스", List.of(ChampionTag.FIGHTER)),
                new Champion(2L, "Ally", "아군", List.of(ChampionTag.FIGHTER)),
                new Champion(3L, "Enemy", "적군", List.of(ChampionTag.TANK))
        ));
        itemRepository.saveAll(List.of(
                new Item(3071L, "아이템 A"),
                new Item(6610L, "아이템 B")
        ));
        cohortRepository.save(new MatchParticipantCohort(
                "KR_1",
                "p-focal",
                "RANKED_SOLO_5x5",
                "PLATINUM",
                "I",
                java.time.Instant.parse("2026-08-06T08:00:00Z")
        ));
        rawMatchRepository.save(new RawMatch("KR_1", """
                {"info":{"gameVersion":"16.15.1.1","queueId":420,"participants":[
                  {"puuid":"p-focal","participantId":1,"championId":1,"teamId":100,
                   "teamPosition":"TOP","item0":3071,"item1":6610,"win":true},
                  {"puuid":"p-ally","participantId":2,"championId":2,"teamId":100,"win":false},
                  {"puuid":"p-enemy","participantId":3,"championId":3,"teamId":200,"win":false}
                ]}}
                """));
        rawMatchTimelineRepository.save(new RawMatchTimeline("KR_1", """
                {"metadata":{"participants":["p-focal","p-ally","p-enemy"]},"info":{"frames":[
                  {"events":[
                    {"timestamp":100,"type":"ITEM_PURCHASED","participantId":1,"itemId":3071},
                    {"timestamp":200,"type":"ITEM_PURCHASED","participantId":1,"itemId":6610}
                  ]}
                ]}}
                """));

        rebuildService.rebuildAll("PLATINUM");
        assertThat(normalizedRepository.count()).isEqualTo(3);
        assertThat(statsRepository.count()).isEqualTo(32);
        assertThat(sampleRepository.count()).isEqualTo(32);

        rebuildService.rebuildAll("PLATINUM");
        assertThat(normalizedRepository.count()).isEqualTo(3);
        assertThat(statsRepository.count()).isEqualTo(32);
        assertThat(sampleRepository.count()).isEqualTo(32);
        assertThat(statsRepository.findAll())
                .allSatisfy(stats -> {
                    assertThat(stats.getGameCount()).isEqualTo(1);
                    assertThat(stats.getWinCount()).isEqualTo(1);
                });
    }

}
