package dfgg.application.recommend.v3;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;
import java.util.Objects;

/**
 * 요청이 보낸 10명의 상태. 아직 누가 어느 챔피언인지와 묶이지 않았다.
 *
 * <p>아군과 상대는 요청의 선수 목록과 같은 순서로 온다. 챔피언 이름을 번호로 바꾸는 쪽이
 * 그 순서대로 짝지어 {@link GameSituation}을 만든다.
 */
public record ReportedGameState(MyState me, List<Player> allies, List<Player> enemies) {

    public ReportedGameState {
        Objects.requireNonNull(me, "내 상태는 필수입니다.");
        allies = List.copyOf(allies);
        enemies = List.copyOf(enemies);
    }

    /**
     * 아군 또는 상대 한 명의 상태. {@code itemIds}는 그 선수가 들고 있는 아이템을 요청이 보낸 그대로 갖는다.
     * 장신구와 소모품이 섞여 있고 같은 번호가 여러 번 올 수 있다.
     */
    public record Player(int level, int kills, int assists, List<Long> itemIds) {

        public Player {
            itemIds = List.copyOf(itemIds);
        }

        /**
         * 같은 번호는 한 번만 싣는다. 학습 데이터의 보유 목록이 같은 Core를 한 번만 들기 때문이다.
         * Core가 아닌 번호는 여기서 거르지 않는다. feature를 뽑는 쪽이 Core만 읽는다.
         */
        public PlayerState toPlayerState(Long championId, ChampionPosition position) {
            return new PlayerState(championId, position, level, kills, assists,
                    itemIds.stream().distinct().toList());
        }
    }
}
