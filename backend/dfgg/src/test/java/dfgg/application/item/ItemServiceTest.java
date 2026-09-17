package dfgg.application.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ItemData;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private DataDragonClient dataDragonClient;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemImageService itemImageService;

    @InjectMocks
    private ItemService itemService;

    private ItemData item(String filename, List<String> from, List<String> into, List<String> tags,
                          Map<String, Boolean> maps, Boolean purchasable, Boolean inStore,
                          Boolean hidden, Boolean consumed) {
        return new ItemData(filename, from, into, tags, maps, consumed, 1,
                new ItemData.Gold(350, 350, purchasable), new ItemData.Image(filename), inStore, hidden);
    }

    private ItemData equipment(String filename, List<String> from, List<String> into) {
        return item(filename, from, into, List.of("Damage"), Map.of("11", true), true, null, null, false);
    }

    @Test
    void 협곡의_시작_조합_완성_아이템과_기본_장화를_이미지와_함께_저장한다() {
        when(dataDragonClient.getItems()).thenReturn(new ItemResponse("16.18", "16.18.1", Map.of(
                "1036", equipment("1036.png", null, List.of("3133")),
                "3133", equipment("3133.png", List.of("1036"), List.of("3071")),
                "3071", equipment("3071.png", List.of("3133"), null),
                "1055", equipment("1055.png", null, null),
                "1001", item("1001.png", null, List.of("3006"), List.of("Boots"),
                        Map.of("11", true), true, null, null, false))));
        when(itemImageService.store(org.mockito.ArgumentMatchers.eq("16.18"),
                org.mockito.ArgumentMatchers.eq("16.18.1"), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> "https://cdn.example.com/images/16.18/items/" + call.getArgument(2));

        itemService.syncItems();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Item>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Item::getItemId)
                .containsExactlyInAnyOrder(1036L, 3133L, 3071L, 1055L, 1001L);
        assertThat(captor.getValue()).allSatisfy(saved -> {
            assertThat(saved.getUrl()).isEqualTo("https://cdn.example.com/images/16.18/items/" + saved.getItemId() + ".png");
            assertThat(saved.getGold()).containsEntry("total", 350);
        });
        Item component = captor.getValue().stream().filter(i -> i.getItemId() == 3133L).findFirst().orElseThrow();
        assertThat(component.getFromItemIds()).containsExactly("1036");
        assertThat(component.getIntoItemIds()).containsExactly("3071");
    }

    @Test
    void 소모품_장신구_다른맵_구매불가_숨김_아이템을_제외한다() {
        Map<String, ItemData> excluded = new java.util.LinkedHashMap<>();
        excluded.put("2003", item("2003.png", null, null, List.of("Consumable"), Map.of("11", true), true, null, null, false));
        excluded.put("3340", item("3340.png", null, null, List.of("Trinket"), Map.of("11", true), true, null, null, false));
        excluded.put("1", item("1.png", null, null, List.of(), Map.of("11", false, "12", true), true, null, null, false));
        excluded.put("2", item("2.png", null, null, List.of(), Map.of("12", true), true, null, null, false));
        excluded.put("3", item("3.png", null, null, List.of(), null, true, null, null, false));
        excluded.put("4", item("4.png", null, null, List.of(), Map.of("11", true), false, null, null, false));
        excluded.put("5", item("5.png", null, null, List.of(), Map.of("11", true), true, false, null, false));
        excluded.put("6", item("6.png", null, null, List.of(), Map.of("11", true), true, null, true, false));
        excluded.put("7", item("7.png", null, null, List.of(), Map.of("11", true), true, null, null, true));
        when(dataDragonClient.getItems()).thenReturn(new ItemResponse("16.18", "16.18.1", excluded));
        itemService.syncItems();
        verify(itemRepository).saveAll(List.of());
        verifyNoInteractions(itemImageService);
    }

    @Test
    void 이미지_실패시_앞선_업로드가_있어도_DB를_저장하지_않는다() {
        Map<String, ItemData> data = new java.util.LinkedHashMap<>();
        data.put("1036", equipment("1036.png", null, List.of("3133")));
        data.put("3133", equipment("3133.png", List.of("1036"), List.of("3071")));
        when(dataDragonClient.getItems()).thenReturn(new ItemResponse("16.18", "16.18.1", data));
        when(itemImageService.store("16.18", "16.18.1", "1036.png")).thenReturn("https://cdn.example.com/1036.png");
        when(itemImageService.store("16.18", "16.18.1", "3133.png")).thenThrow(new IllegalStateException("업로드 실패"));
        assertThatThrownBy(itemService::syncItems).hasMessage("업로드 실패");
        verifyNoInteractions(itemRepository);
    }

    @Test
    void 이미지_정보_누락시_DB를_저장하지_않는다() {
        ItemData missing = new ItemData("롱소드", null, null, List.of(), Map.of("11", true), false, 1,
                new ItemData.Gold(350, 350, true), null, null, null);
        when(dataDragonClient.getItems()).thenReturn(new ItemResponse("16.18", "16.18.1", Map.of("1036", missing)));
        assertThatThrownBy(itemService::syncItems).hasMessageContaining("이미지 정보가 없습니다");
        verifyNoInteractions(itemRepository, itemImageService);
    }

    @Test
    void 데이터_드래곤_조회가_실패하면_저장하지_않는다() {
        when(dataDragonClient.getItems()).thenThrow(new IllegalStateException("API failure"));
        assertThatThrownBy(itemService::syncItems).hasMessage("API failure");
        verifyNoInteractions(itemRepository, itemImageService);
    }

    @Test
    void ID로_아이템을_조회한다() {
        // given
        List<Long> itemIds = List.of(3071L, 6610L);
        List<Item> items = List.of(
                new Item(3071L, Map.of("ko-KR", "아이템 A")),
                new Item(6610L, Map.of("ko-KR", "아이템 B"))
        );
        when(itemRepository.findAllById(itemIds)).thenReturn(items);

        // when
        List<Item> foundItems = itemService.findItemsByIds(itemIds);

        // then
        assertThat(foundItems).containsExactlyElementsOf(items);
    }

    @Test
    void 코어_아이템_ID를_조회한다() {
        // given
        when(itemRepository.findAll()).thenReturn(List.of(
                new Item(3071L, Map.of("ko-KR", "아이템 A")),
                new Item(6610L, Map.of("ko-KR", "아이템 B"))
        ));

        // when
        Set<Integer> coreItemIds = itemService.findCoreItemIds();

        // then
        assertThat(coreItemIds).containsExactlyInAnyOrder(3071, 6610);
    }
}
