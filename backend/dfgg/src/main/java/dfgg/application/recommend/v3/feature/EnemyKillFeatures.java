package dfgg.application.recommend.v3.feature;

/**
 * 상대 중 누가 컸나 — 어떤 종류의 딜이 위협인지, 한 명에게 킬이 몰렸는지.
 *
 * <p>{@code killSum}은 네 비중의 분모다. 역할군이 둘인 챔피언의 킬은 두 비중에 모두 들어가서
 * 역할군 비중의 합이 1을 넘을 수 있다. {@code topKillerShare}는 킬이 가장 많은 상대 한 명의 몫이다.
 */
public record EnemyKillFeatures(
        double killSum,
        double mageShare,
        double assassinShare,
        double marksmanShare,
        double topKillerShare
) {

    public static EnemyKillFeatures unknown() {
        return new EnemyKillFeatures(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }
}
