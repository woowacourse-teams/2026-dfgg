package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.domain.champion.ChampionPosition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GameSituationTest {

    private static final long AHRI = 103L;
    private static final long ZED = 238L;
    private static final long RABADONS_DEATHCAP = 3089L;
    private static final long ZHONYAS_HOURGLASS = 3157L;

    private final MyState me = new MyState(9, 3, 1, 4);

    private PlayerState player(long championId, ChampionPosition position) {
        return new PlayerState(championId, position, 8, 2, 1, List.of());
    }

    @Test
    @DisplayName("상황을 보내지 않은 요청의 상황은 모름이다")
    void unknown_WhenCreated_IsUnknown() {
        // when
        GameSituation situation = GameSituation.unknown();

        // then
        assertThat(situation.isUnknown()).isTrue();
    }

    @Test
    @DisplayName("나·아군·상대의 상태로 만든 상황은 모름이 아니다")
    void of_WhenPlayersGiven_IsNotUnknown() {
        // when
        GameSituation situation = GameSituation.of(
                me, List.of(player(AHRI, ChampionPosition.MID)), List.of(player(ZED, ChampionPosition.MID)));

        // then
        assertThat(situation.isUnknown()).isFalse();
    }

    @Test
    @DisplayName("아군·상대가 한 명도 없어도 모름과는 다르다 — 아는데 선수가 없는 것이다")
    void of_WhenNoPlayers_IsNotUnknown() {
        // when
        GameSituation situation = GameSituation.of(me, List.of(), List.of());

        // then
        assertThat(situation.isUnknown()).isFalse();
        assertThat(situation).isNotEqualTo(GameSituation.unknown());
    }

    @Test
    @DisplayName("만든 뒤 아군·상대 목록 원본을 바꿔도 상황은 영향받지 않는다")
    void of_WhenSourceListsMutatedAfterwards_SituationIsUnaffected() {
        // given
        List<PlayerState> allies = new ArrayList<>(List.of(player(AHRI, ChampionPosition.MID)));
        List<PlayerState> enemies = new ArrayList<>(List.of(player(ZED, ChampionPosition.MID)));
        GameSituation.Known situation = GameSituation.of(me, allies, enemies);

        // when
        allies.clear();
        enemies.clear();

        // then
        assertThat(situation.allies()).hasSize(1);
        assertThat(situation.enemies()).hasSize(1);
    }

    @Test
    @DisplayName("내 상태 없이는 아는 상황을 만들 수 없다")
    void of_WhenMyStateMissing_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> GameSituation.of(null, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("만든 뒤 Core 목록 원본을 바꿔도 선수 상태는 영향받지 않는다")
    void construct_WhenCoreItemIdsMutatedAfterwards_PlayerStateIsUnaffected() {
        // given
        List<Long> coreItemIds = new ArrayList<>(List.of(RABADONS_DEATHCAP, ZHONYAS_HOURGLASS));
        PlayerState player = new PlayerState(AHRI, ChampionPosition.MID, 11, 4, 2, coreItemIds);

        // when
        coreItemIds.clear();

        // then
        assertThat(player.coreItemIds()).containsExactly(RABADONS_DEATHCAP, ZHONYAS_HOURGLASS);
    }

    @Test
    @DisplayName("챔피언 번호가 없는 선수 상태는 만들 수 없다")
    void construct_WhenChampionIdMissing_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> new PlayerState(null, ChampionPosition.MID, 11, 4, 2, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("선수는 자기 포지션을 물으면 그렇다고 답한다")
    void playsAt_WhenSamePosition_ReturnsTrue() {
        // given
        PlayerState player = player(ZED, ChampionPosition.MID);

        // when & then
        assertThat(player.playsAt(ChampionPosition.MID)).isTrue();
    }

    @Test
    @DisplayName("선수는 다른 포지션을 물으면 아니라고 답한다")
    void playsAt_WhenDifferentPosition_ReturnsFalse() {
        // given
        PlayerState player = player(ZED, ChampionPosition.MID);

        // when & then
        assertThat(player.playsAt(ChampionPosition.TOP)).isFalse();
    }

    @Test
    @DisplayName("포지션을 모르는 선수는 어떤 포지션을 물어도 아니라고 답한다")
    void playsAt_WhenPositionUnknown_ReturnsFalse() {
        // given
        PlayerState player = player(ZED, null);

        // when & then
        assertThat(ChampionPosition.values())
                .noneMatch(player::playsAt);
    }
}
