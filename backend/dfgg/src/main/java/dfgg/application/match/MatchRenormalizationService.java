package dfgg.application.match;

import dfgg.application.item.ItemService;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>이미 정규화된</b> 매치를 원본에서 다시 정규화한다.
 *
 * <p>정규화 결과({@code normalized_match_participants})는 한 번 저장되면 스스로 갱신되지 않는다.
 * 정규화 로직을 고쳐도 기존 데이터는 옛 결과를 유지하므로, 반영하려면 이 경로가 필요하다.
 * 실제 사례: {@code CoreItemPurchaseOrderCalculator}가 서포터 퀘스트 아이템을 처리하지 못해
 * 서포터의 90%가 버려지고 있었고, 계산기를 고쳐도 기존 86,108매치는 그대로였다.
 * <p>
 * 재정규화에 더해 참가자별 통계 기여 제거·재집계·완료 표시까지 수행하는데 필요한 두 단계
 * (정규화 → 저장)만 남겼다. 통계까지 다시 만들어야 하면 기존 replay 엔드포인트를 쓰면 된다.
 * <p>
 * Riot API를 호출하지 않는다 — 티어 표본 경로는 참가자별 실제 티어를 조회하지 않는다.
 */
@Service
public class MatchRenormalizationService {

    private static final Logger log = LoggerFactory.getLogger(MatchRenormalizationService.class);

    private final RawMatchRepository rawMatchRepository;
    private final RawMatchTimelineRepository timelineRepository;
    private final MatchNormalizationService normalizationService;
    private final ItemService itemService;

    public MatchRenormalizationService(
            RawMatchRepository rawMatchRepository,
            RawMatchTimelineRepository timelineRepository,
            MatchNormalizationService normalizationService,
            ItemService itemService
    ) {
        this.rawMatchRepository = rawMatchRepository;
        this.timelineRepository = timelineRepository;
        this.normalizationService = normalizationService;
        this.itemService = itemService;
    }

    public RenormalizationResult renormalize(String tier, String cursor, int limit) {
        List<String> matchIds = rawMatchRepository.findNormalizedMatchIdsForRenormalizationAfter(
                cursor, tier, PageRequest.of(0, limit));
        if (matchIds.isEmpty()) {
            return new RenormalizationResult(0, 0, 0, cursor, false, List.of());
        }

        // 아이템 목록은 배치당 한 번만 읽는다. 매치마다 다시 읽으면 7만 번이 된다.
        Set<Integer> coreItemIds = itemService.findCoreItemIds();

        int succeeded = 0;
        List<String> failures = new ArrayList<>();
        for (String matchId : matchIds) {
            try {
                renormalizeOne(matchId, tier, coreItemIds);
                succeeded++;
            } catch (RuntimeException exception) {
                // 한 매치의 실패로 배치를 멈추지 않는다. 7만 건을 도는 중이라면
                // 원본이 깨진 매치 하나 때문에 나머지를 못 고치는 쪽이 더 나쁘다.
                log.warn("매치 재정규화 실패: matchId={}, tier={}", matchId, tier, exception);
                failures.add(matchId + ": " + exception.getMessage());
            }
        }

        String nextCursor = matchIds.get(matchIds.size() - 1);
        boolean hasMore = matchIds.size() == limit;
        log.info(
                "Renormalization batch completed: tier={}, processed={}, succeeded={}, failed={}, nextCursor={}",
                tier, matchIds.size(), succeeded, failures.size(), nextCursor
        );
        return new RenormalizationResult(
                matchIds.size(), succeeded, failures.size(), nextCursor, hasMore, failures);
    }

    /**
     * 매치 하나를 새 트랜잭션에서 정규화하고 저장한다. 배치 전체를 한 트랜잭션으로 묶으면
     * 중간 실패 시 성공한 것까지 되돌아가고, 운영 DB에 긴 락을 걸게 된다.
     */
    @Transactional
    public void renormalizeOne(String matchId, String tier, Set<Integer> coreItemIds) {
        RawMatch rawMatch = rawMatchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("raw match not found: " + matchId));
        RawMatchTimeline timeline = timelineRepository.findById(matchId)
                .orElseThrow(() -> new IllegalStateException("raw match timeline not found: " + matchId));

        NormalizedMatch normalized = normalizationService.normalizeAsTierSample(
                matchId, rawMatch.getRawData(), timeline.getRawData(), coreItemIds, tier);
        normalizationService.save(normalized);
    }
}
