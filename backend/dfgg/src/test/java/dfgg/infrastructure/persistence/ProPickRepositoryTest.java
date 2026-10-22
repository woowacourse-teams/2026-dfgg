package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.propick.ProPick;
import dfgg.domain.propick.ProPickItem;
import dfgg.domain.propick.ProPickRepository;
import dfgg.domain.propick.ProPickRune;
import dfgg.domain.propick.ProPickSide;
import dfgg.domain.propick.ProPickSpell;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/pro-pick-query-test-data.sql")
class ProPickRepositoryTest {

    private static final long WORLDS = 1L;

    @Autowired
    private ProPickRepository pickRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("대회·포지션이 같은 픽만 최근 경기 순으로 읽는다 — 다른 대회·라인은 섞이지 않는다")
    void findByTournamentAndPosition_WhenOtherTournamentOrPositionExists_ReturnOnlyMatchingByPlayedAt() {
        // when
        List<ProPick> picks = pickRepository.findByTournamentAndPosition(WORLDS, ChampionPosition.MID);

        // then
        assertThat(picks).extracting(ProPick::getId).containsExactly(102L, 103L, 101L);
    }

    @Test
    @DisplayName("픽과 함께 선수·대회·챔피언·진영·승패를 읽는다")
    void findByTournamentAndPosition_WhenPickFound_IncludePlayerAndTournament() {
        // when
        ProPick chovy = pickRepository.findByTournamentAndPosition(WORLDS, ChampionPosition.MID).getFirst();

        // then
        assertThat(chovy.getPlayer().getName()).isEqualTo("Chovy");
        assertThat(chovy.getPlayer().getTeam()).isEqualTo("Gen.G");
        assertThat(chovy.getTournament().getName()).isEqualTo("2026 Worlds Main Event");
        assertThat(chovy.getChampionId()).isEqualTo(9001L);
        assertThat(chovy.getSide()).isEqualTo(ProPickSide.RED);
        assertThat(chovy.getPosition()).isEqualTo(ChampionPosition.MID);
        assertThat(chovy.isWin()).isFalse();
    }

    @Test
    @DisplayName("주문·아이템·룬은 넣은 순서가 아니라 칸 순서로 읽고, 빈 칸은 행이 없다")
    void findByTournamentAndPosition_WhenChildrenInsertedOutOfOrder_ReturnBySlot() {
        // when
        ProPick chovy = pickRepository.findByTournamentAndPosition(WORLDS, ChampionPosition.MID).getFirst();

        // then
        assertThat(chovy.getSpells()).extracting(ProPickSpell::getSlot, ProPickSpell::getSpellId)
                .containsExactly(tuple(0, 9001), tuple(1, 9002));
        assertThat(chovy.getItems()).extracting(ProPickItem::getSlot, ProPickItem::getItemId)
                .containsExactly(tuple(1, 9001L), tuple(2, 9002L), tuple(6, 9003L));
        assertThat(chovy.getRunes()).extracting(ProPickRune::getSlot, ProPickRune::getRuneId)
                .containsExactly(tuple(0, 9001), tuple(1, 9002), tuple(4, 9002), tuple(8, 9002));
    }

    @Test
    @DisplayName("픽 수와 상관없이 픽·주문·아이템·룬 네 번의 쿼리로 읽는다 (N+1 없음)")
    void findByTournamentAndPosition_WhenReadingAllRelations_UseFourQueries() {
        // given
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        // when
        List<ProPick> picks = pickRepository.findByTournamentAndPosition(WORLDS, ChampionPosition.MID);
        picks.forEach(pick -> {
            pick.getPlayer().getName();
            pick.getTournament().getName();
            pick.getSpells().size();
            pick.getItems().size();
            pick.getRunes().size();
        });

        // then
        assertThat(picks).hasSize(3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("해당 라인 픽이 없으면 빈 목록이다")
    void findByTournamentAndPosition_WhenNoPickForPosition_ReturnEmpty() {
        // when
        List<ProPick> picks = pickRepository.findByTournamentAndPosition(WORLDS, ChampionPosition.SUPPORT);

        // then
        assertThat(picks).isEmpty();
    }
}
