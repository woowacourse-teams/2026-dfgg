package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.item.ItemPurchaseTypeClassifier;
import dfgg.application.item.ItemPurchaseTypeMapService;
import dfgg.application.match.ParticipantItemEventExtractor;
import dfgg.application.match.ParticipantItemPurchaseExtractor;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.ParticipantItemPurchase;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ItemResponse;
import org.junit.jupiter.api.Test;

class ItemPurchaseTypeMapServiceTest {

    private final DataDragonClient client = mock(DataDragonClient.class);
    private final ItemPurchaseTypeMapService service = new ItemPurchaseTypeMapService(client, new ItemPurchaseTypeClassifier());

    @Test
    void 지정한_과거_빌드로_구매_분류_Map을_생성한다() throws Exception {
        try (var input = getClass().getResourceAsStream("/item/purchase-classification-16.18.1.json")) {
            var response = new ObjectMapper().readValue(input, ItemResponse.class);
            when(client.getItems("16.18.1")).thenReturn(new ItemResponse("16.18", "16.18.1", response.data()));
        }
        var types = service.classifyItemsForPatch("16.18", "16.18.1");
        assertThat(types).containsEntry(1036, ItemPurchaseType.COMPONENT);
        var extractor = new ParticipantItemPurchaseExtractor(new ParticipantItemEventExtractor(new ObjectMapper()));
        var purchases = extractor.extract("KR_TEST", ParticipantItemPurchaseExtractorTest.timeline(), "16.18", types);
        assertThat(purchases).extracting(ParticipantItemPurchase::getPurchaseType).containsExactly(
                ItemPurchaseType.COMPONENT, ItemPurchaseType.COMPONENT, ItemPurchaseType.CORE, ItemPurchaseType.BOOTS);
        verify(client).getItems("16.18.1");
    }

    @Test
    void 다른_패치의_빌드를_조회하지_않는다() {
        assertThatThrownBy(() -> service.classifyItemsForPatch("16.18", "16.19.1"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
    }
}
