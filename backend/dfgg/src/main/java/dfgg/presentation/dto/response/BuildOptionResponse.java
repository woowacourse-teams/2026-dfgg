package dfgg.presentation.dto.response;

import dfgg.presentation.dto.ItemDto;
import java.util.List;

/**
 * 아이템 추천 v2에서 하나의 빌드 선택지를 표현한다.
 */
public record BuildOptionResponse(
        String championTag,
        String direction,
        List<ItemDto> build
) {
}
