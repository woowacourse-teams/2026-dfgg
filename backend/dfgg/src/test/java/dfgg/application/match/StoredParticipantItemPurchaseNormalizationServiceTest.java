package dfgg.application.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.item.ItemPurchaseTypeClassifier;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ItemData;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StoredParticipantItemPurchaseNormalizationServiceTest {

    private final RawMatchRepository rawMatchRepository = mock(RawMatchRepository.class);
    private final DataDragonClient dataDragonClient = mock(DataDragonClient.class);
    private final ItemPurchaseTypeClassifier classifier = mock(ItemPurchaseTypeClassifier.class);
    private final ParticipantItemPurchaseNormalizationService normalizationService =
            mock(ParticipantItemPurchaseNormalizationService.class);
    private StoredParticipantItemPurchaseNormalizationService service;

    @BeforeEach
    void setUp() {
        service = new StoredParticipantItemPurchaseNormalizationService(rawMatchRepository, dataDragonClient,
                classifier, normalizationService, new ObjectMapper());
    }

    @Test
    void Raw_패치를_읽고_같은_패치_카탈로그를_재사용하며_경기별_결과를_반환한다() {
        when(rawMatchRepository.findMatchIdsWithTimeline()).thenReturn(List.of("KR_1", "KR_2", "KR_3"));
        when(rawMatchRepository.findById("KR_1"))
                .thenReturn(Optional.of(rawMatch("KR_1", "16.18.815.9717")));
        when(rawMatchRepository.findById("KR_2"))
                .thenReturn(Optional.of(rawMatch("KR_2", "16.19.1.123")));
        when(rawMatchRepository.findById("KR_3"))
                .thenReturn(Optional.of(rawMatch("KR_3", "16.18.815.9717")));
        ItemResponse firstCatalog = catalog("16.18", "16.18.1");
        ItemResponse secondCatalog = catalog("16.19", "16.19.1");
        when(dataDragonClient.resolveItemDataVersionForPatch("16.18")).thenReturn("16.18.1");
        when(dataDragonClient.resolveItemDataVersionForPatch("16.19")).thenReturn("16.19.1");
        when(dataDragonClient.getItems("16.18.1")).thenReturn(firstCatalog);
        when(dataDragonClient.getItems("16.19.1")).thenReturn(secondCatalog);
        Map<Integer, ItemPurchaseType> types = Map.of(1001, ItemPurchaseType.BOOTS);
        when(classifier.classify(firstCatalog.data())).thenReturn(types);
        when(classifier.classify(secondCatalog.data())).thenReturn(types);
        when(normalizationService.normalize("KR_1", "16.18", types, firstCatalog, positions())).thenReturn(2);
        when(normalizationService.normalize("KR_2", "16.19", types, secondCatalog, positions())).thenReturn(1);
        when(normalizationService.normalize("KR_3", "16.18", types, firstCatalog, positions()))
                .thenThrow(new IllegalStateException("Timeline 오류"));

        PurchaseNormalizationResult result = service.normalizeAll();

        assertThat(result.processed()).isEqualTo(3);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.savedPurchases()).isEqualTo(3);
        assertThat(result.failures()).containsExactly("KR_3: Timeline 오류");
        verify(dataDragonClient).resolveItemDataVersionForPatch("16.18");
        verify(dataDragonClient).resolveItemDataVersionForPatch("16.19");
        verify(dataDragonClient).getItems("16.18.1");
        verify(dataDragonClient).getItems("16.19.1");
    }

    @Test
    void 잘못된_Raw_버전은_경기_실패로_기록하고_다음_경기를_처리한다() {
        when(rawMatchRepository.findMatchIdsWithTimeline()).thenReturn(List.of("KR_1", "KR_2"));
        when(rawMatchRepository.findById("KR_1"))
                .thenReturn(Optional.of(new RawMatch("KR_1", "{\"info\":{}}")));
        when(rawMatchRepository.findById("KR_2"))
                .thenReturn(Optional.of(rawMatch("KR_2", "16.18.815.9717")));
        ItemResponse catalog = catalog("16.18", "16.18.1");
        when(dataDragonClient.resolveItemDataVersionForPatch("16.18")).thenReturn("16.18.1");
        when(dataDragonClient.getItems("16.18.1")).thenReturn(catalog);
        when(classifier.classify(catalog.data())).thenReturn(Map.of(1001, ItemPurchaseType.BOOTS));

        PurchaseNormalizationResult result = service.normalizeAll();

        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failures()).containsExactly("KR_1: Raw Match에 gameVersion이 없습니다.");
        verify(normalizationService).normalize("KR_2", "16.18", Map.of(1001, ItemPurchaseType.BOOTS),
                catalog, positions());
    }

    @Test
    void 카탈로그_장애는_즉시_전달한다() {
        when(rawMatchRepository.findMatchIdsWithTimeline()).thenReturn(List.of("KR_1"));
        when(rawMatchRepository.findById("KR_1"))
                .thenReturn(Optional.of(rawMatch("KR_1", "16.18.815.9717")));
        when(dataDragonClient.resolveItemDataVersionForPatch("16.18"))
                .thenThrow(new IllegalStateException("Data Dragon 접속 실패"));

        assertThatThrownBy(service::normalizeAll)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Data Dragon");
        verifyNoInteractions(normalizationService);
    }

    private ItemResponse catalog(String patch, String dataVersion) {
        return new ItemResponse(patch, dataVersion, Map.of("1001", mock(ItemData.class)));
    }

    private RawMatch rawMatch(String matchId, String version) {
        return new RawMatch(matchId, "{\"info\":{\"gameVersion\":\"" + version
                + "\",\"participants\":[{\"participantId\":1,\"teamPosition\":\"TOP\"}]}}");
    }

    private Map<Integer, String> positions() {
        return Map.of(1, "TOP");
    }
}
