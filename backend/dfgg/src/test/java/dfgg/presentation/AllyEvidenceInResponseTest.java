package dfgg.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.item.trait.ItemTraitCatalog;
import dfgg.domain.item.trait.Synergy;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.ItemMetaStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.ChampionRefDto;
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
 * ally 근거가 실제로 응답의 단어가 되는지 본다.
 * <p>
 * counter와 같은 이유로 픽스처를 겹친다 — v3 픽스처만으로는 아군 pair 표본이 없어
 * base rate로 백오프하고, 그러면 지목할 아군이 없어 테스트가 헛돈다.
 * <p>
 * 픽스처 구성: 잔나가 징크스와 함께면 불타는 향로(10판 중 8판), 코그모와 함께면
 * 월석 재생기(10판 중 9판)를 산다. 같은 챔피언이라도 아군에 따라 사는 게 갈린다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql({"/sql/v3-recommendation-test-data.sql",
        "/sql/ally-synergy-test-data.sql",
        "/sql/ally-evidence-supplement.sql"})
class AllyEvidenceInResponseTest {

    private static final long JINX = 222L;
    private static final long KOGMAW = 96L;
    private static final long ARDENT_CENSER = 3504L;
    private static final long MOONSTONE = 6617L;

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
    @Autowired
    private ItemTraitCatalog itemTraitCatalog;

    @BeforeEach
    void setUp() {
        aggregationService.aggregate(3);
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

    private NextItemRecommendationResponse recommendForJanna() {
        return recommendationService.recommendNextItem(new NextItemRecommendationRequest(
                new ChampionDto("잔나", "SUPPORT"),
                List.of(),
                List.of(new ChampionDto("징크스", "BOTTOM"), new ChampionDto("코그모", "MID")),
                List.of(new ChampionDto("람머스", "TOP")),
                "EMERALD", "16.17"));
    }

    @Test
    @DisplayName("ally 칸이 응답에 존재한다 — 비어 있어도 null이 아니다")
    void recommend_AlwaysCarriesTheAllyField() {
        // when: 채워지는 조건(아이템 synergy·승률 lift 문턱)은 AllyEvidenceTest가 단위로 검증한다.
        NextItemRecommendationResponse response = recommendForJanna();

        // then
        assertThat(response.recommendedItems())
                .allSatisfy(item -> assertThat(item.description().ally()).isNotNull());
    }

    @Test
    @DisplayName("자기에게만 작용하는 아이템에는 아군 이름이 붙지 않는다 — synergy가 SELF면 비운다")
    void recommend_WhenItemIsSelfSynergy_LeavesAllyEmpty() {
        // when
        NextItemRecommendationResponse response = recommendForJanna();

        // then
        response.recommendedItems().stream()
                .filter(item -> itemTraitCatalog.synergyOf(new Item(item.id(), item.name())) == Synergy.SELF)
                .forEach(item -> assertThat(item.description().ally()).as(item.name()).isEmpty());
    }

    @Test
    @DisplayName("지목한 아군은 질의에 있던 아군뿐이다 — 적이나 엉뚱한 챔피언을 지목하지 않는다")
    void recommend_NamesOnlyAlliesFromTheQuery() {
        NextItemRecommendationResponse response = recommendForJanna();

        response.recommendedItems().forEach(item ->
                assertThat(item.description().ally())
                        .extracting(ChampionRefDto::id)
                        .isSubsetOf(List.of(JINX, KOGMAW)));
    }

    @Test
    @DisplayName("아이템마다 원인이 되는 아군이 다르다 — 이 generator의 존재 이유다")
    void recommend_AttributesDifferentAlliesToDifferentItems() {
        // 향로는 징크스와 8/10, 월석은 코그모와 9/10이다.
        NextItemRecommendationResponse response = recommendForJanna();

        response.recommendedItems().stream()
                .filter(item -> item.id() == ARDENT_CENSER && !item.description().ally().isEmpty())
                .forEach(item -> assertThat(item.description().ally())
                        .extracting(ChampionRefDto::id).containsExactly(JINX));
        response.recommendedItems().stream()
                .filter(item -> item.id() == MOONSTONE && !item.description().ally().isEmpty())
                .forEach(item -> assertThat(item.description().ally())
                        .extracting(ChampionRefDto::id).containsExactly(KOGMAW));
    }

    @Test
    @DisplayName("아군에 한글 이름이 붙고 두 명을 넘지 않는다")
    void recommend_AllyCarriesKoreanNameAndIsCappedAtTwo() {
        NextItemRecommendationResponse response = recommendForJanna();

        assertThat(response.recommendedItems()).allSatisfy(item -> {
            assertThat(item.description().ally()).hasSizeLessThanOrEqualTo(2);
            assertThat(item.description().ally())
                    .allSatisfy(ally -> assertThat(ally.name()).isNotBlank());
        });
    }
}
