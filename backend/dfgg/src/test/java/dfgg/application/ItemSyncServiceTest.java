package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemSyncServiceTest {

    @Mock
    private DataDragonClient dataDragonClient;

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemSyncService itemSyncService;

    @Test
    void 상위_아이템이_없는_최종_아이템만_저장한다() {
        // given
        ItemResponse response = new ItemResponse(Map.of(
                "1036", new ItemData("롱소드", null, List.of("3071")),
                "3071", new ItemData("칠흑의 양날 도끼", List.of("3044", "3067", "1037"), null),
                "6672", new ItemData("크라켄 학살자", List.of("6690", "3051", "1043"), List.of()),
                "1058", new ItemData(
                        "재료 단계 아이템", List.of("2003"), List.of(), List.of("Damage"), Map.of("11", true), false, 1
                ),
                "2003", new ItemData(
                        "체력 물약", List.of("2010"), List.of(), List.of("Consumable"), Map.of("11", true), true
                ),
                "3340", new ItemData(
                        "와드 토템", List.of("1000"), List.of(), List.of("Trinket"), Map.of("11", true), false
                ),
                "9999", new ItemData(
                        "다른 맵 아이템", List.of("1001"), List.of(), List.of(), Map.of("11", false), false
                )
        ));
        when(dataDragonClient.getItems()).thenReturn(response);

        // when
        itemSyncService.syncCoreItem();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Item>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(captor.capture());

        assertThat(captor.getValue())
                .extracting(Item::getItemId, Item::getName)
                .containsExactlyInAnyOrder(
                        tuple(3071L, "칠흑의 양날 도끼"),
                        tuple(6672L, "크라켄 학살자")
                );
    }

    @Test
    @DisplayName("신발은 마법 부여 업그레이드 경로가 남아있어도 코어 아이템으로 저장한다")
    void syncCoreItem_WhenBootsHaveEnchantUpgradePath_TreatAsCoreItem() {
        // given
        ItemResponse response = new ItemResponse(Map.of(
                "3006", new ItemData(
                        "광전사의 군화",
                        List.of("1001"),
                        List.of("3172"),
                        List.of("Boots", "AttackSpeed", "FutureTag"),
                        Map.of("11", true),
                        false
                )
        ));
        when(dataDragonClient.getItems()).thenReturn(response);

        // when
        itemSyncService.syncCoreItem();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Item>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(captor.capture());

        assertThat(captor.getValue())
                .extracting(Item::getItemId, Item::getName)
                .containsExactly(tuple(3006L, "광전사의 군화"));
        assertThat(captor.getValue().getFirst().getTags())
                .containsExactly("Boots", "AttackSpeed", "FutureTag");
    }

    @Test
    @DisplayName("컴포넌트(from)가 없는 시작 아이템은 업그레이드 경로가 없어도 코어 아이템으로 저장하지 않는다")
    void syncCoreItem_WhenItemHasNoFromComponents_ExcludeAsStartingItem() {
        // given
        ItemResponse response = new ItemResponse(Map.of(
                "1055", new ItemData(
                        "도란의 검", null, null, List.of("Health", "Damage", "Lane"), Map.of("11", true), false
                )
        ));
        when(dataDragonClient.getItems()).thenReturn(response);

        // when
        itemSyncService.syncCoreItem();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Item>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("컴포넌트(from)가 없는 기본 장화는 신발이어도 코어 아이템으로 저장하지 않는다")
    void syncCoreItem_WhenBaseBootsHaveNoFromComponents_ExcludeAsStartingItem() {
        // given
        ItemResponse response = new ItemResponse(Map.of(
                "1001", new ItemData(
                        "장화", null, List.of("3006", "3009"), List.of("Boots"), Map.of("11", true), false
                )
        ));
        when(dataDragonClient.getItems()).thenReturn(response);

        // when
        itemSyncService.syncCoreItem();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Item>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void 데이터_드래곤_조회가_실패하면_저장하지_않는다() {
        // given
        IllegalStateException exception = new IllegalStateException("API failure");
        when(dataDragonClient.getItems()).thenThrow(exception);

        // when & then
        assertThatThrownBy(itemSyncService::syncCoreItem)
                .isSameAs(exception);
        verifyNoInteractions(itemRepository);
    }

    @Test
    void 최종_아이템_ID가_숫자가_아니면_저장하지_않는다() {
        // given
        ItemResponse response = new ItemResponse(Map.of(
                "invalid-id", new ItemData("잘못된 아이템", List.of("1036"), null)
        ));
        when(dataDragonClient.getItems()).thenReturn(response);

        // when & then
        assertThatThrownBy(itemSyncService::syncCoreItem)
                .isInstanceOf(NumberFormatException.class);
        verifyNoInteractions(itemRepository);
    }
}
