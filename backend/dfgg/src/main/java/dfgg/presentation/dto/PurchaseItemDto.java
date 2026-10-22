package dfgg.presentation.dto;

public record PurchaseItemDto(Long itemId, String name, String imageUrl, int quantity) {
    public PurchaseItemDto {
        if (itemId == null || itemId <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("구매 아이템 ID와 수량은 양수여야 합니다.");
        }
        if (name == null || name.isBlank() || imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("구매 아이템 이름과 이미지 URL은 필수입니다.");
        }
    }
}
