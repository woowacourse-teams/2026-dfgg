package dfgg.application.recommend;

import dfgg.application.champion.ChampionService;
import dfgg.application.item.ItemService;
import dfgg.application.recommend.fallback.FallbackChain;
import dfgg.application.recommend.fallback.FallbackRecommendation;
import dfgg.application.recommend.fallback.RecommendationContext;
import dfgg.common.NextItemRecommendationNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.item.Item;
import dfgg.presentation.dto.ItemDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * prefix(이미 구매한 아이템) 조건부 다음 아이템 추천 요청을 폴백 체인에 위임하고,
 * 그 결과를 응답 DTO로 변환한다.
 */
@Service
@Transactional(readOnly = true)
public class NextItemRecommendationService {

    private static final String BOOTS_TAG = "Boots";

    private final ChampionService championService;
    private final ItemService itemService;
    private final FallbackChain fallbackChain;

    public NextItemRecommendationService(
            ChampionService championService,
            ItemService itemService,
            FallbackChain fallbackChain
    ) {
        this.championService = championService;
        this.itemService = itemService;
        this.fallbackChain = fallbackChain;
    }

    public NextItemRecommendationResponse recommendNextItem(NextItemRecommendationRequest request) {
        Champion myChampion = championService.findChampionByName(request.myChampion().name());
        ChampionPosition position = ChampionPosition.valueOf(request.myChampion().position());
        List<Long> allyChampionIds = resolveChampionIds(request.allies());
        List<Long> enemyChampionIds = resolveChampionIds(request.enemies());

        RecommendationContext context = new RecommendationContext(
                myChampion.getChampionId(),
                request.purchasedItemIds(),
                position,
                request.tier(),
                request.patch(),
                allyChampionIds,
                enemyChampionIds
        );

        FallbackRecommendation recommendation = fallbackChain.recommend(context)
                .orElseThrow(() -> new NextItemRecommendationNotFoundException(myChampion.getName(), position.name()));

        List<ItemDto> orderedItems = toOrderedItemDtos(recommendation.itemIds(), request.purchasedItemIds());
        return new NextItemRecommendationResponse(orderedItems, recommendation.servedBy().name());
    }

    private List<Long> resolveChampionIds(List<dfgg.presentation.dto.ChampionDto> champions) {
        return champions.stream()
                .map(dto -> championService.findChampionByName(dto.name()).getChampionId())
                .toList();
    }

    /**
     * {@code findItemsByIds}는 요청한 순서를 보장하지 않으므로, 랭킹 순서(추천 순위)를
     * 그대로 유지하기 위해 원래 itemIds 순서대로 다시 정렬한다.
     *
     * <p>신발은 한 켤레만 신을 수 있다 — 이미 산 아이템 중 신발이 있으면, 폴백 체인이
     * 추천한 후보 중 신발은 전부 제외한다. 안전/탐색 구역 어느 쪽도 "신발 슬롯"이라는
     * 게임 규칙 자체를 모르고 아이템 단위로만 후보를 매기기 때문에, 이미 산 신발과
     * 다른 신발이 여전히 후보에 낄 수 있어 여기서 마지막으로 걸러낸다.
     */
    private List<ItemDto> toOrderedItemDtos(List<Long> rankedItemIds, List<Long> purchasedItemIds) {
        Map<Long, Item> itemById = itemService.findItemsByIds(rankedItemIds).stream()
                .collect(Collectors.toMap(Item::getItemId, Function.identity()));
        boolean bootsAlreadyPurchased = itemService.findItemsByIds(purchasedItemIds).stream()
                .anyMatch(item -> item.hasTag(BOOTS_TAG));

        return rankedItemIds.stream()
                .map(itemById::get)
                .filter(java.util.Objects::nonNull)
                .filter(item -> !bootsAlreadyPurchased || !item.hasTag(BOOTS_TAG))
                .map(ItemDto::from)
                .toList();
    }
}
