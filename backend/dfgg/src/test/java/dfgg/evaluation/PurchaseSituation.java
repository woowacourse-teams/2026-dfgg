package dfgg.evaluation;

/**
 * Core 하나를 산 순간의 상황. 승률 지표(V)의 행동 모델이 상황을 통제하는 데 쓰는 메타 값이고 feature가 아니다.
 * 알 수 없는 값은 {@code null}이다(0으로 바꾸지 않는다).
 *
 * @param purchaseMinute 구매 시각(분)
 * @param teamGoldDiff   구매 시각 프레임의 팀 {@code totalGold} 합 차이(내 팀 − 상대 팀)
 * @param ownTotalGold   구매 시각 프레임의 본인 {@code totalGold}
 */
public record PurchaseSituation(Double purchaseMinute, Integer teamGoldDiff, Integer ownTotalGold) {

    public static PurchaseSituation unknown() {
        return new PurchaseSituation(null, null, null);
    }
}
