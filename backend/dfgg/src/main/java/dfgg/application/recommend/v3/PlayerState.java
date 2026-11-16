package dfgg.application.recommend.v3;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;

/**
 * 추천을 받는 시점의 아군 또는 상대 한 명의 상태. 서빙 요청에는 참가자 번호가 없어 챔피언 번호로 가리킨다.
 *
 * <p>상황 값 계산에 쓰는 것만 싣는다. {@code coreItemIds}는 그 시각에 들고 있는 Core다.
 * 포지션을 모르는 선수는 {@code position}을 비워 싣는다.
 */
public record PlayerState(
        Long championId,
        ChampionPosition position,
        int level,
        int kills,
        int assists,
        List<Long> coreItemIds
) {

    public PlayerState {
        if (championId == null) {
            throw new IllegalArgumentException("챔피언 ID는 필수입니다.");
        }
        coreItemIds = List.copyOf(coreItemIds);
    }

    /** 포지션을 모르는 선수는 어느 포지션에도 서 있지 않다고 답한다. */
    public boolean playsAt(ChampionPosition position) {
        return this.position != null && this.position == position;
    }
}
