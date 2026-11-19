package dfgg.infrastructure.image;

/**
 * 증강 이름 하나를 그린 견본 그림.
 *
 * @param width  그림 폭(px). 글자 폭 + 좌우 여백
 * @param height 그림 높이(px). 모든 그림이 같다
 * @param png    8비트 흑백 PNG
 */
public record AugmentTitleImage(int width, int height, byte[] png) {
}
