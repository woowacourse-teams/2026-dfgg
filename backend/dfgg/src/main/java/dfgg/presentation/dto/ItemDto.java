package dfgg.presentation.dto;

import dfgg.domain.item.Item;

public record ItemDto(
        Long id,
        String name
) {
    public static ItemDto from(Item item) {
        return new ItemDto(
                item.getItemId(),
                item.getName()
        );
    }
}
