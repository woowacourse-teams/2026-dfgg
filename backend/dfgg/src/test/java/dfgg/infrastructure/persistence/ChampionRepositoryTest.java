package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
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
                "아트록스",
                List.of(ChampionTag.FIGHTER, ChampionTag.TANK)
        );

        // when
        championRepository.save(champion);
        entityManager.flush();
        entityManager.clear();

        // then
        Champion saved = championRepository.findById(266L).orElseThrow();
        assertThat(saved.getRiotKey()).isEqualTo("Aatrox");
        assertThat(saved.getName()).isEqualTo("아트록스");
        assertThat(saved.getChampionTags())
                .containsExactlyInAnyOrder(ChampionTag.FIGHTER, ChampionTag.TANK);
    }

    @Test
    void 같은_ID의_챔피언을_다시_저장하면_기존_데이터를_갱신한다() {
        // given
        championRepository.save(new Champion(
                266L,
                "Aatrox",
                "이전 이름",
                List.of(ChampionTag.FIGHTER)
        ));
        entityManager.flush();
        entityManager.clear();

        // when
        championRepository.saveAll(List.of(new Champion(
                266L,
                "Aatrox",
                "아트록스",
                List.of(ChampionTag.FIGHTER, ChampionTag.TANK)
        )));
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(championRepository.count()).isEqualTo(1);

        Champion updated = championRepository.findById(266L).orElseThrow();
        assertThat(updated.getName()).isEqualTo("아트록스");
        assertThat(updated.getChampionTags())
                .containsExactlyInAnyOrder(ChampionTag.FIGHTER, ChampionTag.TANK);
    }
}
