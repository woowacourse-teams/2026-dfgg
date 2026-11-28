package dfgg.domain.itemstats;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(statements = {
        "DELETE FROM champion_item_stats",
        "INSERT INTO champion_item_stats (champion_id, position, item_id, purchase_count_all, purchase_count_recent, "
                + "win_count_all, win_count_recent, champion_game_count_all, champion_game_count_recent, "
                + "champion_win_count_all) VALUES "
                + "(157, 'MID', 6673, 30, 10, 15, 5, 40, 12, 20), "
                + "(157, 'MID', 3031, 20, 5, 10, 2, 40, 12, 20), "
                + "(222, 'BOTTOM', 6673, 80, 30, 40, 15, 90, 35, 45), "
                + "(117, 'SUPPORT', 3190, 5, 1, 2, 1, 7, 2, 3), "
                + "(157, 'TOP', 3071, 3, 1, 1, 0, 7, 2, 3)"
})
class ChampionItemStatsGameCountTest {

    @Autowired
    private ChampionItemStatsRepository repository;

    @Test
    @DisplayName("챔피언, 포지션마다 판 수 하나를 내고 판 수가 많은 순으로 정렬한다")
    void findGameCountsByChampionPosition_WhenSeveralItemsPerChampion_ReturnsOneRowPerChampionPositionSortedByGames() {
        // when
        List<ChampionPositionGameCount> counts = repository.findGameCountsByChampionPosition();

        // then: 판 수가 같으면 챔피언 ID, 포지션 순이다
        assertThat(counts).containsExactly(
                new ChampionPositionGameCount(222, ChampionPosition.BOTTOM, 90),
                new ChampionPositionGameCount(157, ChampionPosition.MID, 40),
                new ChampionPositionGameCount(117, ChampionPosition.SUPPORT, 7),
                new ChampionPositionGameCount(157, ChampionPosition.TOP, 7)
        );
    }
}
