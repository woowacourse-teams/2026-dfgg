package dfgg.domain.propick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 오늘의 추천 픽 뽑기. 후보를 섞은 뒤 앞에서부터 정해 둔 개수까지 고른다. 같은 챔피언의 픽이 여럿 나올 수 있다.
 */
public class ProPickDraw {

    private final int size;

    public ProPickDraw(int size) {
        this.size = size;
    }

    public List<ProPick> draw(List<ProPick> candidates, RandomGenerator random) {
        List<ProPick> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);
        return shuffled.stream()
                .limit(size)
                .toList();
    }
}
