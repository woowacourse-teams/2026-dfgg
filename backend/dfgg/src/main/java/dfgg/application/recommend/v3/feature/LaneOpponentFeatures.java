package dfgg.application.recommend.v3.feature;

/**
 * 맞라인 — 상대 팀에서 나와 포지션이 같은 한 명과 견준 값. 다른 묶음은 상대 5명을 뭉뚱그려 보는데,
 * 한 명과 오래 맞붙는 포지션에서는 그 한 명이 판단의 중심이다.
 *
 * <p>{@code levelLead}와 {@code coreGoldLead}는 "나 − 맞라인 상대"다. 앞섰으면 양수, 뒤처졌으면 음수다.
 * 맞라인 상대를 한 명으로 정할 수 없으면 세 값을 모두 비운다.
 */
public record LaneOpponentFeatures(
        double levelLead,
        double opponentKills,
        double coreGoldLead
) {

    public static LaneOpponentFeatures unknown() {
        return new LaneOpponentFeatures(Double.NaN, Double.NaN, Double.NaN);
    }
}
