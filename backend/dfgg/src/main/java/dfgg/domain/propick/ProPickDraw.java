package dfgg.domain.propick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * 오늘의 추천 픽 뽑기. 후보를 섞은 뒤 챔피언마다 처음 나온 픽 하나씩 정해 둔 개수까지 고른다.
 */
public class ProPickDraw {

    private final int size;

    public ProPickDraw(int size) {
        this.size = size;
    }

    public List<ProPick> draw(List<ProPick> candidates, RandomGenerator random) {
        List<ProPick> available = new ArrayList<>(candidates);
        Collections.shuffle(available, random);
        Map<Long, ProPick> onePerChampion = new LinkedHashMap<>();
        available.forEach(pick -> onePerChampion.putIfAbsent(pick.getChampionId(), pick));
        return onePerChampion.values().stream()
                .limit(size)
                .toList();
    }
}
