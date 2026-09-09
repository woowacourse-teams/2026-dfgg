package dfgg.application.recommend.v3.explanation;

import java.util.List;

/**
 * 이 아이템에 대해 근거로 쓸 만한 묶음들.
 *
 * @param qualified 점수를 올린 묶음 중 노이즈 문턱을 넘은 것. 큰 순서다
 */
public record SelectedReasons(List<GroupWeight> qualified) {

    public SelectedReasons {
        qualified = List.copyOf(qualified);
    }
}
