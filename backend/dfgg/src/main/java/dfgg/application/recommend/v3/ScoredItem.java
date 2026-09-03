package dfgg.application.recommend.v3;

import java.util.Map;

/**
 * generator가 점수를 매긴 후보 하나.
 * <p>
 * {@code scoreByChampionId}는 어느 챔피언 때문에 이 아이템이 올라왔는지다.
 * Ally-Synergy와 Counter는 상대별 점수를 개별로 계산한 뒤 최댓값 하나로 접어 랭킹에 쓰는데,
 * 접기 전 값을 버리면 "다리우스 때문에 올라왔다"를 말할 수 없게 되고 나중에 같은 통계를 다시 조회해야 한다.
 * 상대 개념이 없는 generator(Build, Self-Synergy)는 비어 있다.
 *
 * @param score 랭킹에 쓰는 점수. 상대별 점수가 있으면 그 최댓값이다
 */
public record ScoredItem(Long itemId, double score, Map<Long, Double> scoreByChampionId) {

    public ScoredItem {
        scoreByChampionId = Map.copyOf(scoreByChampionId);
    }

    /** 상대 개념이 없는 generator용. */
    public ScoredItem(Long itemId, double score) {
        this(itemId, score, Map.of());
    }
}
