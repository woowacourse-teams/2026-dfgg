package dfgg.domain.match;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 집계·학습·서빙이 대상으로 삼는 티어 범위.
 * <p>
 * "무엇이 유효한 티어인가"는 v2와 v3가 함께 아는 사실이라 여기 한 곳에 둔다.
 * 반면 <b>어느 티어를 쓸지는 각 경로가 따로 정한다</b> — 그래서 설정 레코드는 분리하고
 * 이 값 객체만 공유한다. 한 레코드에 몰아넣으면 v3 정책을 바꿀 때마다 v2 생성자가 흔들린다.
 * <p>
 * 모르는 티어는 만들지 못하게 막는다. 오타를 통과시키면 그 티어가 통계에서 통째로 빠진 채
 * 서비스가 뜨고, 지표를 볼 때까지 아무도 모른다.
 */
public record TierScope(List<String> values) {

    private static final Set<String> SUPPORTED_TIERS = Set.of(
            "IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM",
            "EMERALD", "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER"
    );

    public TierScope {
        values = List.copyOf(values);
    }

    /**
     * @param source 예외 메시지에 실을 설정 이름. 어느 설정이 잘못됐는지 바로 보여야 고칠 수 있다
     */
    public static TierScope of(List<String> tiers, String source) {
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException(source + " must not be empty");
        }
        List<String> normalized = tiers.stream()
                .map(TierScope::normalize)
                .distinct()
                .toList();
        List<String> unsupported = normalized.stream()
                .filter(tier -> !SUPPORTED_TIERS.contains(tier))
                .toList();
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException(
                    source + " contains an unsupported tier: " + unsupported);
        }
        return new TierScope(normalized);
    }

    public boolean contains(String tier) {
        return tier != null && values.contains(normalize(tier));
    }

    private static String normalize(String tier) {
        return tier == null ? "" : tier.trim().toUpperCase(Locale.ROOT);
    }
}
