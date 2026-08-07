package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChampionBuildStatsRepositoryTest {

    @Autowired
    private ChampionBuildStatsRepository statsRepository;

    @Autowired
    private ChampionRepository championRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 통계_ID는_자동_생성되고_빌드_정보를_저장한다() {
        Champion champion = championRepository.save(new Champion(
                266L,
                "Aatrox",
                "아트록스",
                List.of(ChampionTag.FIGHTER, ChampionTag.TANK)
        ));
        Item item = itemRepository.save(new Item(3071L, "칠흑의 양날 도끼"));

        ChampionBuildStats stats = statsRepository.save(new ChampionBuildStats(
                "16.15",
                420,
                champion,
                ChampionPosition.TOP,
                false,
                false,
                false,
                false,
                false,
                "PLATINUM",
                "3071",
                List.of(item),
                1,
                2
        ));
        entityManager.flush();
        entityManager.clear();

        ChampionBuildStats saved = statsRepository.findById(stats.getId()).orElseThrow();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPatch()).isEqualTo("16.15");
        assertThat(saved.getQueueId()).isEqualTo(420);
        assertThat(saved.getBuildKey()).isEqualTo("3071");
        assertThat(saved.getItems()).extracting(Item::getItemId).containsExactly(3071L);
    }

    @Test
    void 동일한_집계_키는_중복_저장할_수_없다() {
        Champion champion = championRepository.save(new Champion(
                266L,
                "Aatrox",
                "아트록스",
                List.of(ChampionTag.FIGHTER)
        ));
        ChampionBuildStats first = statsRepository.save(new ChampionBuildStats(
                "16.15", 420, champion, ChampionPosition.TOP,
                false, false, false, false, false,
                "PLATINUM", "3071", List.of(), 1, 1
        ));
        statsRepository.saveAndFlush(first);

        ChampionBuildStats duplicate = new ChampionBuildStats(
                "16.15", 420, champion, ChampionPosition.TOP,
                false, false, false, false, false,
                "PLATINUM", "3071", List.of(), 1, 1
        );

        assertThatThrownBy(() -> statsRepository.save(duplicate))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasMessageContaining("duplicate");
    }
}
