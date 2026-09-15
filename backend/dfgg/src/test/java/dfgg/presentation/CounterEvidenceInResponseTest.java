package dfgg.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.ItemMetaStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * counter 근거가 실제로 응답의 단어가 되는지 본다.
 * <p>
 * {@code v3-recommendation-test-data.sql}만으로는 적 pair 표본이 없어 counter generator가 base rate로 백오프한다.
 * 그러면 지목할 적이 없어 {@code counter}가 늘 비고, 테스트가 헛돈다.
 * 그래서 챔피언·아이템을 주는 v3 픽스처 위에 pair 통계를 주는 counter 픽스처를 겹친다.
 * <p>
 * 픽스처 구성: 야스오가 람머스를 상대로 도미닉 경의 인사를 평소보다 많이 산다(lift 1.74). 무한의 대검은 반대로 덜 산다(lift 0.39) — 이유로 대면 안 된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql({"/sql/v3-recommendation-test-data.sql", "/sql/counter-test-data.sql"})
class CounterEvidenceInResponseTest {

    private static final long RAMMUS = 33L;

    @Autowired
    private NextItemRecommendationService recommendationService;
    @Autowired
    private ItemStatsAggregationService aggregationService;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;
    @Autowired
    private ChampionRepository championRepository;
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

    @BeforeEach
    void setUp() {
        aggregationService.aggregate(3);
    }

    /**
     * {@code @SpringBootTest}는 롤백하지 않는다. 커밋된 채 남으면 다른 테스트가 깨진다.
     */
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

    private NextItemRecommendationResponse recommendAgainstRammus() {
        return recommendationService.recommendNextItem(new NextItemRecommendationRequest(
                new ChampionDto("야스오", "MID"),
                List.of(),
                List.of(new ChampionDto("아리", "BOTTOM"), new ChampionDto("징크스", "SUPPORT")),
                List.of(new ChampionDto("람머스", "TOP")),
                "EMERALD", "16.17"));
    }

    @Test
    @DisplayName("counter 근거가 있으면 적 이름이 응답에 나온다")
    void recommend_WhenCounterEvidenceExists_NamesTheEnemy() {
        NextItemRecommendationResponse response = recommendAgainstRammus();

        assertThat(response.recommendedItems())
                .as("어떤 추천에도 counter 근거가 붙지 않았다면 경로가 죽어 있는 것이다")
                .anySatisfy(item -> assertThat(item.description().counter()).isNotEmpty());
    }

    @Test
    @DisplayName("지목한 적은 질의에 있던 람머스뿐이고 한글 이름이 붙는다")
    void recommend_NamesOnlyTheQueriedEnemyWithKoreanName() {
        NextItemRecommendationResponse response = recommendAgainstRammus();

        assertThat(response.recommendedItems())
                .flatMap(RecommendedItemDto::description)
                .isNotNull();
        response.recommendedItems().forEach(item ->
                assertThat(item.description().counter()).allSatisfy(champion -> {
                    assertThat(champion.id()).isEqualTo(RAMMUS);
                    assertThat(champion.name()).isEqualTo("람머스");
                }));
    }

    @Test
    @DisplayName("lift가 1 이하인 적은 이유로 대지 않는다 — 평소보다 덜 사는 아이템이다")
    void recommend_DoesNotNameEnemiesBelowNeutralLift() {
        // 무한의 대검(3031)은 람머스 상대 lift가 0.39다. 추천에 올라오더라도 람머스를
        // 이유로 대서는 안 된다.
        NextItemRecommendationResponse response = recommendAgainstRammus();

        assertThat(response.recommendedItems())
                .filteredOn(item -> item.id() == 3031L)
                .allSatisfy(item -> assertThat(item.description().counter()).isEmpty());
    }

    @Test
    @DisplayName("counter는 두 명을 넘지 않는다")
    void recommend_CapsCounterAtTwo() {
        assertThat(recommendAgainstRammus().recommendedItems())
                .allSatisfy(item ->
                        assertThat(item.description().counter()).hasSizeLessThanOrEqualTo(2));
    }
}
