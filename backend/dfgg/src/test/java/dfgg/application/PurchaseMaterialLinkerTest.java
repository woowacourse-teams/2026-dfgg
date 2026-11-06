package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.application.match.ParticipantItemEventExtractor;
import dfgg.application.match.PurchaseMaterialLinker;
import dfgg.domain.match.ParticipantItemEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurchaseMaterialLinkerTest {

    private final PurchaseMaterialLinker linker = new PurchaseMaterialLinker();

    @Test
    void 같은_시각의_반복_재료를_원천_순서대로_연결한다() {
        var first = event(1, "ITEM_DESTROYED", 1036, 100L, 0);
        var second = event(2, "ITEM_DESTROYED", 1036, 100L, 1);
        var purchase = event(3, "ITEM_PURCHASED", 3071, 100L, 2);

        assertThat(linker.link(purchase, List.of(first, second, purchase))).contains(List.of(1036, 1036));
    }

    @Test
    void 이전_거래의_재료를_다음_구매에_다시_연결하지_않는다() {
        var destroyed = event(1, "ITEM_DESTROYED", 1036, 100L, 0);
        var firstPurchase = event(2, "ITEM_PURCHASED", 3071, 100L, 1);
        var secondPurchase = event(3, "ITEM_PURCHASED", 1001, 100L, 2);

        assertThat(linker.link(secondPurchase, List.of(destroyed, firstPurchase, secondPurchase)))
                .contains(List.of());
    }

    @Test
    void 다른_시각의_제거는_현재_구매의_재료로_쓰지_않는다() {
        var destroyed = event(1, "ITEM_DESTROYED", 1036, 90L, 0);
        var purchase = event(2, "ITEM_PURCHASED", 3071, 100L, 1);

        assertThat(linker.link(purchase, List.of(destroyed, purchase))).contains(List.of());
    }

    @Test
    void 중간_거래나_시각_결측은_미해결로_남긴다() {
        var destroyed = event(1, "ITEM_DESTROYED", 1036, 100L, 0);
        var sale = event(2, "ITEM_SOLD", 1055, 100L, 1);
        var purchase = event(3, "ITEM_PURCHASED", 3071, 100L, 2);
        var noTime = event(3, "ITEM_PURCHASED", 3071, null, 2);

        assertThat(linker.link(purchase, List.of(destroyed, sale, purchase))).isEmpty();
        assertThat(linker.link(noTime, List.of(destroyed, sale, noTime))).isEmpty();
    }

    @Test
    void 구매_이벤트가_다른_순서로_전달되면_조용히_연결하지_않는다() {
        var destroyed = event(1, "ITEM_DESTROYED", 1036, 100L, 0);
        var purchase = event(3, "ITEM_PURCHASED", 3071, 100L, 2);

        assertThatThrownBy(() -> linker.link(purchase, List.of(destroyed, purchase)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eventOrder");
    }

    private static ParticipantItemEvent event(int order, String type, Integer itemId,
                                               Long timestamp, int eventIndex) {
        return new ParticipantItemEvent("TEST", 1, order,
                "ITEM_PURCHASED".equals(type) ? order : null,
                1, eventIndex, type, itemId, null, null, timestamp,
                "{}", ParticipantItemEventExtractor.NORMALIZATION_VERSION);
    }
}
