package dfgg.domain.score;

import static dfgg.domain.score.ScoreFeature.CHAMPION_DAMAGE;
import static dfgg.domain.score.ScoreFeature.DAMAGE_TAKEN_PER_LIFE;
import static dfgg.domain.score.ScoreFeature.EPIC_MONSTER_DAMAGE;
import static dfgg.domain.score.ScoreFeature.GOLD;
import static dfgg.domain.score.ScoreFeature.TURRET_DAMAGE;
import static dfgg.domain.score.ScoreFeature.VISION_SCORE;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;

/**
 * 협곡 포지션별 채점 상수.
 * <p>
 * 기하 평균으로 잰다.
 * 계수[pos][f] = exp( 평균over(pos 참가자)( ln( (값 + 1) / (그 경기 10명 평균 + 1) ) ) )
 */
public enum PositionScoreProfile {
    TOP(List.of(
            new FeatureRule(CHAMPION_DAMAGE, 1.07, .393),
            new FeatureRule(GOLD, 0.97, .236),
            new FeatureRule(DAMAGE_TAKEN_PER_LIFE, 1.07, .157),
            new FeatureRule(VISION_SCORE, 0.63, .079),
            new FeatureRule(TURRET_DAMAGE, 1.07, .135))),
    JUNGLE(List.of(
            new FeatureRule(CHAMPION_DAMAGE, 0.87, .267),
            new FeatureRule(GOLD, 1.06, .200),
            new FeatureRule(DAMAGE_TAKEN_PER_LIFE, 1.16, .133),
            new FeatureRule(VISION_SCORE, 0.87, .133),
            new FeatureRule(EPIC_MONSTER_DAMAGE, 2.09, .267))),
    MID(List.of(
            new FeatureRule(CHAMPION_DAMAGE, 1.09, .476),
            new FeatureRule(GOLD, 0.99, .238),
            new FeatureRule(DAMAGE_TAKEN_PER_LIFE, 0.88, .079),
            new FeatureRule(VISION_SCORE, 0.63, .079),
            new FeatureRule(TURRET_DAMAGE, 0.82, .128))),
    BOTTOM(List.of(
            new FeatureRule(CHAMPION_DAMAGE, 1.11, .492),
            new FeatureRule(GOLD, 1.15, .328),
            new FeatureRule(DAMAGE_TAKEN_PER_LIFE, 0.74, 0),
            new FeatureRule(VISION_SCORE, 0.56, .082),
            new FeatureRule(TURRET_DAMAGE, 0.91, .098))),
    SUPPORT(List.of(
            new FeatureRule(CHAMPION_DAMAGE, 0.54, .200),
            new FeatureRule(GOLD, 0.75, .067),
            new FeatureRule(DAMAGE_TAKEN_PER_LIFE, 0.72, .200),
            new FeatureRule(VISION_SCORE, 2.11, .533))),
    ;

    private final List<FeatureRule> rules;

    PositionScoreProfile(List<FeatureRule> rules) {
        this.rules = rules;
    }

    public static PositionScoreProfile of(ChampionPosition position) {
        return valueOf(position.name());
    }

    /**
     * 개인 지표 A(0~1). 항목마다 "10명 평균 × 포지션 계수"를 기대값으로 삼아 견주고 가중합한다.
     * 그 포지션이 평소에 내는 만큼 했으면 0.5다.
     */
    public double individualPerformance(ScoringParticipant participant, ScoringParticipants match) {
        return rules.stream()
                .mapToDouble(rule -> rule.weightedScore(participant, match))
                .sum();
    }

    double weightSum() {
        return rules.stream()
                .mapToDouble(FeatureRule::weight)
                .sum();
    }

    /**
     * 항목 하나의 기준. 계수는 "이 포지션이 10명 평균의 몇 배를 내는가"다.
     */
    private record FeatureRule(ScoreFeature feature, double coefficient, double weight) {

        double weightedScore(ScoringParticipant participant, ScoringParticipants match) {
            double expected = match.averageOf(feature) * coefficient;
            return weight * ScoreCurve.compare(participant.statOf(feature), expected);
        }
    }
}
