package dfgg.domain.propick;

import java.util.List;

/**
 * 수집할 주요 리그. Leaguepedia {@code OverviewPage} 접두사 목록이다 (예: {@code LCK/}, {@code 2026 First Stand}).
 * 접두사는 Cargo 조건에 그대로 들어가므로 따옴표·와일드카드(%, _)를 받지 않는다.
 */
public final class ProLeagues {

    private static final String SAFE_PREFIX = "[A-Za-z0-9 ./-]*[A-Za-z0-9/]";

    private final List<String> prefixes;

    public ProLeagues(List<String> prefixes) {
        if (prefixes.isEmpty()) {
            throw new IllegalArgumentException("수집할 리그가 없습니다.");
        }
        prefixes.forEach(ProLeagues::validate);
        this.prefixes = List.copyOf(prefixes);
    }

    private static void validate(String prefix) {
        if (prefix == null || !prefix.matches(SAFE_PREFIX)) {
            throw new IllegalArgumentException("리그 접두사가 올바르지 않습니다: " + prefix);
        }
    }

    public boolean includes(String overviewPage) {
        return prefixes.stream().anyMatch(overviewPage::startsWith);
    }

    public List<String> prefixes() {
        return prefixes;
    }
}
