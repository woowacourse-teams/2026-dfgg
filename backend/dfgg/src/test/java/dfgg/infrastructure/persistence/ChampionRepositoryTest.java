package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChampionRepositoryTest {

    @Autowired
    private ChampionRepository championRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 챔피언과_태그를_저장한다() {
        // given
        Champion champion = new Champion(
                266L,
                "Aatrox",
                Map.of("ko-KR", "아트록스"),
                List.of(ChampionTag.FIGHTER, ChampionTag.TANK)
        );

        // when
        championRepository.save(champion);
        entityManager.flush();
        entityManager.clear();

        // then
        Champion saved = championRepository.findById(266L).orElseThrow();
        assertThat(saved.getRiotKey()).isEqualTo("Aatrox");
        assertThat(saved.getName()).isEqualTo(Map.of("ko-KR", "아트록스"));
        assertThat(saved.getChampionTags())
                .containsExactlyInAnyOrder(ChampionTag.FIGHTER, ChampionTag.TANK);
    }

    @Test
    void 같은_ID의_챔피언을_다시_저장하면_기존_데이터를_갱신한다() {
        // given
        championRepository.save(new Champion(
                266L,
                "Aatrox",
                Map.of("ko-KR", "이전 이름"),
                List.of(ChampionTag.FIGHTER)
        ));
        entityManager.flush();
        entityManager.clear();

        // when
        championRepository.saveAll(List.of(new Champion(
                266L,
                "Aatrox",
                Map.of("ko-KR", "아트록스"),
                List.of(ChampionTag.FIGHTER, ChampionTag.TANK)
        )));
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(championRepository.count()).isEqualTo(1);

        Champion updated = championRepository.findById(266L).orElseThrow();
        assertThat(updated.getName()).isEqualTo(Map.of("ko-KR", "아트록스"));
        assertThat(updated.getChampionTags())
                .containsExactlyInAnyOrder(ChampionTag.FIGHTER, ChampionTag.TANK);
    }
    @Test
    void 다국어_이름을_JSONB로_저장하고_언어별_이름으로_검색한다() {
        Map<String, String> names = Map.of("ko-KR", "아리", "en-US", "Ahri");
        championRepository.save(new Champion(
                103L, "Ahri", names, List.of(ChampionTag.MAGE)
        ));
        entityManager.flush();
        entityManager.clear();

        Champion saved = championRepository.findById(103L).orElseThrow();
        assertThat(saved.getName()).isEqualTo(names);
        assertThat(entityManager.createNativeQuery(
                "SELECT jsonb_typeof(name) FROM champions WHERE champion_id = 103",
                String.class
        ).getSingleResult()).isEqualTo("object");
        assertThat(championRepository.findByNameIgnoreCase("아리"))
                .map(Champion::getChampionId).contains(103L);
        assertThat(championRepository.findByNameIgnoreCase("aHrI"))
                .map(Champion::getChampionId).contains(103L);
        assertThat(championRepository.findByNameIgnoreCase("없는 이름")).isEmpty();
    }

}
