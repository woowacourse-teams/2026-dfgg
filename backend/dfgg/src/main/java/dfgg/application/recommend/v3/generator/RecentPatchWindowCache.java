package dfgg.application.recommend.v3.generator;

import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.RecentPatchWindow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 서빙이 쓰는 최근 패치 창. 처음 쓰일 때 한 번만 계산하고 이후엔 메모리 값을 준다.
 */
@Component
public class RecentPatchWindowCache {

    private final NormalizedMatchParticipantRepository participantRepository;
    // 집계(ItemStatsAggregationService)와 같은 값이어야 한다. 다르면 _recent 통계와 서빙의 "최근"이 어긋난다.
    private final int windowSize;

    private volatile RecentPatchWindow loaded;

    public RecentPatchWindowCache(
            NormalizedMatchParticipantRepository participantRepository,
            @Value("${recommendation.recent-patch-window-size}") int windowSize
    ) {
        this.participantRepository = participantRepository;
        this.windowSize = windowSize;
    }

    public RecentPatchWindow current() {
        RecentPatchWindow window = loaded;
        if (window != null) {
            return window;
        }
        return load();
    }

    // 첫 요청이 몰려도 계산은 한 번이다. 기다린 요청은 먼저 계산된 값을 받는다.
    private synchronized RecentPatchWindow load() {
        if (loaded != null) {
            return loaded;
        }
        RecentPatchWindow window = RecentPatchWindow.of(participantRepository.findDistinctPatches(), windowSize);
        if (window.isEmpty()) {
            return window;
        }
        loaded = window;
        return window;
    }
}
