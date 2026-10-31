package dfgg.application.recommend.v3;

import java.util.Map;

/**
 * 한 generator가 특정 아이템에 대해 남긴 근거. 이 score와 rank는 그대로 LTR feature가 된다.
 * <p>
 * 이 값이 존재한다는 것 자체가 "그 generator가 이 아이템을 후보로 냈다"는 뜻이다.
 * 발견하지 못한 경우는 score 0.0이 아니라 {@link ItemCandidate}에서 아예 부재로 표현한다 —
 * "0점으로 평가했다"와 "평가 대상에 없었다"는 다른 정보다.
 * <p>
 * {@code scoreByChampionId} — 구매 lift. "이 상대와 함께일 때 더 산다". 후보를 발견하는 데 쓰고 LTR feature로도 나간다.
 * {@code winLiftByChampionId} — 승률 lift. "이 상대와 함께 사면 더 이긴다". 추천 이유로만 쓴다.
 * <p>
 * 구매 lift로 이유를 대면 드래프트 상관을 시너지로 오독한다.
 * 승률 lift는 "그래서 이겼는가"를 물음으로써 그런 상관이 걸러진다.
 */
public record SourceEvidence(
        double score, int rank, int backoffLevel,
        Map<Long, Double> scoreByChampionId, Map<Long, Double> winLiftByChampionId) {

    public SourceEvidence {
        scoreByChampionId = Map.copyOf(scoreByChampionId);
        winLiftByChampionId = Map.copyOf(winLiftByChampionId);
    }

    public SourceEvidence(double score, int rank, int backoffLevel,
                          Map<Long, Double> scoreByChampionId) {
        this(score, rank, backoffLevel, scoreByChampionId, Map.of());
    }

    public SourceEvidence(double score, int rank, int backoffLevel) {
        this(score, rank, backoffLevel, Map.of(), Map.of());
    }

    /** 백오프하지 않은(0단계) 근거. */
    public SourceEvidence(double score, int rank) {
        this(score, rank, 0);
    }
}
