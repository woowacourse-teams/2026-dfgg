package dfgg.application.recommend.v3.explanation;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 챔피언 ID를 사람이 읽을 이름으로 바꾼다. 추천 이유에 "다리우스"라고 쓰려면 한글명이 필요하다.
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
        for (Champion champion : championRepository.findAllById(Set.copyOf(championIds))) {
            profiles.put(champion.getChampionId(),
                    new ChampionProfile(champion.getChampionId(), champion.getName()));
        }
        return Map.copyOf(profiles);
    }
}
