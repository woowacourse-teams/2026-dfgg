package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dfgg.application.champion.ChampionService;
import dfgg.application.item.ItemService;
import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.GeneratorResult;
import dfgg.application.recommend.v3.HardValidityFilter;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.ScoredItem;
import dfgg.application.recommend.v3.ranker.CandidateRanker;
import dfgg.common.NextItemRecommendationNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemExclusionGroups;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NextItemRecommendationServiceTest {

    private static final long KRAKEN = 6673L;
    private static final long INFINITY_EDGE = 3031L;
    private static final long LIANDRY = 6653L;

    private ChampionService championService;
    private ItemService itemService;
    private CandidateGenerator buildGenerator;
    private CandidateRanker candidateRanker;
    private NextItemRecommendationService service;

    @BeforeEach
    void setUp() {
        championService = mock(ChampionService.class);
        itemService = mock(ItemService.class);
        buildGenerator = mock(CandidateGenerator.class);
        candidateRanker = mock(CandidateRanker.class);

        when(championService.findChampionByName(any())).thenAnswer(invocation -> {
            Champion champion = mock(Champion.class);
            when(champion.getChampionId()).thenReturn(championIdOf(invocation.getArgument(0)));
            return champion;
        });
        when(buildGenerator.source()).thenReturn(CandidateSource.BUILD);
        when(candidateRanker.modelVersion()).thenReturn("test-ranker");

        service = new NextItemRecommendationService(
                championService, itemService, List.of(buildGenerator),
                new HardValidityFilter(new ItemExclusionGroups()), candidateRanker, 20, 20, 20, 30
        );
    }

    private long championIdOf(String name) {
        return switch (name) {
            case "야스오" -> 157L;
            case "징크스" -> 222L;
            case "쓰레쉬" -> 412L;
            case "리신" -> 64L;
            case "오른" -> 516L;
            case "람머스" -> 33L;
            case "아리" -> 103L;
            case "케이틀린" -> 51L;
            case "레오나" -> 89L;
            default -> 60L;
        };
    }

    private NextItemRecommendationRequest request() {
        return new NextItemRecommendationRequest(
                new ChampionDto("야스오", "MID"), List.of(),
                List.of(new ChampionDto("징크스", "BOTTOM"), new ChampionDto("쓰레쉬", "SUPPORT"),
                        new ChampionDto("리신", "JUNGLE"), new ChampionDto("오른", "TOP")),
                List.of(new ChampionDto("람머스", "TOP"), new ChampionDto("아리", "MID"),
                        new ChampionDto("케이틀린", "BOTTOM"), new ChampionDto("레오나", "SUPPORT"),
                        new ChampionDto("엘리스", "JUNGLE")),
                "EMERALD", "16.17"
        );
    }

    private void givenCandidates(long... itemIds) {
        List<ScoredItem> scored = java.util.stream.LongStream.of(itemIds)
                .mapToObj(id -> new ScoredItem(id, 0.5))
                .toList();
        when(buildGenerator.generate(any(RecommendationQuery.class), anyInt()))
                .thenReturn(GeneratorResult.of(CandidateSource.BUILD, scored));
        when(itemService.findItemsByIds(any())).thenReturn(
                java.util.stream.LongStream.of(itemIds)
                        .mapToObj(id -> new Item(id, "아이템" + id, List.of()))
                        .toList()
        );
    }

    @Test
    @DisplayName("최종 순서는 랭커가 정한 그대로다 — 서비스가 다시 정렬하지 않는다")
    void recommendNextItem_WhenRankerReturnsOrder_PreservesItExactly() {
        // given: 랭커가 generator 점수 순서와 다른 순서를 돌려줘도 그대로 따라야 한다
        givenCandidates(KRAKEN, INFINITY_EDGE, LIANDRY);
        when(candidateRanker.rank(any(CandidateUnion.class), any(RecommendationQuery.class), anyInt()))
                .thenReturn(List.of(LIANDRY, KRAKEN, INFINITY_EDGE));

        // when
        NextItemRecommendationResponse response = service.recommendNextItem(request());

        // then
        assertThat(response.recommendedItems()).extracting(item -> item.id())
                .containsExactly(LIANDRY, KRAKEN, INFINITY_EDGE);
    }

    @Test
    @DisplayName("어떤 랭커가 순위를 냈는지 응답에 담는다")
    void recommendNextItem_WhenServed_ReportsRankerModelVersion() {
        // given
        givenCandidates(KRAKEN);
        when(candidateRanker.rank(any(), any(), anyInt())).thenReturn(List.of(KRAKEN));

        // when
        NextItemRecommendationResponse response = service.recommendNextItem(request());

        // then
        assertThat(response.servedBy()).isEqualTo("test-ranker");
    }

    @Test
    @DisplayName("후보가 하나도 없으면 기존과 같이 NotFound를 던진다 — 404 계약을 유지한다")
    void recommendNextItem_WhenNoCandidates_ThrowsNotFound() {
        // given
        when(buildGenerator.generate(any(RecommendationQuery.class), anyInt()))
                .thenReturn(GeneratorResult.of(CandidateSource.BUILD, List.of()));
        when(itemService.findItemsByIds(any())).thenReturn(List.of());
        when(candidateRanker.rank(any(), any(), anyInt())).thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> service.recommendNextItem(request()))
                .isInstanceOf(NextItemRecommendationNotFoundException.class);
    }
}
