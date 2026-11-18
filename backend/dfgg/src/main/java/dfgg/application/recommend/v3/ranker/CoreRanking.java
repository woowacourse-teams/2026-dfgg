package dfgg.application.recommend.v3.ranker;

import java.util.Objects;

/**
 * 순위를 매기는 랭커와, 그 순위가 왜 나왔는지 계산하는 기여도 계산기의 짝.
 *
 * <p>둘은 같은 모델에서 나온다. 따로 들고 다니면 랭커는 새 모델인데 기여도는 지금 모델로 계산하는
 * 실수가 생길 수 있어 한 묶음으로 넘긴다.
 */
public record CoreRanking(CandidateRanker ranker, TreeShapCalculator contributions) {

    public CoreRanking {
        Objects.requireNonNull(ranker, "랭커는 필수입니다.");
        Objects.requireNonNull(contributions, "기여도 계산기는 필수입니다.");
    }
}
