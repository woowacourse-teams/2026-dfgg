package dfgg.infrastructure.persistence;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChampionTagFetchTest {

    @Autowired
    private ChampionRepository championRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("영속성 컨텍스트에서 분리된 뒤에도 태그를 읽을 수 있다 — feature 추출은 트랜잭션 밖에서도 돈다")
    void findAllWithTags_WhenDetached_TagsAreAlreadyLoaded() {
        // given
        championRepository.save(new Champion(157L, "Yasuo", "야스오", List.of(ChampionTag.FIGHTER)));
        championRepository.save(new Champion(33L, "Rammus", "람머스", List.of(ChampionTag.TANK)));
        entityManager.flush();

        // when: 조회 후 컨텍스트에서 분리한다 (평가 하네스·배치가 이 상태로 쓴다)
        List<Champion> champions = championRepository.findAllWithTagsByChampionIdIn(List.of(157L, 33L));
        entityManager.clear();

        // then: 지연 로딩이면 여기서 LazyInitializationException이 난다
        assertThat(champions).hasSize(2);
        assertThat(champions).flatExtracting(Champion::getChampionTags)
                .containsExactlyInAnyOrder(ChampionTag.FIGHTER, ChampionTag.TANK);
    }

    @Test
    @DisplayName("태그가 없는 챔피언도 조회된다")
    void findAllWithTags_WhenChampionHasNoTags_StillReturnsIt() {
        // given
        championRepository.save(new Champion(999L, "NoTag", "무태그", List.of()));
        entityManager.flush();
        entityManager.clear();

        // when
        List<Champion> champions = championRepository.findAllWithTagsByChampionIdIn(List.of(999L));

        // then
        assertThat(champions).hasSize(1);
        assertThat(champions.get(0).getChampionTags()).isEmpty();
    }
}
