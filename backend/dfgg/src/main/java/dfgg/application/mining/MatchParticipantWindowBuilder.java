package dfgg.application.mining;

import dfgg.application.embedding.WindowFactory;
import dfgg.domain.embedding.ContentContext;
import dfgg.domain.embedding.CounterContext;
import dfgg.domain.embedding.ParticipantBuild;
import dfgg.domain.embedding.TeamComposition;
import dfgg.domain.embedding.Window;
import dfgg.domain.item.Item;
import dfgg.domain.match.NormalizedMatchParticipant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MatchParticipantWindowBuilder {

    private final WindowFactory windowFactory;

    public MatchParticipantWindowBuilder(WindowFactory windowFactory) {
        this.windowFactory = windowFactory;
    }

    public List<Window> buildMatchWindows(List<NormalizedMatchParticipant> matchParticipants, double winWeight) {
        Map<Integer, List<NormalizedMatchParticipant>> byTeam = matchParticipants.stream()
                .collect(Collectors.groupingBy(NormalizedMatchParticipant::getTeamId));

        List<Window> windows = new ArrayList<>();
        windows.addAll(teamCompositionWindows(byTeam, winWeight));

        for (NormalizedMatchParticipant participant : matchParticipants) {
            windows.add(windowFactory.createParticipantBuildWindow(participantBuild(participant), winWeight));
            List<NormalizedMatchParticipant> enemyTeam = opposingTeam(byTeam, participant.getTeamId());
            if (enemyTeam != null) {
                windows.add(windowFactory.createCounterContextWindow(
                        counterContext(participant, enemyTeam), winWeight
                ));
            }
        }
        return windows;
    }

    public List<Window> buildContentContextWindows(List<Item> items) {
        return items.stream()
                .filter(item -> !item.getTags().isEmpty())
                .map(item -> windowFactory.createContentContextWindow(
                        new ContentContext(String.valueOf(item.getItemId()), item.getTags())
                ))
                .toList();
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

    private ParticipantBuild participantBuild(NormalizedMatchParticipant participant) {
        return new ParticipantBuild(
                String.valueOf(participant.getChampionId()),
                itemTokens(participant.getFinalCoreItemIds()),
                participant.getWin()
        );
    }

    private CounterContext counterContext(NormalizedMatchParticipant participant, List<NormalizedMatchParticipant> enemyTeam) {
        return new CounterContext(
                championTokens(enemyTeam),
                itemTokens(participant.getFinalCoreItemIds()),
                participant.getWin()
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
                .map(String::valueOf)
                .toList();
    }

    private List<String> itemTokens(List<Integer> itemIds) {
        return itemIds.stream()
                .map(String::valueOf)
                .toList();
    }
}
