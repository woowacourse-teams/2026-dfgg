package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.match.ParticipantItemEventExtractor;
import dfgg.application.match.ParticipantItemPurchaseExtractor;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.ParticipantItemPurchase;
import dfgg.domain.match.PurchaseCostStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParticipantItemPurchaseExtractorTest {

    private final ParticipantItemPurchaseExtractor extractor = new ParticipantItemPurchaseExtractor(
            new ParticipantItemEventExtractor(new ObjectMapper()));

    @Test
    void Component와_Core만_저장하고_원천_구매_순번을_유지한다() {
        var result = extractor.extract("KR_TEST", timeline(), "16.18", types());
        assertThat(result).extracting(ParticipantItemPurchase::getItemId).containsExactly(1036, 1036, 3071);
        assertThat(result).extracting(ParticipantItemPurchase::getPurchaseOrder).containsExactly(1, 2, 5);
        assertThat(result).extracting(ParticipantItemPurchase::getPurchaseType).containsExactly(
                ItemPurchaseType.COMPONENT, ItemPurchaseType.COMPONENT, ItemPurchaseType.CORE);
        assertThat(result).allSatisfy(purchase -> {
            assertThat(purchase.getPatch()).isEqualTo("16.18");
            assertThat(purchase.getCurrentGold()).isNull();
            assertThat(purchase.getItemCost()).isNull();
            assertThat(purchase.getItemCostStatus()).isNull();
        });
        // 취소된 두 번째 롱소드 구매도 원천 구매 로그에서는 제거하지 않는다.
        assertThat(result.get(1).getGameTimeMs()).isEqualTo(101);
    }

    @Test
    void 비용_가설과_미확정_상태를_일관되게_기록한다() {
        var purchase = extractor.extract("KR_TEST", timeline(), "16.18", types()).getFirst();

        purchase.recordCostHypothesis(350);
        assertThat(purchase.getItemCost()).isEqualTo(350);
        assertThat(purchase.getItemCostStatus()).isEqualTo(PurchaseCostStatus.HYPOTHESIS);

        purchase.markCostUnknown();
        assertThat(purchase.getItemCost()).isNull();
        assertThat(purchase.getItemCostStatus()).isEqualTo(PurchaseCostStatus.UNKNOWN);
        assertThatThrownBy(() -> purchase.recordCostHypothesis(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 분류가_없는_아이템은_OTHER로_추측하지_않는다() {
        assertThatThrownBy(() -> extractor.extract("KR_TEST", timeline(), "16.18", Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("분류가 없습니다");
    }

    @Test
    void 스키마의_시간_범위를_초과하면_실패한다() {
        assertThatThrownBy(() -> extractor.extract("KR_TEST",
                timeline().replace("\"timestamp\":100", "\"timestamp\":2147483648"), "16.18", types()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("정수 범위");
    }

    static Map<Integer, ItemPurchaseType> types() {
        return Map.of(1036, ItemPurchaseType.COMPONENT, 3071, ItemPurchaseType.CORE,
                2003, ItemPurchaseType.CONSUMABLE, 3340, ItemPurchaseType.OTHER, 1001, ItemPurchaseType.BOOTS);
    }

    static String timeline() {
        return """
                {"metadata":{"matchId":"KR_TEST"},"info":{"frames":[{"events":[
                    {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":100},
                    {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":101},
                    {"type":"ITEM_UNDO","participantId":1,"beforeId":1036,"afterId":0,"timestamp":102},
                    {"type":"ITEM_PURCHASED","participantId":1,"itemId":2003,"timestamp":150},
                    {"type":"ITEM_PURCHASED","participantId":1,"itemId":3340,"timestamp":160},
                    {"type":"ITEM_DESTROYED","participantId":1,"itemId":1036,"timestamp":200},
                    {"type":"ITEM_PURCHASED","participantId":1,"itemId":3071,"timestamp":200},
                    {"type":"ITEM_SOLD","participantId":1,"itemId":3071,"timestamp":300},
                    {"type":"ITEM_PURCHASED","participantId":2,"itemId":1001,"timestamp":100}
                ]}]}}
                """;
    }
}
