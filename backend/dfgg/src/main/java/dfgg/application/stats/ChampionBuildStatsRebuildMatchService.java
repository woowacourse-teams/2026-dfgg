package dfgg.application.stats;

import dfgg.application.item.ItemService;
import dfgg.application.match.MatchNormalizationService;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.stats.CompositionStatsSampleRepository;
import dfgg.domain.stats.StatsAggregationCompletionRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 장애 복구나 백필이 필요할 때 저장된 정규화 데이터에서 통계 미완료 대상을 찾아 다시 처리한다.
 */
@Service
public class ChampionBuildStatsRebuildMatchService {

    private static final String QUEUE_TYPE = "RANKED_SOLO_5x5";
    private static final String AGGREGATION_REVISION = "v1";

    private final RawMatchRepository rawMatchRepository;
    private final RawMatchTimelineRepository rawMatchTimelineRepository;
    private final ItemService itemService;
    private final MatchNormalizationService matchNormalizationService;
    private final ChampionBuildStatsAggregationService aggregationService;
    private final StatsAggregationCompletionRepository completionRepository;
    private final CompositionStatsSampleRepository sampleRepository;
    private final NormalizedMatchParticipantRepository participantRepository;
    private final int batchSize;
    private TransactionTemplate transactionTemplate;

    public ChampionBuildStatsRebuildMatchService(
            RawMatchRepository rawMatchRepository,
            RawMatchTimelineRepository rawMatchTimelineRepository,
            ItemService itemService,
            MatchNormalizationService matchNormalizationService,
            ChampionBuildStatsAggregationService aggregationService,
            StatsAggregationCompletionRepository completionRepository,
            CompositionStatsSampleRepository sampleRepository,
            NormalizedMatchParticipantRepository participantRepository,
            @Value("${stats.rebuild.batch-size:100}") int batchSize
    ) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("stats rebuild batch size must be positive");
        }
        this.rawMatchRepository = rawMatchRepository;
        this.rawMatchTimelineRepository = rawMatchTimelineRepository;
        this.itemService = itemService;
        this.matchNormalizationService = matchNormalizationService;
        this.aggregationService = aggregationService;
        this.completionRepository = completionRepository;
        this.sampleRepository = sampleRepository;
        this.participantRepository = participantRepository;
        this.batchSize = batchSize;
    }

    /**
     * 매치 단위 복구 작업이 completion 선점부터 통계 반영까지 하나의 새 트랜잭션으로 실행되도록 설정한다.
     */
    @Autowired
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        this.transactionTemplate = template;
    }

    /**
     * 완료 기록이 없는 정규화 참가자를 찾아 매치별로 통계를 복구한다.
     * 한 매치의 실패가 전체 처리를 중단하지 않도록 매치별로 시도한 뒤 마지막에 실패를 요약한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void rebuildAll(String tier) {
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        rebuildPendingMatches(tier);
    }

    /**
     * 커서 기반으로 미완료 대상을 반복 조회하고, 같은 매치의 참가자들을 묶어 집계 서비스에 전달한다.
     */
    private void rebuildPendingMatches(String tier) {
        int failedMatches = 0;
        String firstFailedMatchId = null;
        String firstFailureReason = null;
        PendingTarget cursor = PendingTarget.initialCursor();

        while (true) {
            List<PendingTarget> targets = nextPendingTargets(tier, cursor);
            if (targets.isEmpty()) {
                break;
            }
            for (Map.Entry<String, List<String>> entry : groupPuuidsByMatch(targets).entrySet()) {
                String matchId = entry.getKey();
                try {
                    NormalizedMatch normalized = loadNormalizedMatch(matchId);
                    registerPendingMatchStats(
                            normalized,
                            QUEUE_TYPE,
                            tier,
                            entry.getValue(),
                            AGGREGATION_REVISION
                    );
                } catch (RuntimeException exception) {
                    failedMatches++;
                    if (firstFailedMatchId == null) {
                        firstFailedMatchId = matchId;
                        firstFailureReason = failureReason(exception);
                    }
                }
            }
            cursor = targets.getLast();
        }

        if (failedMatches > 0) {
            throw new IllegalStateException(
                    "failed to aggregate " + failedMatches + " match(es): "
                            + firstFailedMatchId + " - " + firstFailureReason
            );
        }
    }

    /**
     * 저장된 정규화 참가자 행을 participantId 순서로 읽어 집계에 사용할 매치 객체로 복원한다.
     */
    private NormalizedMatch loadNormalizedMatch(String matchId) {
        // 팀 조합 계산에는 집계 대상뿐 아니라 같은 매치의 전체 참가자 정보가 필요하다.
        List<NormalizedMatchParticipant> rows = participantRepository.findByMatchId(matchId).stream()
                .sorted(Comparator.comparing(NormalizedMatchParticipant::getParticipantId))
                .toList();
        if (rows.isEmpty()) {
            throw new IllegalStateException("normalized match not found: " + matchId);
        }

        NormalizedMatchParticipant first = rows.getFirst();
        return new NormalizedMatch(
                first.getMatchId(),
                first.getPatch(),
                first.getQueueId(),
                rows
        );
    }

    /**
     * 원본 매치와 Timeline을 다시 정규화해 지정한 티어의 기존 통계 기여를 교체한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void replayOne(String matchId, String tier) {
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        List<String> participantPuuids = participantRepository.findPuuidsByMatchIdAndTier(
                matchId,
                tier
        );
        if (participantPuuids.isEmpty()) {
            throw new IllegalArgumentException("normalized participants not found: " + matchId + "/" + tier);
        }
        Set<Integer> coreItemIds = itemService.findCoreItemIds();
        RawMatch rawMatch = rawMatchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("raw match not found: " + matchId));
        RawMatchTimeline timeline = rawMatchTimelineRepository.findById(matchId)
                .orElseThrow(() -> new IllegalStateException("raw match timeline not found: " + matchId));
        replaceMatchStatsAsTierSample(
                rawMatch,
                timeline,
                QUEUE_TYPE,
                tier,
                participantPuuids,
                coreItemIds,
                AGGREGATION_REVISION
        );
    }

    /**
     * 저장된 정규화 매치의 미완료 참가자만 선점해 통계를 등록한다.
     */
    public void registerPendingMatchStats(
            NormalizedMatch normalized,
            String queueType,
            String tier,
            Collection<String> participantPuuids,
            String aggregationRevision
    ) {
        runInNewTransaction(() -> registerPendingMatchStatsInternal(
                normalized,
                queueType,
                tier,
                participantPuuids,
                aggregationRevision
        ));
    }

    /**
     * 미완료 참가자 선점과 통계 등록을 현재 트랜잭션 안에서 수행한다.
     */
    private void registerPendingMatchStatsInternal(
            NormalizedMatch normalized,
            String queueType,
            String tier,
            Collection<String> participantPuuids,
            String aggregationRevision
    ) {
        validateScope(queueType, tier, participantPuuids, aggregationRevision);

        // 참가자별 completion 유니크 제약으로 재집계 대상의 중복 선점을 막는다.
        List<String> claimedPuuids = claimPendingPuuids(
                normalized.matchId(),
                participantPuuids,
                queueType,
                tier,
                aggregationRevision
        );
        if (claimedPuuids.isEmpty()) {
            return;
        }

        aggregationService.aggregate(normalized, tier, claimedPuuids);
    }

    /**
     * 원본 매치에서 정규화 데이터를 다시 만들고 기존 기여를 제거한 뒤 새 통계를 반영한다.
     */
    public void replaceMatchStats(
            RawMatch rawMatch,
            RawMatchTimeline timeline,
            String queueType,
            String tier,
            Collection<String> participantPuuids,
            Set<Integer> coreItemIds,
            String aggregationRevision
    ) {
        runInNewTransaction(() -> replaceMatchStatsInternal(
                rawMatch,
                timeline,
                queueType,
                tier,
                participantPuuids,
                coreItemIds,
                aggregationRevision,
                false
        ));
    }

    /**
     * 매치 전체를 지정한 표본 티어로 다시 정규화하고 모든 참가자의 통계 기여를 교체한다.
     */
    private void replaceMatchStatsAsTierSample(
            RawMatch rawMatch,
            RawMatchTimeline timeline,
            String queueType,
            String tier,
            Collection<String> participantPuuids,
            Set<Integer> coreItemIds,
            String aggregationRevision
    ) {
        runInNewTransaction(() -> replaceMatchStatsInternal(
                rawMatch,
                timeline,
                queueType,
                tier,
                participantPuuids,
                coreItemIds,
                aggregationRevision,
                true
        ));
    }

    /**
     * 기존 기여 제거부터 새 통계 반영까지를 하나의 매치 작업으로 수행한다.
     */
    private void replaceMatchStatsInternal(
            RawMatch rawMatch,
            RawMatchTimeline timeline,
            String queueType,
            String tier,
            Collection<String> participantPuuids,
            Set<Integer> coreItemIds,
            String aggregationRevision,
            boolean useTierSample
    ) {
        validateScope(queueType, tier, participantPuuids, aggregationRevision);

        List<String> requestedPuuids = participantPuuids.stream().distinct().sorted().toList();
        if (requestedPuuids.isEmpty()) {
            throw new IllegalArgumentException("participantPuuids must not be empty");
        }
        // 관리자 replay 동시성 제어와 매치 단위 트랜잭션은 추후 고도화 시 고려한다.
        NormalizedMatch normalized = useTierSample
                ? matchNormalizationService.normalizeAsTierSample(
                        rawMatch.getMatchId(),
                        rawMatch.getRawData(),
                        timeline.getRawData(),
                        coreItemIds,
                        tier
                )
                : matchNormalizationService.normalize(
                        rawMatch.getMatchId(),
                        rawMatch.getRawData(),
                        timeline.getRawData(),
                        coreItemIds
                );
        ensureParticipantsExist(normalized, requestedPuuids);

        List<String> targetPuuids = useTierSample
                ? normalized.participants().stream()
                        .map(NormalizedMatchParticipant::puuid)
                        .distinct()
                        .sorted()
                        .toList()
                : requestedPuuids;

        // 기존 기여를 삭제한 뒤 정규화 데이터와 새 통계를 반영한다.
        for (String puuid : targetPuuids) {
            removePreviousContribution(rawMatch.getMatchId(), puuid);
        }
        matchNormalizationService.save(normalized);
        aggregationService.aggregate(normalized, tier, targetPuuids);
        for (String puuid : targetPuuids) {
            completionRepository.markCompleted(
                    rawMatch.getMatchId(),
                    puuid,
                    queueType,
                    tier,
                    aggregationRevision
            );
        }
    }

    /**
     * Spring 컨테이너에서 실행될 때는 매치별 새 트랜잭션을 열고, 단위 테스트에서는 주입된 작업을 그대로 실행한다.
     */
    private void runInNewTransaction(Runnable action) {
        if (transactionTemplate == null) {
            action.run();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    /**
     * 지정된 참가자 중 completion 행을 새로 만든 참가자만 선점 성공 목록으로 반환한다.
     */
    private List<String> claimPendingPuuids(
            String matchId,
            Collection<String> participantPuuids,
            String queueType,
            String tier,
            String aggregationRevision
    ) {
        List<String> claimedPuuids = new ArrayList<>();
        for (String puuid : participantPuuids.stream().distinct().sorted().toList()) {
            int inserted = completionRepository.insertIfAbsent(
                    matchId,
                    puuid,
                    queueType,
                    tier,
                    aggregationRevision
            );
            if (inserted == 1) {
                claimedPuuids.add(puuid);
            }
        }
        return List.copyOf(claimedPuuids);
    }

    /**
     * 집계 대상 범위를 구성하는 필수 문자열과 참가자 목록의 기본 조건을 검증한다.
     */
    private void validateScope(
            String queueType,
            String tier,
            Collection<String> participantPuuids,
            String aggregationRevision
    ) {
        if (queueType == null || queueType.isBlank()) {
            throw new IllegalArgumentException("queueType must not be blank");
        }
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        if (aggregationRevision == null || aggregationRevision.isBlank()) {
            throw new IllegalArgumentException("aggregationRevision must not be blank");
        }
    }

    /**
     * 재집계 대상 참가자가 새로 정규화한 매치에도 존재하는지 확인한다.
     */
    private void ensureParticipantsExist(NormalizedMatch normalized, List<String> targetPuuids) {
        Set<String> normalizedPuuids = normalized.participants().stream()
                .map(NormalizedMatchParticipant::puuid)
                .collect(Collectors.toSet());
        List<String> missingPuuids = targetPuuids.stream()
                .filter(puuid -> !normalizedPuuids.contains(puuid))
                .toList();
        if (!missingPuuids.isEmpty()) {
            throw new IllegalStateException("participants not found in normalized match: " + missingPuuids);
        }
    }

    /**
     * 참가자의 기존 표본을 삭제하고 관련 통계의 게임 수와 승리 수를 함께 되돌린다.
     */
    private void removePreviousContribution(String matchId, String puuid) {
        sampleRepository.backfillMissingWinFromNormalized(matchId, puuid);
        long missingWinCount = sampleRepository.countByMatchIdAndPuuidAndWinIsNull(matchId, puuid);
        if (missingWinCount > 0) {
            throw new IllegalStateException(
                    "cannot replay stats because previous win contributions are unknown: "
                            + matchId + "/" + puuid + " (" + missingWinCount + ")"
            );
        }

        long contributionCount = sampleRepository.countByMatchIdAndPuuid(matchId, puuid);
        int decrementedStats = sampleRepository.deleteContributionsAndDecrement(matchId, puuid);
        if (decrementedStats != contributionCount) {
            throw new IllegalStateException(
                    "cannot replay stats because stored counts do not match samples: "
                            + matchId + "/" + puuid
            );
        }
    }

    /**
     * 마지막으로 처리한 매치와 PUUID 이후의 미완료 대상을 조회한다.
     * 배치 마지막 매치의 나머지 참가자는 같은 매치로 묶기 위해 추가 조회한다.
     */
    private List<PendingTarget> nextPendingTargets(String tier, PendingTarget cursor) {
        List<PendingTarget> targets = completionRepository.findPendingTargetsAfter(
                        QUEUE_TYPE,
                        tier,
                        AGGREGATION_REVISION,
                        cursor.matchId(),
                        cursor.puuid(),
                        batchSize
                ).stream()
                .map(PendingTarget::from)
                .collect(Collectors.toCollection(ArrayList::new));
        if (targets.isEmpty()) {
            return List.of();
        }

        // 배치 경계에 걸린 마지막 매치는 참가자가 나뉘지 않도록 나머지 대상도 함께 조회한다.
        PendingTarget lastTarget = targets.getLast();
        completionRepository.findRemainingTargetsForMatch(
                        lastTarget.matchId(),
                        QUEUE_TYPE,
                        tier,
                        AGGREGATION_REVISION,
                        lastTarget.puuid()
                ).stream()
                .map(PendingTarget::from)
                .forEach(targets::add);
        return List.copyOf(targets);
    }

    /**
     * 조회 결과를 매치 ID별 참가자 목록으로 묶어 한 매치씩 처리할 수 있게 한다.
     */
    private Map<String, List<String>> groupPuuidsByMatch(List<PendingTarget> targets) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (PendingTarget target : targets) {
            grouped.computeIfAbsent(target.matchId(), ignored -> new ArrayList<>())
                    .add(target.puuid());
        }
        return grouped;
    }

    /**
     * 예외 타입과 메시지를 복구 실패 요약에 사용할 문자열로 변환한다.
     */
    private String failureReason(RuntimeException exception) {
        String type = exception.getClass().getSimpleName();
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return type;
        }
        return type + ": " + exception.getMessage();
    }

    private record PendingTarget(String matchId, String puuid) {

        /**
         * 커서 기반 조회를 시작할 때 사용할 가장 작은 위치를 만든다.
         */
        private static PendingTarget initialCursor() {
            return new PendingTarget("", "");
        }

        /**
         * 저장소 조회 결과를 서비스 내부 커서 객체로 변환한다.
         */
        private static PendingTarget from(StatsAggregationCompletionRepository.PendingTarget target) {
            return new PendingTarget(target.getMatchId(), target.getPuuid());
        }
    }
}
