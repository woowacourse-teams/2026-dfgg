package dfgg.domain.propick;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * 오늘의 추천 픽 뽑기. 밴된 챔피언을 빼고, 후보를 섞은 뒤 챔피언마다 처음 나온 픽 하나씩 정해 둔 개수까지 고른다.
 */
public class ProPickDraw {

    private final int size;

    public ProPickDraw(int size) {
        this.size = size;
    }

    /** 밴은 양 팀이 같은 챔피언을 밴할 수 있어 중복을 허용한다. */
    public List<ProPick> draw(List<ProPick> candidates, Collection<Integer> bans, RandomGenerator random) {
        Set<Long> banned = bans.stream().map(Integer::longValue).collect(Collectors.toSet());
        List<ProPick> shuffled = new ArrayList<>(candidates.stream()
                .filter(pick -> !banned.contains(pick.getChampionId()))
                .toList());
        Collections.shuffle(shuffled, random);
        Map<Long, ProPick> onePerChampion = new LinkedHashMap<>();
        shuffled.forEach(pick -> onePerChampion.putIfAbsent(pick.getChampionId(), pick));
        return onePerChampion.values().stream()
                .limit(size)
                .toList();
    }
}
