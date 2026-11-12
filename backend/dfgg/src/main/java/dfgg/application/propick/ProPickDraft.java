package dfgg.application.propick;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.propick.ProPickItem;
import dfgg.domain.propick.ProPickRune;
import dfgg.domain.propick.ProPickSide;
import dfgg.domain.propick.ProPickSpell;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Leaguepedia 한 행을 id로 바꾼 픽. 대회·선수는 아직 페이지 이름이고 저장할 때 행으로 바꾼다.
 */
public record ProPickDraft(
        String sourceGameId,
        String overviewPage,
        String playerPage,
        String team,
        OffsetDateTime playedAt,
        ProPickSide side,
        ChampionPosition position,
        Integer championId,
        boolean win,
        List<ProPickSpell> spells,
        List<ProPickItem> items,
        List<ProPickRune> runes
) {
}
