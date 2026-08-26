package dfgg.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NextItemRecommendationNotFoundException extends RuntimeException {

    public NextItemRecommendationNotFoundException(String champion, String position) {
        super(String.format("해당 챔피언(%s)의 포지션(%s)에 대해 추천할 다음 아이템을 찾지 못했습니다.", champion, position));
    }
}
