package dfgg.application.recommend.v3.generator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@code P(item | 내 챔피언, 적 챔피언) / P(item | 내 챔피언)} — 이번 작업의 핵심 계산.
 * <p>
 * 분모가 내 챔피언 자신의 구매율이라는 점이 기존 구조와 갈리는 지점이다.
 * 예전 카운터 학습은 "적 X가 있을 때 우리 팀 누군가가 산 아이템"을 셌기 때문에,
 * 아리가 산 리안드리가 야스오의 추천 근거가 될 수 있었다.
 * 여기서는 야스오가 리안드리를 거의 안 산다는 사실이 분모에 그대로 들어가고,
 * 그 base rate가 별도 feature로도 남는다.
 * <p>
 * 양쪽 확률에 동일한 라플라스 스무딩을 건다.
 * 두 가지를 동시에 해결한다.
 * base rate가 0이어도 분모가 0이 되지 않아 lift가 발산하지 않고,
 * 표본이 얇을수록 두 확률이 모두 사전분포 쪽으로 끌려가 lift가 1(근거 없음)로 수축한다.
 * 두세 판의 우연이 강한 counter 신호로 둔갑하는 걸 막는 장치다.
 */
@Component
public class CounterLiftCalculator {

    /** 스무딩 강도. 클수록 표본이 얇을 때 lift가 1로 더 강하게 끌려간다. */
    private final double alpha;

    /** 라플라스 스무딩의 사전분포 크기(아이템 종류 수). */
    private final int vocabularySize;

    public CounterLiftCalculator(
            @Value("${recommendation.counter.lift-smoothing-alpha}") double alpha,
            @Value("${recommendation.counter.item-vocabulary-size}") int vocabularySize
    ) {
        this.alpha = alpha;
        this.vocabularySize = vocabularySize;
    }

    /**
     * @param coCount        이 적을 만난 판 중 이 아이템을 산 판 수
     * @param pairGameCount  이 적을 만난 판 수(아이템 무관)
     * @param baseCount      이 챔피언이 이 아이템을 산 판 수(적 무관)
     * @param baseGameCount  이 챔피언이 치른 판 수
     */
    public CounterLift calculate(int coCount, int pairGameCount, int baseCount, int baseGameCount) {
        if (pairGameCount <= 0 || baseGameCount <= 0) {
            return CounterLift.NEUTRAL;
        }
        double baseRate = (double) baseCount / baseGameCount;
        double flooredBaseRate = Math.max(baseRate, floor(baseGameCount));
        double smoothedPairRate = (coCount + alpha * flooredBaseRate) / (pairGameCount + alpha);

        return new CounterLift(
                smoothedPairRate / flooredBaseRate,
                (double) coCount / pairGameCount,
                baseRate
        );
    }

    /**
     * base rate가 정확히 0인 아이템의 하한. 이게 없으면 0으로 나누게 된다.
     * 관측 판수가 많을수록 하한이 낮아진다 — 1000판을 치르고도 한 번도 안 샀다는 사실이
     * 10판 치르고 안 산 것보다 강한 증거이므로, lift도 그만큼 크게 잡히는 게 맞다.
     */
    private double floor(int baseGameCount) {
        return alpha / (baseGameCount + alpha * vocabularySize);
    }
}
