package dfgg.application.match;

import java.util.List;

/**
 * 재정규화 한 배치의 결과.
 *
 * @param nextCursor 다음 호출에 넘길 커서. 실패한 매치가 있어도 진행한다 — 같은 지점에서
 *                   무한히 멈추는 것보다 실패 목록을 남기고 넘어가는 편이 낫다.
 * @param hasMore    배치가 limit만큼 찼는지. 남았으면 같은 명령을 커서만 바꿔 반복한다.
 * @param failures   실패한 매치와 이유. 무엇이 왜 실패했는지 봐야 다음 판단을 할 수 있다.
 */
public record RenormalizationResult(
        int processed,
        int succeeded,
        int failed,
        String nextCursor,
        boolean hasMore,
        List<String> failures
) {

    public RenormalizationResult {
        failures = List.copyOf(failures);
    }
}
