package dfgg.domain.score;

import dfgg.common.exception.InvalidDfScoreRequestException;
import dfgg.domain.champion.ChampionPosition;

/**
 * 채점할 참가자 한 명의 원시 값. 포지션은 없을 수 있다 — 칼바람처럼 포지션이 없는 모드가 있다.
 */
public class ScoringParticipant {

    private final int teamId;
    private final ChampionPosition position;
    private final boolean win;
    private final int kills;
    private final int deaths;
    private final int assists;
    private final int championDamage;
    private final int damageTaken;
    private final int gold;
    private final int visionScore;
    private final int turretDamage;
    private final int objectiveDamage;

    public ScoringParticipant(int teamId, ChampionPosition position, boolean win,
                              int kills, int deaths, int assists,
                              int championDamage, int damageTaken, int gold, int visionScore,
                              int turretDamage, int objectiveDamage) {
        validateObjectiveDamage(turretDamage, objectiveDamage);
        this.teamId = teamId;
        this.position = position;
        this.win = win;
        this.kills = kills;
        this.deaths = deaths;
        this.assists = assists;
        this.championDamage = championDamage;
        this.damageTaken = damageTaken;
        this.gold = gold;
        this.visionScore = visionScore;
        this.turretDamage = turretDamage;
        this.objectiveDamage = objectiveDamage;
    }

    /** Riot의 오브젝트 피해는 포탑 피해를 포함한다. 작으면 두 값 중 하나가 잘못 들어왔다. */
    private void validateObjectiveDamage(int turretDamage, int objectiveDamage) {
        if (objectiveDamage < turretDamage) {
            throw new InvalidDfScoreRequestException(
                    "오브젝트 피해(%d)가 포탑 피해(%d)보다 작을 수 없습니다".formatted(objectiveDamage, turretDamage));
        }
    }

    public int teamId() {
        return teamId;
    }

    public boolean hasPosition() {
        return position != null;
    }

    public ChampionPosition position() {
        return position;
    }

    public boolean isWinner() {
        return win;
    }

    public int deaths() {
        return deaths;
    }

    public int kills() {
        return kills;
    }

    /** 킬 관여 수(킬 + 어시스트). */
    public int killInvolvement() {
        return kills + assists;
    }

    /** (킬 + 어시스트) ÷ 데스. 데스가 0이면 1로 나눈다. */
    public double kda() {
        return (double) killInvolvement() / Math.max(deaths, 1);
    }

    /** 허용 데스를 넘은 만큼. 넘지 않았으면 0이다. */
    public double excessDeaths(double allowedDeaths) {
        return Math.max(0, deaths - allowedDeaths);
    }

    /** 개인 지표 항목의 값. */
    public double statOf(ScoreFeature feature) {
        return switch (feature) {
            case CHAMPION_DAMAGE -> championDamage;
            case GOLD -> gold;
            case DAMAGE_TAKEN_PER_LIFE -> (double) damageTaken / (deaths + 1);
            case VISION_SCORE -> visionScore;
            case TURRET_DAMAGE -> turretDamage;
            case EPIC_MONSTER_DAMAGE -> objectiveDamage - turretDamage;
        };
    }
}
