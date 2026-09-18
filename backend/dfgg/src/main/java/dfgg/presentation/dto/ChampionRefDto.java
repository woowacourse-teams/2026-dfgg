package dfgg.presentation.dto;

/**
 * 추천 이유에서 지목한 챔피언. id와 한글명, 화면에 그릴 이미지 URL을 함께 낸다.
 */
public record ChampionRefDto(
        Long id,
        String name,
        String imageUrl
) {
}
