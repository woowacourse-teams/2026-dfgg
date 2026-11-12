package dfgg.application.recommend.situational;

import dfgg.domain.champion.Champion;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.item.trait.ItemTraitCatalog;
import dfgg.domain.language.Language;
import dfgg.infrastructure.external.dto.ItemResponse;
import dfgg.presentation.dto.ChampionRefDto;
import dfgg.presentation.dto.RecommendationDescription;
import dfgg.presentation.dto.SituationalItemDto;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 상황 대응 추천을 v3 추천 아이템과 같은 이름·이미지·추천 이유 형식으로 바꾼다. */
@Component
public class SituationalItemMapper {
    private final ImageUrls images;
    private final ItemTraitCatalog traits;

    public SituationalItemMapper(ImageUrls images, ItemTraitCatalog traits) {
        this.images = images;
        this.traits = traits;
    }

    public List<SituationalItemDto> toDtos(List<SituationalComponentRuntime.SituationalRecommendation> recommendations,
                                           ItemResponse catalog, Map<Long, Champion> enemies, Language language) {
        return recommendations.stream().map(recommendation -> toDto(recommendation, catalog, enemies, language)).toList();
    }

    private SituationalItemDto toDto(SituationalComponentRuntime.SituationalRecommendation recommendation,
                                     ItemResponse catalog, Map<Long, Champion> enemies, Language language) {
        var item = catalog.data().get(Integer.toString(recommendation.itemId()));
        if (item == null) throw new IllegalStateException("상황 대응 아이템의 카탈로그 정보가 없습니다: " + recommendation.itemId());
        // 상대 조합 때문에 낸 추천이라 근거 적 챔피언은 v3의 counter와 같은 의미다. 아군 근거는 없다.
        var counter = recommendation.reasonChampionIds().stream()
                .map(id -> enemies.get(id.longValue()))
                .map(champion -> new ChampionRefDto(champion.getChampionId(), language.pick(champion.getName()),
                        images.championOf(champion.getRiotKey())))
                .toList();
        String name = language.pick(catalog.localizedName(Integer.toString(recommendation.itemId())));
        return new SituationalItemDto((long) recommendation.itemId(), name, images.itemOf(recommendation.itemId()),
                new RecommendationDescription(counter, List.of(), traitNamesOf(recommendation.itemId(), language)),
                recommendation.additionalCost(), recommendation.affordable());
    }

    /** v3와 같은 규칙: 표시명이 없는 특성은 빼고 매 요청 같은 순서로 낸다. */
    private List<String> traitNamesOf(int itemId, Language language) {
        return traits.traitsOf(itemId).stream()
                .map(trait -> language.pick(trait.getDisplayName()))
                .filter(displayName -> !displayName.isBlank())
                .sorted()
                .toList();
    }
}
