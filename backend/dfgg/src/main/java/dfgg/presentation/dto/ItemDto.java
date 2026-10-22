package dfgg.presentation.dto;

import dfgg.domain.item.Item;

public record ItemDto(
        Long id,
        String name,
        String imageUrl
) {
    public static ItemDto of(Item item, String imageUrl) {
        return new ItemDto(
                item.getItemId(),
                item.getName().get("ko-KR"),
                imageUrl
        );
    }
}
