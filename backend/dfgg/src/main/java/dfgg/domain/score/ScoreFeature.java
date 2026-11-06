package dfgg.domain.score;

/**
 * 개인 지표 A를 이루는 항목. 값은 참가자가 자기 원시 값으로 계산한다({@link ScoringParticipant#statOf}).
 */
public enum ScoreFeature {
    CHAMPION_DAMAGE,
    GOLD,
    /** 받은 피해 ÷ (데스 + 1). 많이 죽으면서 맞은 피해를 탱킹으로 치지 않는다. */
    DAMAGE_TAKEN_PER_LIFE,
    VISION_SCORE,
    TURRET_DAMAGE,
    /** 오브젝트 피해 − 포탑 피해. 드래곤·바론·전령 같은 에픽 몬스터에 준 피해다. */
    EPIC_MONSTER_DAMAGE,
}
