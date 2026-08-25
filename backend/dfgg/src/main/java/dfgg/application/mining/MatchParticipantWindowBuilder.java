package dfgg.application.mining;

import dfgg.application.embedding.WindowFactory;
import dfgg.domain.embedding.BuildContext;
import dfgg.domain.embedding.ContentContext;
import dfgg.domain.embedding.CounterTeamContext;
import dfgg.domain.embedding.TeamComposition;
import dfgg.domain.embedding.Window;
import dfgg.domain.item.Item;
import dfgg.domain.match.NormalizedMatchParticipant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MatchParticipantWindowBuilder {

    private final WindowFactory windowFactory;
    private final Map<Integer, String> championTokenCache = new ConcurrentHashMap<>();
    private final Map<Long, String> itemTokenCache = new ConcurrentHashMap<>();

    public MatchParticipantWindowBuilder(WindowFactory windowFactory) {
        this.windowFactory = windowFactory;
    }

    public List<Window> buildMatchWindows(List<NormalizedMatchParticipant> matchParticipants, double winWeight) {
        Map<Integer, List<NormalizedMatchParticipant>> byTeam = groupByTeam(matchParticipants);

        List<Window> windows = new ArrayList<>();
        windows.addAll(teamCompositionWindows(byTeam, winWeight));

        for (NormalizedMatchParticipant participant : matchParticipants) {
            windows.add(windowFactory.createBuildContextWindow(buildContext(participant), winWeight));
        }
        return windows;
    }

    public List<Window> buildCounterWindows(List<NormalizedMatchParticipant> matchParticipants, double winWeight) {
        Map<Integer, List<NormalizedMatchParticipant>> byTeam = groupByTeam(matchParticipants);
        if (byTeam.size() != 2) {
            return List.of();
        }

        List<Window> windows = new ArrayList<>();
        for (Map.Entry<Integer, List<NormalizedMatchParticipant>> entry : byTeam.entrySet()) {
            List<NormalizedMatchParticipant> team = entry.getValue();
            List<NormalizedMatchParticipant> enemyTeam = opposingTeam(byTeam, entry.getKey());
            windows.add(windowFactory.createCounterTeamWindow(counterTeamContext(team, enemyTeam), winWeight));
        }
        return windows;
    }

    public List<Window> buildContentContextWindows(List<Item> items) {
        return items.stream()
                .filter(item -> !item.getTags().isEmpty())
                .map(item -> windowFactory.createContentContextWindow(
                        new ContentContext(itemToken(item.getItemId()), item.getTags())
                ))
                .toList();
    }

    private Map<Integer, List<NormalizedMatchParticipant>> groupByTeam(List<NormalizedMatchParticipant> matchParticipants) {
        return matchParticipants.stream()
                .collect(Collectors.groupingBy(NormalizedMatchParticipant::getTeamId));
    }

    private List<Window> teamCompositionWindows(Map<Integer, List<NormalizedMatchParticipant>> byTeam, double winWeight) {
        if (byTeam.size() != 2) {
            return List.of();
        }
        List<Integer> teamIds = new ArrayList<>(byTeam.keySet());
        return windowFactory.createTeamCompositionWindows(
                teamComposition(byTeam.get(teamIds.get(0))),
                teamComposition(byTeam.get(teamIds.get(1))),
                winWeight
        );
    }

    private TeamComposition teamComposition(List<NormalizedMatchParticipant> team) {
        return new TeamComposition(championTokens(team), team.get(0).getWin());
    }

    private BuildContext buildContext(NormalizedMatchParticipant participant) {
        return new BuildContext(
                championToken(participant.getChampionId()),
                itemTokens(participant.getFinalCoreItemIds()),
                participant.getWin()
        );
    }

    private CounterTeamContext counterTeamContext(
            List<NormalizedMatchParticipant> team,
            List<NormalizedMatchParticipant> enemyTeam
    ) {
        Set<String> teamItemTokens = new LinkedHashSet<>();
        for (NormalizedMatchParticipant participant : team) {
            teamItemTokens.addAll(itemTokens(participant.getFinalCoreItemIds()));
        }
        return new CounterTeamContext(
                championTokens(enemyTeam),
                List.copyOf(teamItemTokens),
                team.get(0).getWin()
        );
    }

    private List<NormalizedMatchParticipant> opposingTeam(
            Map<Integer, List<NormalizedMatchParticipant>> byTeam,
            Integer teamId
    ) {
        return byTeam.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(teamId))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private List<String> championTokens(List<NormalizedMatchParticipant> team) {
        return team.stream()
                .map(NormalizedMatchParticipant::getChampionId)
                .map(this::championToken)
                .toList();
    }

    private List<String> itemTokens(List<Integer> itemIds) {
        return itemIds.stream()
                .map(this::itemToken)
                .toList();
    }

    private String championToken(int championId) {
        return championTokenCache.computeIfAbsent(championId, id -> Integer.toString(id));
    }

    private String itemToken(long itemId) {
        return itemTokenCache.computeIfAbsent(itemId, id -> Long.toString(id));
    }
}
