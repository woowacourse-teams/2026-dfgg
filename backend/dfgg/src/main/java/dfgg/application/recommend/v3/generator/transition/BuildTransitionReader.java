package dfgg.application.recommend.v3.generator.transition;

import java.util.List;

/**
 * "이 구매 순서 다음에 무엇을 샀나" 집계를 읽는다. 결과가 없으면 빈 목록이다.
 */
public interface BuildTransitionReader {

    /** 구매 순서가 정확히 같은 표본에서 다음 한 칸. */
    List<TransitionRow> exactPrefix(BuildTransitionRequest request);

    /** 마지막으로 산 아이템 바로 다음 칸(빌드 안 위치 무관). 산 아이템이 있어야 한다. */
    List<TransitionRow> lastItem(BuildTransitionRequest request);
}
