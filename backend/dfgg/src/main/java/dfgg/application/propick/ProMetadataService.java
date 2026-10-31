package dfgg.application.propick;

import dfgg.domain.propick.ProPlayer;
import dfgg.domain.propick.ProPlayerRepository;
import dfgg.domain.propick.ProTournament;
import dfgg.domain.propick.ProTournamentRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 픽에 나온 대회·선수를 Leaguepedia 정보로 저장하거나 갱신한다. 대회는 OverviewPage, 선수는 위키 페이지로 같은 행을 찾는다.
 */
@Service
public class ProMetadataService {

    private final ProMetadataSource metadataSource;
    private final ProTournamentRepository tournamentRepository;
    private final ProPlayerRepository playerRepository;
    private final Clock clock;

    public ProMetadataService(ProMetadataSource metadataSource, ProTournamentRepository tournamentRepository,
                              ProPlayerRepository playerRepository, Clock clock) {
        this.metadataSource = metadataSource;
        this.tournamentRepository = tournamentRepository;
        this.playerRepository = playerRepository;
        this.clock = clock;
    }

    /** @return OverviewPage별 저장된 대회 */
    @Transactional
    public Map<String, ProTournament> upsertTournaments(Collection<String> overviewPages) {
        List<ProTournamentInfo> infos = metadataSource.tournaments(overviewPages);
        Map<String, ProTournament> stored = new HashMap<>(byKey(
                tournamentRepository.findByOverviewPageIn(overviewPages), ProTournament::getOverviewPage));
        for (ProTournamentInfo info : infos) {
            ProTournament tournament = stored.get(info.overviewPage());
            if (tournament == null) {
                stored.put(info.overviewPage(), tournamentRepository.save(new ProTournament(info.overviewPage(),
                        info.name(), info.league(), info.region(), info.startDate(), info.endDate())));
                continue;
            }
            tournament.update(info.name(), info.league(), info.region(), info.startDate(), info.endDate());
        }
        return stored;
    }

    /** @return 첫 글자를 대문자로 맞춘 위키 페이지별 저장된 선수 */
    @Transactional
    public Map<String, ProPlayer> upsertPlayers(Collection<String> playerPages) {
        List<ProPlayerInfo> infos = metadataSource.players(playerPages);
        List<String> pages = infos.stream().map(ProPlayerInfo::page).toList();
        Map<String, ProPlayer> stored = new HashMap<>(byKey(
                playerRepository.findByLeaguepediaPageIn(pages), ProPlayer::getLeaguepediaPage));
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (ProPlayerInfo info : infos) {
            ProPlayer player = stored.get(info.page());
            if (player == null) {
                stored.put(info.page(), playerRepository.save(
                        new ProPlayer(info.page(), info.name(), info.team(), info.imageUrl(), now)));
                continue;
            }
            player.update(info.name(), info.team(), info.imageUrl(), now);
        }
        return stored;
    }

    private <T> Map<String, T> byKey(List<T> entities, Function<T, String> keyOf) {
        return entities.stream().collect(Collectors.toMap(keyOf, Function.identity()));
    }
}
