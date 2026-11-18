package dfgg.application.recommend.v3.feature;

import dfgg.domain.item.Item;
import java.util.Map;

/**
 * 후보 하나가 지금 상황에 맞는지를 {@link SituationMatch}의 일곱 칸에 쓴다.
 * <p>
 * 상황 값은 이미 벡터에 있는 질의 단위 칸에서 읽는다. 상황을 다시 계산하지 않으므로 질의 단위 값과 어긋날 일이 없다.
 * DB를 보지 않고, 후보 아이템은 부르는 쪽이 요청당 한 번 읽어 넘긴다.
 * <p>
 * 비어 있는 칸(NaN)이 곧 "모름"이다. 상황 값이 비었거나 후보 아이템을 못 찾으면 칸을 건드리지 않는다.
 * 상황을 알고 후보도 알 때만, 태그가 맞으면 상황 값을, 아니면 0을 쓴다.
 */
public class SituationMatchFeatureExtractor {

    public void extract(long candidateItemId, Map<Long, Item> candidateItems, FeatureVector vector) {
        Item candidate = candidateItems.get(candidateItemId);
        if (candidate == null) {
            return;
        }
        for (SituationMatch match : SituationMatch.values()) {
            double situation = vector.get(match.situation());
            if (Double.isNaN(situation)) {
                continue;
            }
            vector.set(match.feature(), match.matches(candidate) ? situation : 0.0);
        }
    }
}
