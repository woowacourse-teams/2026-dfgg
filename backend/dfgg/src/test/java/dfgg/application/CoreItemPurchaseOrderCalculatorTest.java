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

    // --- 서포터 퀘스트 아이템 ---
    //
    // 실측(RDS, KR_8312933636 참가자 10 브라움)으로 확인한 구조:
    //   final_core_item_ids = 3047,3109,3190,3876,2524  →  5개 중 3876만 획득 이벤트가 없다
    //   서포터 아이템 계열은 파괴 이벤트로만 나타난다:
    //     455376 ITEM_DESTROYED 3865 / 822892 ITEM_DESTROYED 3866 / 832244 ITEM_DESTROYED 3867
    //   3865조차 구매 이벤트가 없다 — 지급 후 퀘스트로 자동 승급하기 때문이다.
    //
    // 표본 50건에서 ITEM_DESTROYED 3867은 50/50으로 항상 존재해 앵커로 쓸 수 있다.

    @Test
    void 서포터_퀘스트_아이템은_구매_이벤트가_없어도_3867_파괴_시점으로_순서에_넣는다() {
        MatchTimelineResponse timeline = timeline(
                new MatchTimelineResponse.Event(744292L, "ITEM_PURCHASED", 10, 3190, null, null),
                new MatchTimelineResponse.Event(832244L, "ITEM_DESTROYED", 10, 3867, null, null),
                new MatchTimelineResponse.Event(966607L, "ITEM_PURCHASED", 10, 3047, null, null)
        );

        Optional<List<Integer>> result = calculator.calculate(
                timeline,
                10,
                List.of(3190, 3876, 3047),
                List.of(3190, 3876, 3047)
        );

        // 3876은 3190 이후, 3047 이전에 획득한 것으로 본다
        assertThat(result).contains(List.of(3190, 3876, 3047));
    }

    @Test
    void 서포터_퀘스트_아이템의_구매_이벤트가_있으면_그것을_그대로_쓴다() {
        MatchTimelineResponse timeline = timeline(
                new MatchTimelineResponse.Event(100L, "ITEM_PURCHASED", 10, 3190, null, null),
                new MatchTimelineResponse.Event(200L, "ITEM_PURCHASED", 10, 3876, null, null),
                new MatchTimelineResponse.Event(900L, "ITEM_DESTROYED", 10, 3867, null, null)
        );

        Optional<List<Integer>> result = calculator.calculate(
                timeline,
                10,
                List.of(3190, 3876),
                List.of(3190, 3876)
        );

        // 앵커(900)가 아니라 실제 구매 시점(200)을 쓴다
        assertThat(result).contains(List.of(3190, 3876));
    }

    @Test
    void 퀘스트_아이템_하나_때문에_나머지_아이템의_순서까지_버리지_않는다() {
        MatchTimelineResponse timeline = timeline(
                new MatchTimelineResponse.Event(744292L, "ITEM_PURCHASED", 10, 3190, null, null),
                new MatchTimelineResponse.Event(832244L, "ITEM_DESTROYED", 10, 3867, null, null),
                new MatchTimelineResponse.Event(966607L, "ITEM_PURCHASED", 10, 3047, null, null),
                new MatchTimelineResponse.Event(1395720L, "ITEM_PURCHASED", 10, 3109, null, null),
                new MatchTimelineResponse.Event(1909328L, "ITEM_PURCHASED", 10, 2524, null, null)
        );

        Optional<List<Integer>> result = calculator.calculate(
                timeline,
                10,
                List.of(3047, 3109, 3190, 3876, 2524),
                List.of(3047, 3109, 3190, 3876, 2524)
        );

        assertThat(result).contains(List.of(3190, 3876, 3047, 3109, 2524));
    }

    @Test
    void 앵커가_될_3867_파괴_이벤트가_없으면_계산하지_않는다() {
        MatchTimelineResponse timeline = timeline(
                new MatchTimelineResponse.Event(100L, "ITEM_PURCHASED", 10, 3190, null, null)
        );

        Optional<List<Integer>> result = calculator.calculate(
                timeline,
                10,
                List.of(3190, 3876),
                List.of(3190, 3876)
        );

        // 시점을 추론할 근거가 없으면 지어내지 않는다
        assertThat(result).isEmpty();
    }

    @Test
    void 퀘스트_아이템이_아닌_아이템은_앵커로_보정하지_않는다() {
        MatchTimelineResponse timeline = timeline(
                new MatchTimelineResponse.Event(100L, "ITEM_PURCHASED", 10, 3190, null, null),
                new MatchTimelineResponse.Event(832244L, "ITEM_DESTROYED", 10, 3867, null, null)
        );

        Optional<List<Integer>> result = calculator.calculate(
                timeline,
                10,
                List.of(3190, 3031),
                List.of(3190, 3031)
        );

        // 무한의 대검(3031)이 구매 기록 없이 최종 아이템에 있는 건 데이터 이상이지 퀘스트가 아니다
        assertThat(result).isEmpty();
    }
}
