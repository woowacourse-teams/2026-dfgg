package dfgg.application.recommend.v3.feature;

/**
 * 내 상태 — 내가 앞섰는지 뒤처졌는지.
 *
 * <p>{@code levelLead}는 "내 레벨 − 상대 평균 레벨"이다. 어시스트를 킬과 따로 낸다 — 서포터·탱커·정글러는
 * 킬이 적은 것이 정상이라, 킬만 보면 상태가 실제보다 나쁘게 읽힌다.
 */
public record MyStateFeatures(
        double levelLead,
        double kills,
        double deaths,
        double assists
) {

    public static MyStateFeatures unknown() {
        return new MyStateFeatures(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }
}
