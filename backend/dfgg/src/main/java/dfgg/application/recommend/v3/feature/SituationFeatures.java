package dfgg.application.recommend.v3.feature;

/**
 * 게임 상황을 숫자로 바꾼 값. 묶음별로 나눠 담는다. 모르는 값은 NaN이다 — 지어낸 값을 넣으면 모델이 실제 관측으로 읽는다.
 */
public record SituationFeatures(
        EnemyItemFeatures enemyItems,
        EnemyKillFeatures enemyKills,
        LaneOpponentFeatures laneOpponent,
        MyStateFeatures myState,
        TeamStateFeatures teamState
) {

    public static SituationFeatures unknown() {
        return new SituationFeatures(
                EnemyItemFeatures.unknown(),
                EnemyKillFeatures.unknown(),
                LaneOpponentFeatures.unknown(),
                MyStateFeatures.unknown(),
                TeamStateFeatures.unknown()
        );
    }
}
