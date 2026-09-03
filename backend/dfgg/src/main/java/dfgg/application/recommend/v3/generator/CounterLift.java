package dfgg.application.recommend.v3.generator;

/**
 * 적 하나에 대한 counter 근거. 세 값을 따로 들고 있는 것이 핵심이다.
 * <p>
 * {@code lift}만 남기면 "이 챔피언이 원래 안 사는 아이템인데 이 적 상대로만 몇 번 보였다"와 "원래도 자주 사는데 이 적 상대로 더 산다"를 구분할 수 없다.
 * 앞의 경우가 바로 이번 작업이 고치려는 실패 유형이라, {@code baseRate}를 별도 feature로 넘겨 LTR이 둘을 구분하게 한다.
 *
 * @param lift            스무딩된 {@code P(item|내 챔피언, 적) / P(item|내 챔피언)}
 * @param pairProbability 스무딩 전 원 확률 {@code co / pairGames}
 * @param baseRate        스무딩 전 원 base rate {@code purchase / games}
 */
public record CounterLift(double lift, double pairProbability, double baseRate) {

    public static final CounterLift NEUTRAL = new CounterLift(1.0, 0.0, 0.0);
}
