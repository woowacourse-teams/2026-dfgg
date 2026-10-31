package dfgg.application.propick;

import dfgg.domain.propick.ProPick;
import dfgg.domain.propick.ProPickRepository;
import dfgg.domain.propick.ProPlayer;
import dfgg.domain.propick.ProTournament;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 하나의 픽을 한 트랜잭션으로 저장한다. 같은 (경기, 선수)는 새로 만들지 않고 갱신한다.
 */
@Component
public class ProPickWriter {

    private final ProPickRepository pickRepository;

    public ProPickWriter(ProPickRepository pickRepository) {
        this.pickRepository = pickRepository;
    }

    /** @return 저장하거나 갱신한 픽 수 */
    @Transactional
    public int save(List<ProPickDraft> drafts, Map<String, ProTournament> tournaments, Map<String, ProPlayer> players) {
        List<String> gameIds = drafts.stream().map(ProPickDraft::sourceGameId).distinct().toList();
        Map<String, ProPick> stored = new HashMap<>();
        pickRepository.findBySourceGameIdIn(gameIds)
                .forEach(pick -> stored.put(keyOf(pick.getSourceGameId(), pick.getPlayer()), pick));
        Set<String> savedKeys = new HashSet<>();
        for (ProPickDraft draft : drafts) {
            ProPlayer player = players.get(ProPlayer.canonicalPage(draft.playerPage()));
            ProTournament tournament = tournaments.get(draft.overviewPage());
            String key = keyOf(draft.sourceGameId(), player);
            savedKeys.add(key);
            ProPick pick = stored.computeIfAbsent(key, ignored -> new ProPick(tournament, player, draft.sourceGameId()));
            pick.update(tournament, draft.playedAt(), draft.team(), draft.side(), draft.position(),
                    draft.championId().longValue(), draft.win(), draft.spells(), draft.items(), draft.runes());
        }
        pickRepository.saveAll(stored.values());
        return savedKeys.size();
    }

    private String keyOf(String sourceGameId, ProPlayer player) {
        return sourceGameId + "|" + player.getId();
    }
}
