package dfgg.application.propick;

import dfgg.infrastructure.external.client.CargoQuery;
import dfgg.infrastructure.external.client.LeaguepediaClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 리그 하나의 최근 경기 선수 행(Leaguepedia {@code ScoreboardPlayers})을 받는다.
 */
@Component
public class ProScoreboardSource {

    private static final String FIELDS = String.join(", ",
            "SP.GameId", "SP.OverviewPage", "SP.DateTime_UTC", "SP.Team", "SP.Side", "SP.PlayerWin", "SP.Link",
            "SP.Role", "SP.Champion", "SP.SummonerSpells", "SP.Items", "SP.Trinket", "SP.Runes");

    private final LeaguepediaClient leaguepediaClient;

    public ProScoreboardSource(LeaguepediaClient leaguepediaClient) {
        this.leaguepediaClient = leaguepediaClient;
    }

    /** 접두사는 {@link dfgg.domain.propick.ProLeagues}가 검증한 값이다. */
    public List<Map<String, String>> fetchLeague(String prefix, LocalDate since) {
        CargoQuery query = new CargoQuery(
                "ScoreboardPlayers=SP",
                FIELDS,
                "SP.OverviewPage LIKE '" + prefix + "%' AND SP.DateTime_UTC >= '" + since + "'",
                "SP.DateTime_UTC DESC");
        // Cargo LIKE는 대소문자를 가리지 않아 다른 대회가 섞일 수 있다. 접두사로 한 번 더 거른다.
        return leaguepediaClient.cargoQuery(query).stream()
                .filter(row -> row.getOrDefault("OverviewPage", "").startsWith(prefix))
                .toList();
    }
}
