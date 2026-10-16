package dfgg.application.recommend.situational;

import dfgg.domain.champion.Champion;
import dfgg.domain.image.ImageUrls;
import dfgg.infrastructure.external.dto.ItemResponse;
import dfgg.presentation.dto.ChampionRefDto;
import dfgg.presentation.dto.SituationalItemDto;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 상황 대응 추천에 카탈로그 이름·이미지와 근거 챔피언 표시 정보를 붙인다. */
@Component
public class SituationalItemMapper {
    private final ImageUrls images;

    public SituationalItemMapper(ImageUrls images) {
        this.images = images;
    }

    public List<SituationalItemDto> toDtos(List<SituationalComponentRuntime.SituationalRecommendation> recommendations,
                                           ItemResponse catalog, Map<Long, Champion> enemies) {
        return recommendations.stream().map(recommendation -> toDto(recommendation, catalog, enemies)).toList();
    }

    private SituationalItemDto toDto(SituationalComponentRuntime.SituationalRecommendation recommendation,
                                     ItemResponse catalog, Map<Long, Champion> enemies) {
        var item = catalog.data().get(Integer.toString(recommendation.itemId()));
        if (item == null) throw new IllegalStateException("상황 대응 아이템의 카탈로그 정보가 없습니다: " + recommendation.itemId());
        var reasons = recommendation.reasonChampionIds().stream()
                .map(id -> enemies.get(id.longValue()))
                .map(champion -> new ChampionRefDto(champion.getChampionId(), champion.getName().get("ko-KR"),
                        images.championOf(champion.getRiotKey())))
                .toList();
        return new SituationalItemDto((long) recommendation.itemId(), item.name(), images.itemOf(recommendation.itemId()),
                recommendation.effect().name(), reasons, recommendation.additionalCost(), recommendation.affordable());
    }
}
