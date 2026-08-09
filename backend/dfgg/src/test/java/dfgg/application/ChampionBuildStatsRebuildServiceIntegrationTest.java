package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.MatchParticipantCohort;
import dfgg.domain.match.MatchParticipantCohortRepository;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.NormalizedParticipant;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CompositionStatsSample;
import dfgg.domain.stats.CompositionStatsSampleRepository;
import jakarta.persistence.EntityManager;
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

    @Autowired
    private EntityManager entityManager;

    @Test
    void 특정_티어를_집계해도_기존의_다른_티어_파생_데이터를_삭제하지_않는다() {
        Champion champion = championRepository.save(new Champion(
                1L,
                "Aatrox",
                "아트록스",
                List.of(ChampionTag.FIGHTER)
        ));
        Item item = itemRepository.save(new Item(3071L, "아이템 A"));
        NormalizedMatch existingMatch = new NormalizedMatch(
                "KR_EXISTING",
                "16.15",
                420,
                List.of(new NormalizedParticipant(
                        "p-existing",
                        1,
                        1,
                        100,
                        "TOP",
                        true,
                        List.of(3071),
                        List.of(3071),
                        true
                ))
        );
        normalizedRepository.saveAndFlush(new NormalizedMatchParticipant(
                existingMatch,
                existingMatch.participants().getFirst()
        ));
        ChampionBuildStats existingStats = statsRepository.saveAndFlush(new ChampionBuildStats(
                "16.15",
                420,
                champion,
                ChampionPosition.TOP,
                null,
                null,
                null,
                null,
                null,
                "GOLD",
                "3071",
                List.of(item),
                1,
                1
        ));
        sampleRepository.saveAndFlush(new CompositionStatsSample(
                existingStats,
                "KR_EXISTING",
                "p-existing"
        ));
        Long existingStatsId = existingStats.getId();
        entityManager.clear();

        int recordedSamples = rebuildService.rebuildAll("PLATINUM");

        entityManager.flush();
        entityManager.clear();
        assertThat(recordedSamples).isZero();
        assertThat(normalizedRepository.findByMatchId("KR_EXISTING")).hasSize(1);
        assertThat(statsRepository.findById(existingStatsId))
                .get()
                .satisfies(stats -> {
                    assertThat(stats.getTier()).isEqualTo("GOLD");
                    assertThat(stats.getGameCount()).isEqualTo(1);
                    assertThat(stats.getWinCount()).isEqualTo(1);
                });
        assertThat(sampleRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_raw_데이터를_반복_집계해도_정규화와_통계가_중복되지_않는다() {
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
