package dfgg.application.recommend.v3.generator.transition;

import java.util.Set;

/**
 * Build 전이 결과를 바꾸는 데이터 조건. 둘 중 하나라도 바뀌면 캐시한 결과를 다시 읽어야 한다.
 */
record BuildDataVersion(Set<String> recentPatches, Set<Long> coreItemIds) {

    BuildDataVersion {
        recentPatches = Set.copyOf(recentPatches);
        coreItemIds = Set.copyOf(coreItemIds);
    }
}
