package dfgg.domain.recommendation;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionTag;
import java.util.List;

/**
 * 하나의 챔피언 태그에 해당하는 빌드 방향 분류와 조합 적합도 계산 정책이다.
 */
public interface ChampionBuildPolicy {

    ChampionTag supportedTag();

    List<BuildCandidate> evaluate(
            List<CoreBuildCluster> clusters,
            List<Champion> enemies
    );
}
