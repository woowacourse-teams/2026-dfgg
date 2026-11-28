package dfgg.domain.match;

import java.util.Set;

/** 요청한 게임의 큐. 사용자 설정 게임(0)과 훈련모드(3140)는 라인과 5대5 인원이 정해지지 않는다. */
public record GameQueue(Integer id) {

    private static final int CUSTOM_GAME = 0;
    private static final int PRACTICE_TOOL = 3140;
    private static final Set<Integer> FREE_ROSTER_QUEUE_IDS = Set.of(CUSTOM_GAME, PRACTICE_TOOL);

    /** 큐를 모르면 일반 게임으로 본다. */
    public boolean hasFreeRoster() {
        if (id == null) {
            return false;
        }
        return FREE_ROSTER_QUEUE_IDS.contains(id);
    }
}
