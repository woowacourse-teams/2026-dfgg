package dfgg.application.recommend.v3.explanation;

import java.util.List;
import java.util.Optional;

/**
 * 이 아이템에 대해 설명하기로 한 것들.
 *
 * @param highlights 점수를 올린 묶음 중 말할 것 (0~2개). 큰 순서다
 * @param caveat     붙일 만한 단서가 있을 때만. 대부분의 추천에는 없다
 */
public record SelectedReasons(List<GroupWeight> highlights, Optional<GroupWeight> caveat) {

    public SelectedReasons {
        highlights = List.copyOf(highlights);
    }
}
