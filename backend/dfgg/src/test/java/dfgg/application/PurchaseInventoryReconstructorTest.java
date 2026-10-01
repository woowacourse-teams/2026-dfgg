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
    void 기록에_없는_아이템도_제거_직전_보유로_인정하고_이후_복원을_계속한다() {
        List<ParticipantItemEvent> events = events("""
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":2001,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":101},
                {"type":"ITEM_PURCHASED","participantId":2,"itemId":1037,"timestamp":101}
                """);

        var before = reconstructor.beforePurchases(events);

        assertThat(before.get(events.get(1))).isEqualTo(ItemInventory.empty());
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

    @Test
    void 역할_표시는_일반_인벤토리와_조합_재료에_포함하지_않는다() {
        var events = events("""
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1200,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100}
                """);
        assertThat(reconstructor.reconstruct(events, "16.18").get(events.getLast()).inventoryBefore())
                .isEqualTo(ItemInventory.empty());
        assertThat(reconstructor.reconstruct(events, "16.17").get(events.getLast()).inventoryBefore())
                .isEqualTo(ItemInventory.empty().add(1200));
    }

    @Test
    void 자동_소모품_제거는_이전_구매_상태를_소급해서_바꾸지_않는다() {
        var events = events("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":2151,"timestamp":200},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":300}
                """);
        var states = reconstructor.reconstruct(events, "16.18");
        assertThat(states.get(events.getFirst()).inventoryBefore()).isEqualTo(ItemInventory.empty());
        assertThat(states.get(events.getLast()).inventoryBefore()).isEqualTo(ItemInventory.empty().add(1036));
    }

    @Test
    void 자동_장화를_조합한_구매_직전에는_장화가_한_개_포함된다() {
        var events = events("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1029,"timestamp":100},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":2422,"timestamp":200},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1029,"timestamp":200},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3047,"timestamp":200}
                """);
        assertThat(reconstructor.reconstruct(events, "16.18").get(events.getLast()).inventoryBefore())
                .isEqualTo(ItemInventory.empty().add(2422).add(1029));
    }

    @Test
    void 미드_퀘스트_장화_자동_변형을_후속_구매에_반영한다() {
        var events = events("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3009,"timestamp":100},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":3009,"timestamp":200},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1201,"timestamp":200},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":300}
                """);
        assertThat(reconstructor.reconstruct(events, "16.18").get(events.getLast()).inventoryBefore())
                .isEqualTo(ItemInventory.empty().add(3170));
    }

    @Test
    void 퀘스트_완료_후_장화_구매의_자기_제거를_조합_재료로_복원하지_않는다() {
        var events = events("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1001,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1033,"timestamp":110},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1201,"timestamp":200},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1001,"timestamp":300},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1033,"timestamp":300},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":3111,"timestamp":300},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3111,"timestamp":300},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":400}
                """);
        var states = reconstructor.reconstruct(events, "16.18");
        assertThat(states.get(events.get(6)).inventoryBefore()).isEqualTo(ItemInventory.empty().add(1001).add(1033));
        assertThat(states.get(events.getLast()).inventoryBefore()).isEqualTo(ItemInventory.empty().add(3173));
    }

    @Test
    void 서포터_자동_지급과_확정된_변형은_반영하고_선택_결과가_없는_완료는_보류한다() {
        var events = events("""
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":3865,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":200},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":3866,"timestamp":300},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":400},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":3867,"timestamp":500},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1052,"timestamp":600}
                """);
        var states = reconstructor.reconstruct(events, "16.18");
        assertThat(states.get(events.get(1)).inventoryBefore()).isEqualTo(ItemInventory.empty().add(3866));
        assertThat(states.get(events.get(3)).inventoryBefore()).isEqualTo(ItemInventory.empty().add(1036).add(3867));
        assertThat(states.get(events.getLast()).inventoryFailure())
                .isEqualTo(PurchaseInventoryReconstructor.InventoryFailure.TRANSFORM_OUTPUT_UNOBSERVED);
    }

    @Test
    void 즉시_소비_엘릭서는_과거_보유_아이템이나_구매_후_보유_아이템으로_추가하지_않는다() {
        var events = events("""
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":2139,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":2139,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":200}
                """);
        var states = reconstructor.reconstruct(events, "16.18");
        assertThat(states.get(events.get(1)).inventoryBefore()).isEqualTo(ItemInventory.empty());
        assertThat(states.get(events.getLast()).inventoryBefore()).isEqualTo(ItemInventory.empty());
    }

    @Test
    void 바텀_퀘스트의_장화_슬롯_이동은_이후_조합에서_한_번만_사용된다() {
        var events = events("""
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1202,"timestamp":100},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":2422,"timestamp":100},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1207,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1029,"timestamp":200},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":2422,"timestamp":300},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1029,"timestamp":300},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3047,"timestamp":300}
                """);
        var states = reconstructor.reconstruct(events, "16.18");
        assertThat(states.get(events.get(3)).inventoryBefore()).isEqualTo(ItemInventory.empty().add(2422));
        assertThat(states.get(events.getLast()).inventoryBefore()).isEqualTo(ItemInventory.empty().add(2422).add(1029));
    }

    @Test
    void 관측된_마나무네_자동_변형_이후에는_무라마나를_보유한다() {
        var events = events("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3004,"timestamp":100},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":3004,"timestamp":200},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":300}
                """);
        assertThat(reconstructor.reconstruct(events, "16.18").get(events.getLast()).inventoryBefore())
                .isEqualTo(ItemInventory.empty().add(3042));
    }

    @Test
    void 개별_지급_규칙이_없어도_제거된_아이템의_보유를_인정한다() {
        var events = events("""
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1036,"timestamp":100},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":200}
                """);
        assertThat(reconstructor.reconstruct(events, "16.18").get(events.getLast()).inventoryBefore())
                .isEqualTo(ItemInventory.empty());
    }

    private List<ParticipantItemEvent> events(String payload) {
        String raw = "{\"info\":{\"frames\":[{\"events\":[" + payload + "]}]}}";
        return extractor.extract("KR_TEST", raw);
    }
}
