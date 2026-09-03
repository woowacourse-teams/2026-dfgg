package dfgg.application.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.application.item.ItemService;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class MatchRenormalizationServiceTest {

    private static final String TIER = "PLATINUM";

    private RawMatchRepository rawMatchRepository;
    private RawMatchTimelineRepository timelineRepository;
    private MatchNormalizationService normalizationService;
    private ItemService itemService;
    private MatchRenormalizationService service;

    @BeforeEach
    void setUp() {
        rawMatchRepository = mock(RawMatchRepository.class);
        timelineRepository = mock(RawMatchTimelineRepository.class);
        normalizationService = mock(MatchNormalizationService.class);
        itemService = mock(ItemService.class);
        service = new MatchRenormalizationService(
                rawMatchRepository, timelineRepository, normalizationService, itemService);

        when(itemService.findCoreItemIds()).thenReturn(Set.of(3031));
        when(rawMatchRepository.findById(anyString()))
                .thenAnswer(i -> Optional.of(new RawMatch(i.getArgument(0), "{}")));
        when(timelineRepository.findById(anyString()))
                .thenAnswer(i -> Optional.of(new RawMatchTimeline(i.getArgument(0), "{}")));
        when(normalizationService.normalizeAsTierSample(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(mock(NormalizedMatch.class));
    }

    private void givenTargets(String... matchIds) {
        when(rawMatchRepository.findNormalizedMatchIdsForRenormalizationAfter(
                anyString(), eq(TIER), any(Pageable.class)))
                .thenReturn(List.of(matchIds));
    }

    @Test
    @DisplayName("대상 매치를 하나씩 재정규화한다")
    void renormalize_WhenTargetsExist_ReplaysEachOne() {
        // given
        givenTargets("M1", "M2", "M3");

        // when
        RenormalizationResult result = service.renormalize(TIER, "", 10);

        // then
        verify(normalizationService, org.mockito.Mockito.times(3))
                .save(any(NormalizedMatch.class));
        assertThat(result.succeeded()).isEqualTo(3);
        assertThat(result.failed()).isZero();
    }

    @Test
    @DisplayName("다음 커서를 돌려준다 — 중단하고 이어서 돌릴 수 있어야 한다")
    void renormalize_WhenFinished_ReturnsNextCursor() {
        // given
        givenTargets("M1", "M2", "M3");

        // when
        RenormalizationResult result = service.renormalize(TIER, "", 10);

        // then
        assertThat(result.nextCursor()).isEqualTo("M3");
    }

    @Test
    @DisplayName("한 매치가 실패해도 나머지를 계속 처리한다 — 86k를 도는 중 하나 때문에 멈추면 안 된다")
    void renormalize_WhenOneMatchFails_ContinuesWithTheRest() {
        // given
        givenTargets("M1", "M2", "M3");
        when(timelineRepository.findById("M2")).thenReturn(Optional.empty());

        // when
        RenormalizationResult result = service.renormalize(TIER, "", 10);

        // then
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패한 매치 ID를 남긴다 — 무엇이 왜 실패했는지 봐야 다음 판단을 한다")
    void renormalize_WhenFailuresOccur_ReportsThem() {
        // given
        givenTargets("M1", "M2");
        when(timelineRepository.findById("M2")).thenReturn(Optional.empty());

        // when
        RenormalizationResult result = service.renormalize(TIER, "", 10);

        // then
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0)).contains("M2");
    }

    @Test
    @DisplayName("실패해도 커서는 진행한다 — 같은 매치에서 무한히 멈추지 않는다")
    void renormalize_WhenLastMatchFails_StillAdvancesCursor() {
        // given
        givenTargets("M1", "M2");
        when(timelineRepository.findById("M2")).thenReturn(Optional.empty());

        // when
        RenormalizationResult result = service.renormalize(TIER, "", 10);

        // then
        assertThat(result.nextCursor()).isEqualTo("M2");
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 하지 않고 커서를 그대로 둔다")
    void renormalize_WhenNoTargets_DoesNothing() {
        // given
        givenTargets();

        // when
        RenormalizationResult result = service.renormalize(TIER, "KR_9999", 10);

        // then
        verify(normalizationService, never()).save(any(NormalizedMatch.class));
        assertThat(result.processed()).isZero();
        assertThat(result.nextCursor()).isEqualTo("KR_9999");
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    @DisplayName("아이템 목록을 배치당 한 번만 조회한다 — 매치마다 다시 읽으면 7만 번이 된다")
    void renormalize_WhenManyMatches_LoadsCoreItemsOncePerBatch() {
        // given
        givenTargets("M1", "M2", "M3");

        // when
        service.renormalize(TIER, "", 10);

        // then
        verify(itemService, org.mockito.Mockito.times(1)).findCoreItemIds();
    }

    @Test
    @DisplayName("limit만큼 채워서 돌아오면 더 남았다고 알린다")
    void renormalize_WhenBatchIsFull_ReportsHasMore() {
        // given: limit 3을 요청했는데 3건이 왔다
        givenTargets("M1", "M2", "M3");

        // when
        RenormalizationResult result = service.renormalize(TIER, "", 3);

        // then
        assertThat(result.hasMore()).isTrue();
    }

    @Test
    @DisplayName("limit보다 적게 오면 마지막 배치다")
    void renormalize_WhenBatchIsNotFull_ReportsNoMore() {
        // given
        givenTargets("M1", "M2");

        // when
        RenormalizationResult result = service.renormalize(TIER, "", 10);

        // then
        assertThat(result.hasMore()).isFalse();
    }
}
