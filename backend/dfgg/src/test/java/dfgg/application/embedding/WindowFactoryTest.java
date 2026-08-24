package dfgg.application.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.embedding.BuildContext;
import dfgg.domain.embedding.ContentContext;
import dfgg.domain.embedding.TeamComposition;
import dfgg.domain.embedding.Window;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WindowFactoryTest {

    private static final double WIN_WEIGHT = 3.0;

    private final WindowFactory windowFactory = new WindowFactory();

    @Test
    @DisplayName("팀 구성 문맥에서 승리한 팀의 윈도우에는 승패 가중치가, 패배한 팀의 윈도우에는 가중치 1.0이 적용된다")
    void createTeamCompositionWindows_AppliesWinWeightPerTeam() {
        // given
        TeamComposition allyTeam = new TeamComposition(List.of("Ahri", "Zed", "Leona", "Jinx", "Nautilus"), true);
        TeamComposition enemyTeam = new TeamComposition(List.of("Garen", "Darius", "Braum", "Ashe", "Sett"), false);

        // when
        List<Window> windows = windowFactory.createTeamCompositionWindows(allyTeam, enemyTeam, WIN_WEIGHT);

        // then
        assertThat(windows.get(0).weight()).isEqualTo(WIN_WEIGHT);
        assertThat(windows.get(1).weight()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("빌드 문맥에서 승리한 참가자의 윈도우에는 승패 가중치가 적용된다")
    void createBuildContextWindow_WhenWin_AppliesWinWeight() {
        // given
        BuildContext buildContext = new BuildContext(
                "Ahri",
                List.of("Zed", "Leona", "Jinx", "Nautilus"),
                List.of("Garen", "Darius", "Braum", "Ashe", "Sett"),
                List.of("RabadonsDeathcap", "VoidStaff"),
                true
        );

        // when
        Window window = windowFactory.createBuildContextWindow(buildContext, WIN_WEIGHT);

        // then
        assertThat(window.weight()).isEqualTo(WIN_WEIGHT);
    }

    @Test
    @DisplayName("빌드 문맥에서 패배한 참가자의 윈도우에는 가중치 1.0이 적용된다")
    void createBuildContextWindow_WhenLose_AppliesDefaultWeight() {
        // given
        BuildContext buildContext = new BuildContext(
                "Ahri",
                List.of("Zed", "Leona", "Jinx", "Nautilus"),
                List.of("Garen", "Darius", "Braum", "Ashe", "Sett"),
                List.of("RabadonsDeathcap", "VoidStaff"),
                false
        );

        // when
        Window window = windowFactory.createBuildContextWindow(buildContext, WIN_WEIGHT);

        // then
        assertThat(window.weight()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("빌드 문맥 윈도우는 내 챔피언, 아군, 적군, 아이템 토큰을 모두 포함한다")
    void createBuildContextWindow_IncludesChampionAllyEnemyAndItemTokens() {
        // given
        BuildContext buildContext = new BuildContext(
                "Ahri",
                List.of("Zed"),
                List.of("Garen"),
                List.of("RabadonsDeathcap"),
                true
        );

        // when
        Window window = windowFactory.createBuildContextWindow(buildContext, WIN_WEIGHT);

        // then
        assertThat(window.tokens()).containsExactlyInAnyOrder("Ahri", "Zed", "Garen", "RabadonsDeathcap");
    }

    @Test
    @DisplayName("콘텐츠 문맥은 매치 결과와 무관한 정적 데이터이므로 승패 가중을 적용하지 않는다")
    void createContentContextWindow_NeverAppliesWinWeight() {
        // given
        ContentContext contentContext = new ContentContext("FrozenHeart", List.of("Armor", "Mana", "Aura"));

        // when
        Window window = windowFactory.createContentContextWindow(contentContext);

        // then
        assertThat(window.weight()).isEqualTo(1.0);
    }
}
