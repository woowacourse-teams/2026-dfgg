package dfgg.application.propick;

import dfgg.domain.catalog.EnglishNameIndex;

/**
 * Leaguepedia 영어 이름을 id로 바꿀 카탈로그 색인. 수집 한 번에 한 번 만든다.
 */
public record ProPickCatalog(
        EnglishNameIndex champions,
        EnglishNameIndex spells,
        EnglishNameIndex items,
        EnglishNameIndex runes
) {
}
