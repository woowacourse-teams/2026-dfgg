package dfgg.application.embedding;

import dfgg.domain.embedding.BuildContext;
import dfgg.domain.embedding.CounterTeamContext;
import dfgg.domain.embedding.Window;
import dfgg.domain.embedding.TeamComposition;
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

    public Window createBuildContextWindow(BuildContext buildContext, double winWeight) {
        List<String> tokens = new ArrayList<>();
        tokens.add(buildContext.championToken());
        tokens.addAll(buildContext.itemTokens());
        return new Window(tokens, weightFor(buildContext.win(), winWeight));
    }

    public Window createCounterTeamWindow(CounterTeamContext counterTeamContext, double winWeight) {
        List<String> tokens = new ArrayList<>();
        tokens.add(counterTeamContext.enemyChampionToken());
        tokens.addAll(counterTeamContext.itemTokens());
        return new Window(tokens, weightFor(counterTeamContext.win(), winWeight));
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
