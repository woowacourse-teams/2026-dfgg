package dfgg.application.embedding;

import dfgg.domain.embedding.CounterContext;
import dfgg.domain.embedding.Window;
import dfgg.domain.embedding.TeamComposition;
import dfgg.domain.embedding.ParticipantBuild;
import dfgg.domain.embedding.ContentContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WindowFactory {

    public List<Window> createTeamCompositionWindows(TeamComposition allyTeam, TeamComposition enemyTeam) {
        return List.of(
                new Window(allyTeam.championTokens()),
                new Window(enemyTeam.championTokens())
        );
    }

    public Window createParticipantBuildWindow(ParticipantBuild build) {
        List<String> tokens = new ArrayList<>();
        tokens.add(build.championToken());
        tokens.addAll(build.itemTokens());
        return new Window(tokens);
    }

    public Window createCounterContextWindow(CounterContext counterContext) {
        List<String> tokens = new ArrayList<>();
        tokens.addAll(counterContext.enemyChampionTokens());
        tokens.addAll(counterContext.itemTokens());
        return new Window(tokens);
    }

    public Window createContentContextWindow(ContentContext contentContext) {
        List<String> tokens = new ArrayList<>();
        tokens.add(contentContext.itemToken());
        tokens.addAll(contentContext.tagTokens());
        return new Window(tokens);
    }
}
