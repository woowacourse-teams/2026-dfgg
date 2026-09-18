package dfgg.application.recommend;

import dfgg.application.champion.ChampionService;
import dfgg.application.item.ItemService;
import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateTopK;
import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.GeneratorResult;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.HardValidityFilter;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.ranker.CandidateRanker;
import dfgg.application.recommend.v3.explanation.ChampionDirectory;
import dfgg.application.recommend.v3.explanation.AllyEvidence;
import dfgg.application.recommend.v3.explanation.CounterEvidence;
import dfgg.application.recommend.v3.explanation.ChampionProfile;
import dfgg.application.recommend.v3.ranker.GroupedContributions;
import dfgg.application.recommend.v3.ranker.RankedCandidate;
import dfgg.application.recommend.v3.ranker.TreeShapCalculator;
import dfgg.common.exception.InvalidRecommendationRequestException;
import dfgg.common.exception.NextItemRecommendationNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.item.Item;
import dfgg.infrastructure.config.LeagueOfLegendsVersionProperties;
import dfgg.domain.item.trait.ItemTrait;
import dfgg.domain.item.trait.ItemTraitCatalog;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.ChampionRefDto;
import dfgg.presentation.dto.RecommendationDescription;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(NextItemRecommendationService.class);

    private static final int TOP_N = 5;

    private final ChampionService championService;
    private final ItemService itemService;
    private final List<CandidateGenerator> generators;
    private final HardValidityFilter hardValidityFilter;
    private final CandidateRanker candidateRanker;
    private final CandidateTopK candidateTopK;
    private final TreeShapCalculator treeShapCalculator;
    private final ChampionDirectory championDirectory;
    private final ItemTraitCatalog itemTraitCatalog;
    private final ImageUrls imageUrls;
    private final LeagueOfLegendsVersionProperties leagueOfLegendsVersion;

    public NextItemRecommendationService(
            ChampionService championService,
            ItemService itemService,
            List<CandidateGenerator> generators,
            HardValidityFilter hardValidityFilter,
            CandidateRanker candidateRanker,
            CandidateTopK candidateTopK,
            TreeShapCalculator treeShapCalculator,
            ChampionDirectory championDirectory,
            ItemTraitCatalog itemTraitCatalog,
            ImageUrls imageUrls,
            LeagueOfLegendsVersionProperties leagueOfLegendsVersion
    ) {
        this.championService = championService;
        this.itemService = itemService;
        this.generators = List.copyOf(generators);
        this.hardValidityFilter = hardValidityFilter;
        this.candidateRanker = candidateRanker;
        this.candidateTopK = candidateTopK;
        this.treeShapCalculator = treeShapCalculator;
        this.championDirectory = championDirectory;
        this.itemTraitCatalog = itemTraitCatalog;
        this.imageUrls = imageUrls;
        this.leagueOfLegendsVersion = leagueOfLegendsVersion;
    }

    public NextItemRecommendationResponse recommendNextItem(NextItemRecommendationRequest request) {
        Champion myChampion = championService.findChampionByName(request.myChampion().name());
        RecommendationQuery query = toQuery(request, myChampion);
        if (query.position().isFullBuild(query.purchasedItemCount())) {
            return new NextItemRecommendationResponse(List.of());
        }

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
        // 근거로 지목할 챔피언 이름은 요청당 한 번에 해석한다. 후보마다 조회하면 N+1이 된다.
        List<Long> evidenceChampionIds = new ArrayList<>(query.enemyChampionIds());
        evidenceChampionIds.addAll(query.allyChampionIds());
        Map<Long, ChampionProfile> championProfiles = championDirectory.resolve(evidenceChampionIds);

        List<RecommendedItemDto> recommendedItems = new ArrayList<>();
        for (RankedCandidate candidate : ranked) {
            Item item = itemById.get(candidate.itemId());
            ItemCandidate evidence = valid.candidateOf(candidate.itemId());
            logContributions(candidate, evidence);

            recommendedItems.add(RecommendedItemDto.of(item, imageUrls.itemOf(item.getItemId()),
                    new RecommendationDescription(
                            championRefs(CounterEvidence.championIdsFor(evidence), championProfiles),
                            championRefs(AllyEvidence.championIdsFor(
                                    evidence, itemTraitCatalog.synergyOf(item)), championProfiles),
                            traitNamesOf(item))));
        }
        return new NextItemRecommendationResponse(recommendedItems);
    }

    /**
     * 이 아이템이 왜 이 순위인지를 SHAP 묶음 기여도로 남긴다.
     * 응답에는 싣지 않는다.
     * SHAP은 "예측을 얼마나 밀었나"를 답할 뿐 사용자에게 보일 이유가 아니다.
     * <p>
     * 소속(sources)을 함께 남긴다. 부호만으로는 의미가 정해지지 않는다.
     * counter가 찾지 않은 후보에서도 결측 feature 때문에 COUNTER 기여가 양수로 잡힐 수 있다.
     * <p>
     * 어느 모델이 순위를 냈는지(model)도 남긴다. 응답에서 {@code servedBy}를 뺐으므로 모델을 추적할 곳은 여기뿐이다.
     * <p>
     * 순위를 매길 때 쓴 feature 벡터를 그대로 쓴다. 다시 계산하면 서빙 점수와 어긋날 수 있다.
     * DEBUG가 꺼져 있으면 SHAP을 아예 계산하지 않는다.
     */
    private void logContributions(RankedCandidate candidate, ItemCandidate evidence) {
        if (!log.isDebugEnabled()) {
            return;
        }
        GroupedContributions contributions = GroupedContributions.of(
                treeShapCalculator.contributions(candidate.features().values()));
        log.debug("v3 ranking model={} itemId={} score={} sources={} baseValue={} contributions=[{}]",
                candidateRanker.modelVersion(), candidate.itemId(), candidate.modelScore(),
                new TreeSet<>(evidence.sources()), contributions.baseValue(), contributions.describe());
    }

    /** 이름을 못 찾은 챔피언은 뺀다. id만으로는 화면에 쓸 수 없다. */
    private List<ChampionRefDto> championRefs(
            List<Long> championIds, Map<Long, ChampionProfile> championProfiles) {
        return championIds.stream()
                .map(championProfiles::get)
                .filter(Objects::nonNull)
                .map(profile -> new ChampionRefDto(profile.championId(), profile.name(),
                        imageUrls.championOf(profile.riotKey())))
                .toList();
    }

    /**
     * 아이템 자체의 성질이라 모델 판단과 무관하다. 게이트를 걸지 않는다.
     * <p>
     * 팀이 정한 표시명을 낸다. enum 이름({@code CC_CLEANSE})은 코드의 식별자라 화면에 쓸 말이 아니다.
     * 정렬은 매 요청 같은 순서를 내기 위한 것이다.
     */
    private List<String> traitNamesOf(Item item) {
        return itemTraitCatalog.traitsOf(item).stream()
                .map(ItemTrait::getDisplayName)
                .sorted()
                .toList();
    }

    private RecommendationQuery toQuery(NextItemRecommendationRequest request, Champion myChampion) {
        List<Champion> allies = resolveChampions(request.allies());
        List<Champion> enemies = resolveChampions(request.enemies());
        rejectDuplicateChampions(myChampion, allies, enemies);
        return new RecommendationQuery(
                myChampion.getChampionId(),
                ChampionPosition.valueOf(request.myChampion().position()),
                request.purchasedItemIds(),
                championIdsOf(allies),
                championIdsOf(enemies),
                request.tier(),
                patchOf(request)
        );
    }

    private String patchOf(NextItemRecommendationRequest request) {
        if (request.patch() == null || request.patch().isBlank()) {
            return leagueOfLegendsVersion.version();
        }
        return request.patch();
    }

    /**
     * 한 게임에 같은 챔피언은 둘일 수 없다 — 나·아군·적 10명 모두 달라야 한다.
     * <p>
     * 이름이 아니라 해석된 ID로 비교한다. "Yasuo"와 "야스오"는 같은 챔피언이다.
     * {@link RecommendationQuery}도 내 챔피언의 중복은 검사하지만 그건 불변식이라 500이 된다.
     * 요청의 모순은 여기서 입력 오류로 걸러낸다.
     */
    private void rejectDuplicateChampions(Champion myChampion, List<Champion> allies, List<Champion> enemies) {
        List<Champion> everyone = new ArrayList<>();
        everyone.add(myChampion);
        everyone.addAll(allies);
        everyone.addAll(enemies);

        Set<Long> seen = new HashSet<>();
        for (Champion champion : everyone) {
            if (!seen.add(champion.getChampionId())) {
                throw new InvalidRecommendationRequestException(
                        "같은 챔피언이 두 번 이상 들어 있습니다: " + champion.getName().get("ko-KR"));
            }
        }
    }

    private Map<Long, Item> loadItems(CandidateUnion union, RecommendationQuery query) {
        List<Long> itemIds = new ArrayList<>(union.candidates().stream()
                .map(candidate -> candidate.itemId())
                .toList());
        itemIds.addAll(query.purchasedItemIds());
        return itemService.findItemsByIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getItemId, Function.identity(), (first, second) -> first));
    }

    private List<Champion> resolveChampions(List<ChampionDto> champions) {
        return champions.stream()
                .map(champion -> championService.findChampionByName(champion.name()))
                .toList();
    }

    private List<Long> championIdsOf(List<Champion> champions) {
        return champions.stream()
                .map(Champion::getChampionId)
                .toList();
    }
}
