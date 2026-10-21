package dfgg.application.rune;

import java.util.List;

/**
 * 패치 하나의 룬 정규화 한 배치 결과.
 *
 * @param savedRunes 이번 배치에서 저장한 참가자 룬 수
 * @param nextCursor 다음 호출에 넘길 커서. 실패한 경기가 있어도 진행한다.
 * @param hasMore    배치가 limit만큼 찼는지. 남았으면 같은 명령을 커서만 바꿔 반복한다.
 */
public record RuneNormalizationBatchResult(int processed, int succeeded, int failed, int savedRunes,
                                           String nextCursor, boolean hasMore, List<String> failures) {

    public RuneNormalizationBatchResult {
        failures = List.copyOf(failures);
    }
}
