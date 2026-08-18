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

    public List<Window> createTeamCompositionWindows(TeamComposition allyTeam, TeamComposition enemyTeam, double winWeight) {
        return List.of(
                new Window(allyTeam.championTokens(), weightFor(allyTeam.win(), winWeight)),
                new Window(enemyTeam.championTokens(), weightFor(enemyTeam.win(), winWeight))
        );
    }

    public Window createParticipantBuildWindow(ParticipantBuild build, double winWeight) {
        List<String> tokens = new ArrayList<>();
        tokens.add(build.championToken());
        tokens.addAll(build.itemTokens());
        return new Window(tokens, weightFor(build.win(), winWeight));
    }

    public Window createCounterContextWindow(CounterContext counterContext, double winWeight) {
        List<String> tokens = new ArrayList<>();
        tokens.addAll(counterContext.enemyChampionTokens());
        tokens.addAll(counterContext.itemTokens());
        return new Window(tokens, weightFor(counterContext.win(), winWeight));
    }

    private double weightFor(boolean win, double winWeight) {
        return win ? winWeight : 1.0;
    }

    public Window createContentContextWindow(ContentContext contentContext) {
        List<String> tokens = new ArrayList<>();
        tokens.add(contentContext.itemToken());
        tokens.addAll(contentContext.tagTokens());
        return new Window(tokens);
    }
}
