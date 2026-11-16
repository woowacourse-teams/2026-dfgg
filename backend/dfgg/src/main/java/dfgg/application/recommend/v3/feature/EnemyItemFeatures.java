package dfgg.application.recommend.v3.feature;

/**
 * 상대의 아이템 — 상대가 무엇을 쌓고 있는가. 비중은 개수가 아니라 가격으로 센다.
 *
 * <p>{@code coreGoldSum}은 세 비중의 분모다. 모델이 분모가 작은 경우를 구분하게 함께 낸다.
 * 한 아이템이 태그를 둘 가지면 두 비중에 모두 들어가서 비중의 합이 1을 넘을 수 있다.
 */
public record EnemyItemFeatures(
        double coreGoldSum,
        double spellDamageShare,
        double armorShare,
        double spellBlockShare
) {

    public static EnemyItemFeatures unknown() {
        return new EnemyItemFeatures(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }
}
