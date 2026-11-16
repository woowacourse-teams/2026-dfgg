package dfgg.application.recommend.v3.feature;

/**
 * 팀 상태 — 판 전체가 유리한지 불리한지. 네 값 모두 "우리 팀 − 상대"이고, 우리 팀은 나와 아군이다.
 *
 * <p>아이템을 Core만 보므로 Core를 완성하지 못한 선수의 수입은 킬과 어시스트로만 짐작할 수 있다.
 * 킬·어시스트·Core 가격은 합의 차이, 레벨은 평균의 차이다.
 */
public record TeamStateFeatures(
        double killLead,
        double assistLead,
        double levelLead,
        double coreGoldLead
) {

    public static TeamStateFeatures unknown() {
        return new TeamStateFeatures(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }
}
