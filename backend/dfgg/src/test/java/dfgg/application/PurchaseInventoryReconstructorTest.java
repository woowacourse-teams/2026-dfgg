package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.match.ParticipantItemEventExtractor;
import dfgg.application.match.PurchaseInventoryReconstructor;
import dfgg.application.match.PurchaseMaterialLinker;
import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.ParticipantItemEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurchaseInventoryReconstructorTest {

    private final ParticipantItemEventExtractor extractor = new ParticipantItemEventExtractor(new ObjectMapper());
    private final PurchaseInventoryReconstructor reconstructor =
            new PurchaseInventoryReconstructor(new PurchaseMaterialLinker());

    @Test
    void 구매_직전_상태에는_조합으로_제거될_재료가_포함된다() {
        List<ParticipantItemEvent> events = events("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":101},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":2003,"timestamp":102},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1036,"timestamp":200},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3071,"timestamp":200},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":201}
                """);

        var before = reconstructor.beforePurchases(events);

        assertThat(before.get(events.get(4))).isEqualTo(ItemInventory.empty()
                .add(1036).add(1036).add(2003));
        assertThat(before.get(events.get(5))).isEqualTo(ItemInventory.empty()
                .add(1036).add(2003).add(3071));
    }

    @Test
    void 판매와_취소는_한_개만_되돌리고_다른_참가자와_섞이지_않는다() {
        List<ParticipantItemEvent> events = events("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":101},
                {"type":"ITEM_SOLD","participantId":1,"itemId":1036,"timestamp":102},
                {"type":"ITEM_UNDO","participantId":1,"beforeId":0,"afterId":1036,"timestamp":103},
                {"type":"ITEM_UNDO","participantId":1,"beforeId":1036,"afterId":0,"timestamp":104},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":105},
                {"type":"ITEM_PURCHASED","participantId":2,"itemId":1001,"timestamp":100}
                """);

        var before = reconstructor.beforePurchases(events);

        assertThat(before.get(events.get(5)).quantityOf(1036)).isEqualTo(1);
        assertThat(before.get(events.get(6))).isEqualTo(ItemInventory.empty());
    }

    @Test
    void 기록에_없는_아이템이_제거되면_이후_구매의_상태를_확정하지_않는다() {
        List<ParticipantItemEvent> events = events("""
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":2001,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":101},
                {"type":"ITEM_PURCHASED","participantId":2,"itemId":1037,"timestamp":101}
                """);

        var before = reconstructor.beforePurchases(events);

        assertThat(before).doesNotContainKey(events.get(1));
        assertThat(before.get(events.get(2))).isEqualTo(ItemInventory.empty());
    }

    @Test
    void 알_수_없는_아이템_행동은_이후_구매의_상태를_확정하지_않는다() {
        List<ParticipantItemEvent> events = events("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100},
                {"type":"ITEM_FUTURE","participantId":1,"timestamp":101},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":102}
                """);

        assertThat(reconstructor.beforePurchases(events)).containsKey(events.getFirst())
                .doesNotContainKey(events.getLast());
    }

    @Test
    void 같은_시각의_판매_뒤_구매에는_판매_후_상태를_사용한다() {
        List<ParticipantItemEvent> events = events("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100},
                {"type":"ITEM_SOLD","participantId":1,"itemId":1036,"timestamp":200},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":200}
                """);

        assertThat(reconstructor.beforePurchases(events).get(events.getLast())).isEqualTo(ItemInventory.empty());
    }

    private List<ParticipantItemEvent> events(String payload) {
        String raw = "{\"info\":{\"frames\":[{\"events\":[" + payload + "]}]}}";
        return extractor.extract("KR_TEST", raw);
    }
}
