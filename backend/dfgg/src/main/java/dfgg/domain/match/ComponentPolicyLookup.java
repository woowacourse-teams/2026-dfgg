package dfgg.domain.match;

import java.util.List;
import java.util.Map;

/** Train에서 고정한 패치별 Prepared 정책 조회표. 현재 구매나 미래 이벤트를 입력받지 않는다. */
public final class ComponentPolicyLookup {
    private static final List<String> ROLES = List.of("TOP", "JUNGLE", "MID", "BOTTOM", "SUPPORT");

    private final String patch;
    private final String sha256;
    private final Map<RoleKey, Prepared> prepared;

    public ComponentPolicyLookup(String patch, String sha256, Map<RoleKey, Prepared> prepared) {
        if (patch == null || !patch.matches("\\d+\\.\\d+") || sha256 == null || !sha256.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("정책 조회표 패치와 SHA-256이 필요합니다.");
        }
        this.patch = patch;
        this.sha256 = sha256;
        this.prepared = Map.copyOf(prepared);
    }

    private ComponentPolicyLookup() {
        patch = null; sha256 = null; prepared = Map.of();
    }

    public static ComponentPolicyLookup disabled() { return new ComponentPolicyLookup(); }
    public boolean enabled() { return patch != null; }
    public String sha256() { return sha256; }

    public void validatePatch(String requestedPatch) {
        if (enabled() && !patch.equals(requestedPatch)) throw new IllegalArgumentException("정책 조회표 패치 불일치");
    }

    /** 내 챔피언·포지션에 적용되는 Prepared 아이템을 아이템 ID 순서로 반환한다. 보유 여부는 생성기가 판단한다. */
    public List<ComponentPolicyEvidence> evidenceFor(Integer championId, String role) {
        if (!enabled() || role == null || !ROLES.contains(role)) return List.of();
        if (championId == null) throw new IllegalArgumentException("정책 조회에는 Champion ID가 필요합니다.");
        var line = prepared.get(new RoleKey(championId, role));
        if (line == null) return List.of();
        return PreparedComponentPolicy.items().stream().sorted()
                .map(item -> new ComponentPolicyEvidence(item, line.linePurchaseRate(), line.games())).toList();
    }

    public record RoleKey(int championId, String role) {
        public RoleKey {
            if (championId <= 0 || !ROLES.contains(role)) throw new IllegalArgumentException("정책 조회표 키가 유효하지 않습니다.");
        }
    }

    public record Prepared(int games, double linePurchaseRate) {
        public Prepared {
            if (games < PreparedComponentPolicy.MIN_GAMES || !(linePurchaseRate >= PreparedComponentPolicy.MIN_LINE_PURCHASE_RATE)
                    || linePurchaseRate > 1) {
                throw new IllegalArgumentException("Prepared 적용 기준 불충족");
            }
        }
    }
}
