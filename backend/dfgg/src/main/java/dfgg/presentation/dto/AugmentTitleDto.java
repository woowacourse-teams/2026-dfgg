package dfgg.presentation.dto;

import dfgg.infrastructure.image.AugmentTitleImage;
import java.util.Base64;

/**
 * 증강 하나의 제목 견본.
 *
 * @param augmentId 다른 증강 API의 augmentId와 같은 값
 * @param name      그린 이름. 저장된 이름 그대로다
 * @param png       8비트 흑백 PNG의 base64
 */
public record AugmentTitleDto(
        int augmentId,
        String name,
        int width,
        int height,
        String png
) {

    public static AugmentTitleDto of(int augmentId, String name, AugmentTitleImage image) {
        return new AugmentTitleDto(augmentId, name, image.width(), image.height(),
                Base64.getEncoder().encodeToString(image.png()));
    }
}
