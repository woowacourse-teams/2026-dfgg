package dfgg.infrastructure.image;

/**
 * 견본을 그린 규격. 앱이 로그에 남기고 규격이 바뀌었는지 확인하는 데 쓴다.
 *
 * @param font     폰트 이름
 * @param fontSize 글자 크기(px)
 * @param padding  좌우 여백(px)
 */
public record AugmentTitleStyle(String font, int fontSize, int padding) {
}
