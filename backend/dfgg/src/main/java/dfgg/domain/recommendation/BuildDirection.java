package dfgg.domain.recommendation;

import dfgg.domain.champion.ChampionTag;

public record BuildDirection(
        ChampionTag championTag,
        String code
) {
    public BuildDirection {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("빌드 방향 코드는 비어 있을 수 없습니다.");
        }

        code = code.trim();
    }
}
