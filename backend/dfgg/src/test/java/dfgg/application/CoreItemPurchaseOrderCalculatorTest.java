package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.match.CoreItemPurchaseOrderCalculator;
import dfgg.infrastructure.external.dto.MatchTimelineResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CoreItemPurchaseOrderCalculatorTest {

    private final CoreItemPurchaseOrderCalculator calculator = new CoreItemPurchaseOrderCalculator();

    @Test
    void 코어_아이템의_구매_순서를_계산한다() {
        MatchTimelineResponse timeline = timeline(
                new MatchTimelineResponse.Event(500L, "ITEM_PURCHASED", 1, 1036, null, null),
                new MatchTimelineResponse.Event(100L, "ITEM_PURCHASED", 1, 3071, null, null),
                new MatchTimelineResponse.Event(200L, "ITEM_PURCHASED", 1, 6610, null, null),
                new MatchTimelineResponse.Event(300L, "ITEM_SOLD", 1, 6610, null, null),
                new MatchTimelineResponse.Event(400L, "ITEM_PURCHASED", 1, 3053, null, null)
        );

        Optional<List<Integer>> result = calculator.calculate(
                timeline,
                1,
                List.of(3071, 3053),
                List.of(3071, 6610, 3053)
        );

        assertThat(result).contains(List.of(3071, 3053));
    }

    @Test
    void 아이템_되돌리기는_구매_순서에서_제거한다() {
        MatchTimelineResponse timeline = timeline(
                new MatchTimelineResponse.Event(100L, "ITEM_PURCHASED", 1, 3071, null, null),
                new MatchTimelineResponse.Event(200L, "ITEM_UNDO", 1, null, 3071, 0),
                new MatchTimelineResponse.Event(300L, "ITEM_PURCHASED", 1, 3053, null, null),
                new MatchTimelineResponse.Event(400L, "ITEM_PURCHASED", 1, 3071, null, null)
        );

        Optional<List<Integer>> result = calculator.calculate(
                timeline,
                1,
                List.of(3071, 3053),
                List.of(3071, 3053)
        );

        assertThat(result).contains(List.of(3053, 3071));
    }

    @Test
    void Timeline에_최종_코어_아이템_구매_기록이_없으면_계산하지_않는다() {
        MatchTimelineResponse timeline = timeline(
                new MatchTimelineResponse.Event(100L, "ITEM_PURCHASED", 1, 3071, null, null)
        );

        Optional<List<Integer>> result = calculator.calculate(
                timeline,
                1,
                List.of(3071, 3053),
                List.of(3071, 3053)
        );

        assertThat(result).isEmpty();
    }

    private MatchTimelineResponse timeline(MatchTimelineResponse.Event... events) {
        return new MatchTimelineResponse(
                new MatchTimelineResponse.Metadata(List.of("puuid-1")),
                new MatchTimelineResponse.Info(
                        60_000L,
                        List.of(new MatchTimelineResponse.Frame(List.of(events)))
                )
        );
    }
}
