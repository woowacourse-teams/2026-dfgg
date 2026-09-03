package dfgg.application.recommend.v3;

import java.util.Map;

/**
 * 한 generator가 특정 아이템에 대해 남긴 근거. 이 score와 rank는 그대로 LTR feature가 된다.
 * <p>
 * 이 값이 존재한다는 것 자체가 "그 generator가 이 아이템을 후보로 냈다"는 뜻이다.
 * 발견하지 못한 경우는 score 0.0이 아니라 {@link ItemCandidate}에서 아예 부재로 표현한다 —
 * "0점으로 평가했다"와 "평가 대상에 없었다"는 다른 정보다.
 * <p>
 * {@code scoreByChampionId}는 어느 챔피언 때문에 올라온 후보인지다.
 * Ally-Synergy와 Counter만 채우고 나머지는 비어 있다.
 * 랭킹은 이 값을 쓰지 않지만, 추천 이유를 만들 때 "다리우스 때문에"를 말하려면 필요하다.
 */
public record SourceEvidence(
        double score, int rank, int backoffLevel, Map<Long, Double> scoreByChampionId) {

    public SourceEvidence {
        scoreByChampionId = Map.copyOf(scoreByChampionId);
    }

    public SourceEvidence(double score, int rank, int backoffLevel) {
        this(score, rank, backoffLevel, Map.of());
    }

    /** 백오프하지 않은(0단계) 근거. */
    public SourceEvidence(double score, int rank) {
        this(score, rank, 0);
    }
}
