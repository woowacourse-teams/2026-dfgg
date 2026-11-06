package dfgg.domain.rune;

import java.util.Comparator;

/**
 * 룬 페이지의 방향. 핵심 룬과 보조 계열이 같으면 세부 룬·파편이 달라도 같은 운영 방식으로 본다.
 * 추천은 페이지 전체가 아니라 이 단위로 판 수를 센다. 페이지 전체로 세면 조합이 너무 잘게 쪼개진다.
 */
public record RunePath(int keystoneId, int subStyleId) {

    /** 판 수가 같을 때 매번 같은 순서로 내기 위한 기준 */
    static final Comparator<RunePath> BY_IDS = Comparator.comparingInt(RunePath::keystoneId)
            .thenComparingInt(RunePath::subStyleId);
}
