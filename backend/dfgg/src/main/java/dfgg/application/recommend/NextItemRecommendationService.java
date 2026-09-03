package dfgg.application.recommend;

import dfgg.application.champion.ChampionService;
import dfgg.application.item.ItemService;
import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateTopK;
import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.GeneratorResult;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.HardValidityFilter;
import dfgg.application.recommend.v3.ranker.CandidateRanker;
import dfgg.application.recommend.v3.ranker.TreeShapCalculator;
import dfgg.application.recommend.v3.ranker.RankedCandidate;
import dfgg.common.NextItemRecommendationNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.item.Item;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.RecommendationReasons;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * v3 추천 파이프라인.
 *
 * <pre>
 * Game State → 4개 Generator → Candidate Union → Hard Validity Filter → Ranker → Top-5
 * </pre>
 *
 * <p>각 단계의 책임이 겹치지 않는다. generator는 후보를 <b>발견</b>만 하고(목적은 recall),
 * 필터는 게임 규칙상 <b>못 사는 것</b>만 걷어내며, 최종 순위는 랭커가 단독으로 정한다.
 * 여기서 점수를 섞거나 context별로 재정렬하지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class NextItemRecommendationService {

    private static final int TOP_N = 5;

    private final ChampionService championService;
    private final ItemService itemService;
    private final List<CandidateGenerator> generators;
    private final HardValidityFilter hardValidityFilter;
    private final CandidateRanker candidateRanker;
    private final CandidateTopK candidateTopK;
    private final TreeShapCalculator treeShapCalculator;

    public NextItemRecommendationService(
            ChampionService championService,
            ItemService itemService,
            List<CandidateGenerator> generators,
            HardValidityFilter hardValidityFilter,
            CandidateRanker candidateRanker,
            CandidateTopK candidateTopK,
            TreeShapCalculator treeShapCalculator
    ) {
        this.championService = championService;
        this.itemService = itemService;
        this.generators = List.copyOf(generators);
        this.hardValidityFilter = hardValidityFilter;
        this.candidateRanker = candidateRanker;
        this.candidateTopK = candidateTopK;
        this.treeShapCalculator = treeShapCalculator;
    }

    public NextItemRecommendationResponse recommendNextItem(NextItemRecommendationRequest request) {
        RecommendationQuery query = toQuery(request);

        List<GeneratorResult> generatorResults = new ArrayList<>();
        for (CandidateGenerator generator : generators) {
            generatorResults.add(generator.generate(query, candidateTopK.of(generator.
                    source())));
        }

        CandidateUnion union = CandidateUnion.merge(generatorResults);
        Map<Long, Item> itemById = loadItems(union, query);
        CandidateUnion valid = hardValidityFilter.filter(union, query.purchasedItemIds(), itemById);

        List<RankedCandidate> ranked = candidateRanker.rank(valid, query, TOP_N);
        if (ranked.isEmpty()) {
            // 기존 v3와 같은 404 계약을 유지한다. 파이프라인을 갈아끼운 것이지
            // "추천할 게 없다"를 표현하는 방식까지 바꿀 이유는 없다.
            throw new NextItemRecommendationNotFoundException(
                    request.myChampion().name(), query.position().name());
        }
        List<RecommendedItemDto> recommendedItems = ranked.stream()
                .map(candidate -> RecommendedItemDto.of(
                        itemById.get(candidate.itemId()),
                        // 순위를 매길 때 쓴 feature 벡터를 그대로 넘긴다. 다시 계산하면
                        // 서빙 점수와 이유가 어긋날 수 있다.
                        RecommendationReasons.of(
                                treeShapCalculator.contributions(candidate.features().values()))))
                .toList();
        return new NextItemRecommendationResponse(recommendedItems, candidateRanker.modelVersion());
    }

    private RecommendationQuery toQuery(NextItemRecommendationRequest request) {
        Champion myChampion = championService.findChampionByName(request.myChampion().name());
        return new RecommendationQuery(
                myChampion.getChampionId(),
                ChampionPosition.valueOf(request.myChampion().position()),
                request.purchasedItemIds(),
                resolveChampionIds(request.allies()),
                resolveChampionIds(request.enemies()),
                request.tier(),
                request.patch()
        );
    }

    private Map<Long, Item> loadItems(CandidateUnion union, RecommendationQuery query) {
        List<Long> itemIds = new ArrayList<>(union.candidates().stream()
                .map(candidate -> candidate.itemId())
                .toList());
        itemIds.addAll(query.purchasedItemIds());
        return itemService.findItemsByIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getItemId, Function.identity(), (first, second) -> first));
    }

    private List<Long> resolveChampionIds(List<ChampionDto> champions) {
        return champions.stream()
                .map(champion -> championService.findChampionByName(champion.name()).getChampionId())
                .toList();
    }
}
