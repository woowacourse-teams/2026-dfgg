package dfgg.domain.match;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관측된 패치 중 최신 N개. 집계가 어떤 행의 {@code _recent} 카운트를 올릴지 판정하는 기준이다.
 *
 * <p>단일 패치를 통계 키로 쓰면 표본이 무너지고(실측 중앙값 2회), 전체를 뭉뚱그리면 아이템
 * 버프/너프가 묻힌다. 그 사이를 메우는 게 이 윈도다 — 실측상 최근 3패치가 오차를 절반으로
 * 줄이면서 삼중항 지지도의 89%를 유지한다.
 */
public record RecentPatchWindow(Set<String> patches) {

    public RecentPatchWindow {
        patches = Set.copyOf(patches);
    }

    public static RecentPatchWindow of(Collection<String> observedPatches, int windowSize) {
        if (windowSize < 1) {
            throw new IllegalArgumentException("패치 윈도 크기는 1 이상이어야 합니다: " + windowSize);
        }
        Set<String> newest = observedPatches.stream()
                .map(PatchVersion::of)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(windowSize)
                .map(PatchVersion::value)
                .collect(Collectors.toUnmodifiableSet());
        return new RecentPatchWindow(newest);
    }

    public boolean contains(String patch) {
        return patches.contains(PatchVersion.of(patch).value());
    }

    public boolean isEmpty() {
        return patches.isEmpty();
    }
}
