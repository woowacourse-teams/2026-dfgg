package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.item.ItemService;
import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.explanation.ChampionDirectory;
import dfgg.application.recommend.v3.explanation.ChampionProfile;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.ItemMetaStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * 근거가 generator에서 응답을 만드는 지점까지 살아서 도착하는가.
 * <p>
 * union과 hard filter를 지나야 서비스가 문장을 만드는 자리에 닿는데, 그 구간이 검증돼 있지 않았다.
 * <p>
 * 서비스의 조립 순서를 그대로 재현한다 — generator → union → filter.
 * 이 세 줄이 검증 대상이라 중복이 아니다.
 */
@SpringBootTest
@ActiveProfiles("test")
// v3 픽스처가 챔피언·아이템을, counter 픽스처가 적 pair 통계를 만든다. 뒤쪽이 참가자를
// 지우고 다시 넣으므로 순서가 중요하다. v3 픽스처만으로는 적 pair 표본이 없어 counter가
// base rate로 백오프하고, 그러면 근거가 비어 근거 전달을 확인할 수 없다.
@Sql({"/sql/v3-recommendation-test-data.sql", "/sql/counter-test-data.sql"})
class EvidenceReachesResponseLayerTest {

    private static final long YASUO = 157L;
    private static final long RAMMUS = 33L;
    private static final long AHRI = 103L;
    private static final long JINX = 222L;

    @Autowired
    private ItemStatsAggregationService aggregationService;
    @Autowired
    private List<CandidateGenerator> generators;
    @Autowired
    private HardValidityFilter hardValidityFilter;
    @Autowired
    private CandidateTopK candidateTopK;
    @Autowired
    private ItemService itemService;
    @Autowired
    private ChampionRepository championRepository;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private ChampionItemStatsRepository championItemStatsRepository;
    @Autowired
    private ChampionItemRollupRepository championItemRollupRepository;
    @Autowired
    private ChampionPairItemStatsRepository championPairItemStatsRepository;
    @Autowired
    private ItemMetaStatsRepository itemMetaStatsRepository;

    private ChampionDirectory championDirectory;

    @BeforeEach
    void setUp() {
        aggregationService.aggregate(3);
        championDirectory = new ChampionDirectory(championRepository);
    }

    /** {@code @SpringBootTest}는 롤백하지 않는다. 커밋된 채 남으면 다른 테스트가 깨진다. */
    @AfterEach
    void cleanUp() {
        championItemStatsRepository.deleteAllInBatch();
        championItemRollupRepository.deleteAllInBatch();
        championPairItemStatsRepository.deleteAllInBatch();
        itemMetaStatsRepository.deleteAllInBatch();
        participantRepository.deleteAllInBatch();
        championRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
    }

    /** counter 픽스처의 구성 그대로: 야스오 편에 아리·징크스, 상대는 람머스. */
    private RecommendationQuery query() {
        return new RecommendationQuery(
                YASUO, ChampionPosition.MID, List.of(),
                List.of(AHRI, JINX),
                List.of(RAMMUS),
                "EMERALD", "16.17");
    }

    /** 서비스가 하는 것과 같은 조립: generator → union → hard filter. */
    private CandidateUnion pipelineUnion() {
        RecommendationQuery query = query();
        List<GeneratorResult> results = new ArrayList<>();
        for (CandidateGenerator generator : generators) {
            results.add(generator.generate(query, candidateTopK.of(generator.source())));
        }
        CandidateUnion union = CandidateUnion.merge(results);

        List<Long> itemIds = new ArrayList<>(
                union.candidates().stream().map(ItemCandidate::itemId).toList());
        Map<Long, Item> itemById = itemService.findItemsByIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getItemId, Function.identity(), (first, second) -> first));
        return hardValidityFilter.filter(union, query.purchasedItemIds(), itemById);
    }

    private List<ItemCandidate> withCounterEvidence(CandidateUnion union) {
        return union.candidates().stream()
                .filter(candidate -> candidate.hasSource(CandidateSource.COUNTER))
                .toList();
    }

    @Test
    @DisplayName("counter 후보가 어느 적 때문인지를 필터를 지나서도 들고 있다")
    void pipeline_KeepsCounterAttributionThroughUnionAndFilter() {
        List<ItemCandidate> counterCandidates = withCounterEvidence(pipelineUnion());

        assertThat(counterCandidates).isNotEmpty();
        assertThat(counterCandidates)
                .anySatisfy(candidate -> assertThat(
                        candidate.evidenceOf(CandidateSource.COUNTER).orElseThrow().scoreByChampionId())
                        .isNotEmpty());
    }

    @Test
    @DisplayName("근거로 지목된 챔피언이 질의에 실제로 있던 적이다 — 엉뚱한 챔피언을 지목하지 않는다")
    void pipeline_AttributionNamesOnlyChampionsFromTheQuery() {
        List<Long> enemies = query().enemyChampionIds();

        List<ItemCandidate> counterCandidates = withCounterEvidence(pipelineUnion());

        assertThat(counterCandidates).allSatisfy(candidate ->
                assertThat(candidate.evidenceOf(CandidateSource.COUNTER).orElseThrow()
                        .scoreByChampionId().keySet())
                        .isSubsetOf(enemies));
    }

    @Test
    @DisplayName("근거의 챔피언 ID가 한글 이름으로 해석된다 — 문장에 넣을 수 있는 상태다")
    void pipeline_AttributedChampionIdsResolveToKoreanNames() {
        List<ItemCandidate> counterCandidates = withCounterEvidence(pipelineUnion());
        List<Long> attributed = counterCandidates.stream()
                .flatMap(candidate -> candidate.evidenceOf(CandidateSource.COUNTER).orElseThrow()
                        .scoreByChampionId().keySet().stream())
                .distinct()
                .toList();

        Map<Long, ChampionProfile> profiles = championDirectory.resolve(attributed);

        assertThat(attributed).isNotEmpty();
        assertThat(profiles.keySet()).containsAll(attributed);
        assertThat(profiles.values()).allSatisfy(profile ->
                assertThat(profile.name()).isNotBlank());
    }

    @Test
    @DisplayName("아군 시너지 후보도 어느 아군 때문인지를 들고 있다")
    void pipeline_KeepsAllyAttributionThroughUnionAndFilter() {
        List<ItemCandidate> allyCandidates = pipelineUnion().candidates().stream()
                .filter(candidate -> candidate.hasSource(CandidateSource.ALLY_SYNERGY))
                .toList();

        assertThat(allyCandidates).isNotEmpty();
        assertThat(allyCandidates)
                .anySatisfy(candidate -> assertThat(
                        candidate.evidenceOf(CandidateSource.ALLY_SYNERGY).orElseThrow()
                                .scoreByChampionId())
                        .isNotEmpty());
    }

    @Test
    @DisplayName("상대 개념이 없는 Build 후보는 아무도 지목하지 않는다")
    void pipeline_BuildCandidatesAttributeToNoChampion() {
        assertThat(pipelineUnion().candidates()).allSatisfy(candidate ->
                candidate.evidenceOf(CandidateSource.BUILD).ifPresent(evidence ->
                        assertThat(evidence.scoreByChampionId()).isEmpty()));
    }
}
