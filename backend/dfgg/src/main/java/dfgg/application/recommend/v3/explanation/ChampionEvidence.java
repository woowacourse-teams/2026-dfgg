package dfgg.application.recommend.v3.explanation;

import java.util.List;
import java.util.Map;

/**
 * generator가 남긴 상대별 점수에서 <b>근거로 지목할 챔피언</b>을 고른다.
 * <p>
 * 다섯 명을 다 늘어놓으면 "누구 때문인가"가 흐려지고,
 * 문턱 없이 뽑으면 상관없는 챔피언까지 이유가 된다.
 * 문턱은 호출자가 정한다. 1.0은 "평소와 같다"이다.
 * <p>
 * base rate로 백오프한 후보는 상대별 점수가 비어 있어 자연히 빈 목록이 된다 — 아군/적이 이유가 아닌데 그렇게 말하면 거짓말이다.
 */
public final class ChampionEvidence {

    private ChampionEvidence() {
    }

    /**
     * @param minimumScore 이 값을 <b>초과</b>해야 근거로 쓴다. 같으면 뺀다
     */
    public static List<Long> topChampionIds(
            Map<Long, Double> scoreByChampionId, double minimumScore, int limit) {
        return scoreByChampionId.entrySet().stream()
                .filter(entry -> entry.getValue() > minimumScore)
                // 점수가 같으면 챔피언 ID로 갈라 매 요청 같은 순서를 낸다.
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }
}
