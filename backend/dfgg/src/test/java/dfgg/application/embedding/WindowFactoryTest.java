package dfgg.application.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.embedding.ContentContext;
import dfgg.domain.embedding.CounterContext;
import dfgg.domain.embedding.ParticipantBuild;
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
        TeamComposition allyTeam = new TeamComposition(List.of("Ahri", "Zed", "Leona", "Jinx", "Nautilus"), true);
        TeamComposition enemyTeam = new TeamComposition(List.of("Garen", "Darius", "Braum", "Ashe", "Sett"), false);

        List<Window> windows = windowFactory.createTeamCompositionWindows(allyTeam, enemyTeam, WIN_WEIGHT);

        assertThat(windows.get(0).weight()).isEqualTo(WIN_WEIGHT);
        assertThat(windows.get(1).weight()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("참가자-빌드 문맥에서 승리한 참가자의 윈도우에는 승패 가중치가 적용된다")
    void createParticipantBuildWindow_WhenWin_AppliesWinWeight() {
        ParticipantBuild build = new ParticipantBuild("Ahri", List.of("RabadonsDeathcap", "VoidStaff"), true);

        Window window = windowFactory.createParticipantBuildWindow(build, WIN_WEIGHT);

        assertThat(window.weight()).isEqualTo(WIN_WEIGHT);
    }

    @Test
    @DisplayName("참가자-빌드 문맥에서 패배한 참가자의 윈도우에는 가중치 1.0이 적용된다")
    void createParticipantBuildWindow_WhenLose_AppliesDefaultWeight() {
        ParticipantBuild build = new ParticipantBuild("Ahri", List.of("RabadonsDeathcap", "VoidStaff"), false);

        Window window = windowFactory.createParticipantBuildWindow(build, WIN_WEIGHT);

        assertThat(window.weight()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("대응(카운터) 문맥에서 승리한 참가자의 윈도우에는 승패 가중치가 적용된다")
    void createCounterContextWindow_WhenWin_AppliesWinWeight() {
        CounterContext counterContext = new CounterContext(
                List.of("Garen", "Darius", "Braum", "Ashe", "Sett"),
                List.of("FrozenHeart"),
                true
        );

        Window window = windowFactory.createCounterContextWindow(counterContext, WIN_WEIGHT);

        assertThat(window.weight()).isEqualTo(WIN_WEIGHT);
    }

    @Test
    @DisplayName("대응(카운터) 문맥에서 패배한 참가자의 윈도우에는 가중치 1.0이 적용된다")
    void createCounterContextWindow_WhenLose_AppliesDefaultWeight() {
        CounterContext counterContext = new CounterContext(
                List.of("Garen", "Darius", "Braum", "Ashe", "Sett"),
                List.of("FrozenHeart"),
                false
        );

        Window window = windowFactory.createCounterContextWindow(counterContext, WIN_WEIGHT);

        assertThat(window.weight()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("콘텐츠 문맥은 매치 결과와 무관한 정적 데이터이므로 승패 가중을 적용하지 않는다")
    void createContentContextWindow_NeverAppliesWinWeight() {
        ContentContext contentContext = new ContentContext("FrozenHeart", List.of("Armor", "Mana", "Aura"));

        Window window = windowFactory.createContentContextWindow(contentContext);

        assertThat(window.weight()).isEqualTo(1.0);
    }
}
