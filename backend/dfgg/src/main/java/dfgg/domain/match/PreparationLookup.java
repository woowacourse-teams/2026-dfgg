package dfgg.domain.match;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Train에서 고정한 패치별 조회표. 현재 구매나 미래 이벤트를 입력받지 않는다. */
public final class PreparationLookup {
    private final String patch;
    private final String sha256;
    private final Map<Key, List<Pattern>> entries;

    public PreparationLookup(String patch, String sha256, Map<Key, List<Pattern>> entries) {
        if (patch == null || !patch.matches("\\d+\\.\\d+") || sha256 == null || !sha256.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Preparation Lookup 패치와 SHA-256이 필요합니다.");
        }
        this.patch = patch;
        this.sha256 = sha256;
        var copy = new HashMap<Key, List<Pattern>>();
        entries.forEach((key, patterns) -> {
            if (patterns.isEmpty() || patterns.size() > 3 || patterns.stream().map(Pattern::itemId).distinct().count() != patterns.size()) {
                throw new IllegalArgumentException("Preparation 후보는 키별 중복 없이 1~3개여야 합니다.");
            }
            copy.put(key, List.copyOf(patterns));
        });
        this.entries = Map.copyOf(copy);
    }

    private PreparationLookup() { patch = null; sha256 = null; entries = Map.of(); }
    public static PreparationLookup disabled() { return new PreparationLookup(); }
    public boolean enabled() { return patch != null; }
    public String sha256() { return sha256; }
    public void validatePatch(String requestedPatch) {
        if (enabled() && !patch.equals(requestedPatch)) throw new IllegalArgumentException("Preparation Lookup 패치 불일치");
    }
    public List<Pattern> find(Integer championId, String role, int targetItemId) {
        if (!enabled()) return List.of();
        if (championId == null) throw new IllegalArgumentException("Preparation 조회에는 Champion ID가 필요합니다.");
        return entries.getOrDefault(new Key(championId, role, targetItemId), List.of());
    }
    public record Key(int championId, String role, int targetItemId) {
        public Key {
            if (championId <= 0 || targetItemId <= 0 || !List.of("TOP", "JUNGLE", "MID", "BOTTOM", "SUPPORT").contains(role)) {
                throw new IllegalArgumentException("Preparation Lookup 키가 유효하지 않습니다.");
            }
        }
    }
    public record Pattern(int itemId, int count, double futureRecipeLinkRate, int eligibleQueryCount, int futureRecipeLinkedCount) {
        public double observationRate() { return (double) count / eligibleQueryCount; }
        public List<Double> rankingFeatures() {
            return List.of(1.0, observationRate(), Math.log1p(count), futureRecipeLinkRate);
        }
        public Pattern {
            if (eligibleQueryCount < count || futureRecipeLinkedCount < 0 || futureRecipeLinkedCount > count
                    || Math.abs(futureRecipeLinkRate - (double) futureRecipeLinkedCount / count) > 1e-12
                    || itemId <= 0 || count < 50 || !Double.isFinite(futureRecipeLinkRate) || futureRecipeLinkRate < .70 || futureRecipeLinkRate > 1) {
                throw new IllegalArgumentException("Preparation 패턴 승격 조건 불충족");
            }
        }
    }
}
