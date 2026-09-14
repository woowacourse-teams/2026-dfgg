package dfgg.application.recommend.v3.explanation;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 챔피언 ID를 사람이 읽을 이름과 태그로 바꾼다.
 * <p>
 * 둘 다 DB에 있지만 그냥 읽으면 두 가지가 걸린다.
 * <ul>
 *   <li>태그가 {@code @ElementCollection}이라 지연 로딩이다 — 트랜잭션 밖에서 읽으면 터진다.
 *       그래서 fetch join 메서드를 쓴다.</li>
 *   <li>태그가 {@code List}로 매핑돼 있어 DB에 중복 행이 있으면 그대로 올라올 수 있다.
 *       조회 쿼리의 {@code SELECT DISTINCT}가 SQL로 넘어가 같은 (챔피언, 태그) 행을 합쳐 주지만,
 *       쿼리에 기대지 않고 여기서 집합으로 한 번 더 접는다.</li>
 * </ul>
 * <p>
 * 모르는 ID는 결과에서 빠진다. 이름을 지어내지 않고, 부재는 호출자가 판단한다.
 */
public class ChampionDirectory {

    private final ChampionRepository championRepository;

    public ChampionDirectory(ChampionRepository championRepository) {
        this.championRepository = championRepository;
    }

    /** 여러 ID를 한 번에 해석한다. 챔피언마다 조회하면 요청당 9번이 된다. */
    public Map<Long, ChampionProfile> resolve(Collection<Long> championIds) {
        if (championIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ChampionProfile> profiles = new HashMap<>();
        for (Champion champion : championRepository.findAllWithTagsByChampionIdIn(Set.copyOf(championIds))) {
            profiles.put(champion.getChampionId(), toProfile(champion));
        }
        return Map.copyOf(profiles);
    }

    private ChampionProfile toProfile(Champion champion) {
        Set<ChampionTag> tags = champion.getChampionTags().isEmpty()
                ? Set.of()
                : EnumSet.copyOf(champion.getChampionTags());
        return new ChampionProfile(champion.getChampionId(), champion.getName(), tags);
    }
}
