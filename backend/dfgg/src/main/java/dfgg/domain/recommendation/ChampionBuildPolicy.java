package dfgg.domain.recommendation;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionTag;
import java.util.List;

/**
 * 하나의 챔피언 태그에 해당하는 빌드 방향 분류와 조합 적합도 계산 정책이다.
 *
 * <p>다중 태그 챔피언의 후보를 서로 비교하므로, 정책은 선택한 방향의 아이템 근거 점수를
 * 최대 가능 점수 기준으로 정규화한 뒤 적 조합 가중치를 적용해야 한다.
 */
public interface ChampionBuildPolicy {

    ChampionTag supportedTag();

    List<BuildDirection> supportedDirections();

    List<BuildCandidate> evaluate(
            List<CoreBuildCluster> clusters,
            List<Champion> enemies
    );
}
