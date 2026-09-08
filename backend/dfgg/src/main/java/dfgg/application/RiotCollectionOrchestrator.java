package dfgg.application;

import dfgg.application.match.MatchNormalizationService;
import dfgg.application.match.RiotMatchSyncService;
import dfgg.application.player.RiotPlayerSyncService;
import dfgg.application.stats.ChampionBuildStatsMatchService;
import dfgg.domain.match.NormalizedMatch;
import dfgg.infrastructure.config.RiotSchedulerProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Riot 데이터 자동 수집의 전체 순서만 조율한다. 플레이어를 조회한 뒤 새로 수집한 매치별로 원본 수집 → 정규화 → 통계 집계를 이어서 실행한다. 이전 실행에서 남은 미완료 데이터의 정규화와 집계는 관리자
 * API가 담당한다.
 */
@Service
public class RiotCollectionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RiotCollectionOrchestrator.class);
    private static final String QUEUE_TYPE = "RANKED_SOLO_5x5";
    private static final String MASTER_TIER = "MASTER";
    private static final String GRANDMASTER_TIER = "GRANDMASTER";
    private static final String CHALLENGER_TIER = "CHALLENGER";
    private static final Set<String> APEX_TIERS = Set.of(MASTER_TIER, GRANDMASTER_TIER, CHALLENGER_TIER);
    private static final Set<String> SUPPORTED_TIERS = Set.of(
            "IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND",
            MASTER_TIER, GRANDMASTER_TIER, CHALLENGER_TIER
    );
    private static final List<String> DIVISION_ORDER = List.of("IV", "III", "II", "I");

    private final RiotSchedulerProperties properties;
    private final RiotPlayerSyncService playerSyncService;
    private final RiotMatchSyncService matchSyncService;
    private final MatchNormalizationService matchNormalizationService;
    private final ChampionBuildStatsMatchService statsMatchService;
    private final Map<String, Integer> nextLeaguePages;
    private final Map<String, Integer> nextDivisionIndexes;
    private final Map<String, Boolean> leagueRangeHasPlayersByTier;
    private final Map<String, Integer> nextApexPlayerIndexes;
    private int nextTierIndex;

    public RiotCollectionOrchestrator(
            RiotSchedulerProperties properties,
            RiotPlayerSyncService playerSyncService,
            RiotMatchSyncService matchSyncService,
            MatchNormalizationService matchNormalizationService,
            ChampionBuildStatsMatchService statsMatchService
    ) {
        this.properties = properties;
        this.playerSyncService = playerSyncService;
        this.matchSyncService = matchSyncService;
        this.matchNormalizationService = matchNormalizationService;
        this.statsMatchService = statsMatchService;
        this.nextLeaguePages = new HashMap<>();
        this.nextDivisionIndexes = new HashMap<>();
        this.leagueRangeHasPlayersByTier = new HashMap<>();
        this.nextApexPlayerIndexes = new HashMap<>();
        this.nextTierIndex = 0;
    }

    public void runOnce() {
        try {
            validateProperties();
        } catch (RuntimeException exception) {
            log.error("Riot 수집 스케줄러 설정이 올바르지 않습니다.", exception);
            return;
        }

        String sampleTier = properties.getTiers().get(nextTierIndex);

        try {
            List<String> collectedPuuids = collectPlayers(sampleTier);
            collectMatches(collectedPuuids, sampleTier);

            if (properties.isRecoverMissingTimelines()) {
                collectMissingTimelines();
            }
        } finally {
            moveToNextTier();
        }
    }

    private List<String> collectPlayers(String tier) {
        if (APEX_TIERS.contains(tier)) {
            return collectApexPlayers(tier);
        }

        boolean completed = true;
        LinkedHashSet<String> collectedPuuids = new LinkedHashSet<>();
        String division = currentDivision(tier);
        int nextLeaguePage = nextLeaguePages.getOrDefault(tier, 1);
        int pageEnd = nextLeaguePage + properties.getLeaguePageCount();
        for (int page = nextLeaguePage; page < pageEnd; page++) {
            boolean pageCollected = collectLeaguePage(tier, division, page, collectedPuuids);
            completed = completed && pageCollected;
        }
        recordLeaguePlayersFound(tier, collectedPuuids);
        if (completed) {
            moveToNextLeagueRange(tier);
        }
        return List.copyOf(collectedPuuids);
    }

    private boolean collectLeaguePage(String tier, String division, int page, Set<String> collectedPuuids) {
        try {
            RiotPlayerSyncService.SyncResult result = playerSyncService.syncLeagueEntries(QUEUE_TYPE, tier, division,
                    page);
            collectedPuuids.addAll(result.puuids());
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void recordLeaguePlayersFound(String tier, Set<String> collectedPuuids) {
        if (collectedPuuids.isEmpty()) {
            return;
        }
        leagueRangeHasPlayersByTier.put(tier, true);
    }

    /**
     * 마스터·그랜드마스터·챌린저 중 지정한 리그의 플레이어 정보를 동기화하고, 이번 실행에서 매치를 수집할 플레이어의 PUUID를 최대 playerLimit명만큼 반환한다.
     *
     * <p>상위 리그는 디비전과 페이지 구분이 없으므로 전체 명단을 조회한다.
     * PUUID를 중복 제거 후 정렬하고, 티어별로 기억한 위치부터 순환 선택한다. 명단 끝에 도달하면 처음으로 돌아가며, 선택 직후 다음 위치를 저장한다.
     *
     * <p>리그 조회 또는 플레이어 동기화에 실패하면 위치를 유지하고 빈 목록을 반환한다.
     * 조회된 명단이 비어 있으면 해당 티어의 위치를 0으로 초기화한다.
     */
    private List<String> collectApexPlayers(String tier) {
        RiotPlayerSyncService.SyncResult syncResult;
        try {
            // 최상위 리그 API에는 division과 page가 없으며 서비스가 이 두 인자를 사용하지 않는다.
            syncResult = playerSyncService.syncLeagueEntries(QUEUE_TYPE, tier, "I", 1);
        } catch (RuntimeException ignored) {
            return List.of();
        }

        List<String> puuids = syncResult.puuids().stream()
                .distinct()
                .sorted()
                .toList();
        if (puuids.isEmpty()) {
            nextApexPlayerIndexes.put(tier, 0);
            return List.of();
        }
        int nextPlayerIndex = nextApexPlayerIndexes.getOrDefault(tier, 0);
        int start = Math.floorMod(nextPlayerIndex, puuids.size());
        int count = Math.min(properties.getPlayerLimit(), puuids.size());
        List<String> selected = IntStream.range(0, count)
                .mapToObj(offset -> puuids.get((start + offset) % puuids.size()))
                .toList();
        nextApexPlayerIndexes.put(tier, (start + count) % puuids.size());
        return selected;
    }

    private void moveToNextLeagueRange(String tier) {
        int nextDivisionIndex = nextDivisionIndexes.getOrDefault(tier, 0) + 1;
        if (nextDivisionIndex >= progressiveDivisions().size()) {
            nextDivisionIndex = 0;
            moveToNextLeaguePage(tier);
        }
        nextDivisionIndexes.put(tier, nextDivisionIndex);
    }

    private void moveToNextLeaguePage(String tier) {
        boolean hasPlayers = leagueRangeHasPlayersByTier.getOrDefault(tier, false);
        leagueRangeHasPlayersByTier.put(tier, false);

        if (!hasPlayers) {
            nextLeaguePages.put(tier, 1);
            return;
        }

        int nextLeaguePage = nextLeaguePages.getOrDefault(tier, 1);
        nextLeaguePages.put(tier, nextLeaguePage + properties.getLeaguePageCount());
    }

    private void moveToNextTier() {
        nextTierIndex = (nextTierIndex + 1) % properties.getTiers().size();
    }

    private String currentDivision(String tier) {
        List<String> divisions = progressiveDivisions();
        int divisionIndex = nextDivisionIndexes.getOrDefault(tier, 0);
        return divisions.get(Math.min(divisionIndex, divisions.size() - 1));
    }

    private List<String> progressiveDivisions() {
        List<String> configured = properties.getDivisions();
        if (configured.size() != 1) {
            return configured;
        }
        int startIndex = DIVISION_ORDER.indexOf(configured.getFirst());
        if (startIndex < 0) {
            return configured;
        }
        return DIVISION_ORDER.subList(startIndex, DIVISION_ORDER.size());
    }

    private void collectMatches(List<String> puuids, String sampleTier) {
        Set<String> processedMatchIds = new LinkedHashSet<>();
        int playerCount = properties.getPlayerPageSize();
        List<String> limitedPuuids = puuids.stream()
                .limit(properties.getPlayerLimit())
                .toList();
        for (int fromIndex = 0; fromIndex < limitedPuuids.size(); fromIndex += playerCount) {
            List<String> targets = limitedPuuids.subList(
                    fromIndex,
                    Math.min(fromIndex + playerCount, limitedPuuids.size())
            );
            for (String puuid : targets) {
                collectPlayerMatches(puuid, sampleTier, processedMatchIds);
            }
        }
    }

    /**
     * 한 플레이어의 매치 ID를 조회하고, 각 매치를 원본 수집부터 통계 집계까지 처리한다. 매치 ID 조회가 실패해도 다른 플레이어의 수집은 계속한다.
     */
    private void collectPlayerMatches(String puuid, String sampleTier, Set<String> processedMatchIds) {
        List<String> matchIds;
        try {
            matchIds = matchSyncService.findMatchIds(
                    puuid,
                    0,
                    properties.getMatchCount()
            );
        } catch (RuntimeException ignored) {
            return;
        }

        for (String matchId : matchIds) {
            // 여러 플레이어가 같은 매치를 조회할 수 있으므로 한 스케줄 실행 안에서는 한 번만 처리한다.
            if (processedMatchIds.add(matchId)) {
                processMatch(matchId, sampleTier);
            }
        }
    }

    /**
     * 한 매치를 Raw Match → Timeline → 정규화 → 통계 순서로 처리한다. 원본 수집에 실패한 매치는 정규화하지 않고 다음 실행의 복구 대상으로 남긴다.
     */
    private void processMatch(String matchId, String sampleTier) {
        boolean collected;
        try {
            collected = matchSyncService.syncMatch(matchId);
        } catch (RuntimeException ignored) {
            return;
        }

        // 이미 원본이 모두 있던 매치는 자동 재처리하지 않고 관리자 재집계 대상으로 남긴다.
        if (!collected) {
            return;
        }

        try {
            NormalizedMatch normalized = matchNormalizationService.normalizeAsTierSample(matchId, sampleTier);
            matchNormalizationService.save(normalized);
            aggregateStats(normalized, List.of(sampleTier), new ArrayList<>());
        } catch (RuntimeException exception) {
            // 자동 재시도하지 않는다. 운영자가 수집을 중단한 뒤 관리자 재집계 API로 복구한다.
            log.error("매치 정규화 또는 통계 집계 실패: matchId={}", matchId, exception);
        }
    }

    private void collectMissingTimelines() {
        try {
            matchSyncService.syncMissingTimelines();
        } catch (RuntimeException ignored) {
            // 누락 Timeline 보완이 실패해도 수집 실행 자체는 종료한다.
        }
    }

    /**
     * 저장된 Raw Match와 Raw Timeline 중 아직 처리하지 않은 매치를 정규화하고 바로 통계를 집계한다. 관리자 API에서도 스케줄러와 같은 정상 처리 흐름을 재사용할 수 있도록 공개한다.
     *
     * @throws IllegalStateException 한 건이라도 정규화하거나 집계하지 못한 경우
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void normalizeAndAggregatePendingMatches(String tier) {
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        // 관리자 재집계도 전달받은 티어를 매치 전체의 표본 티어로 사용한다.
        List<Failure> failures = normalizePendingMatches(
                List.of(tier),
                matchId -> matchNormalizationService.normalizeAsTierSample(matchId, tier),
                true
        );
        if (!failures.isEmpty()) {
            Failure firstFailure = failures.getFirst();
            throw new IllegalStateException(
                    "failed to normalize or aggregate " + failures.size() + " match(es): "
                            + firstFailure.targetId + " - " + firstFailure.reason,
                    firstFailure.cause
            );
        }
    }

    private synchronized List<Failure> normalizePendingMatches(
            List<String> tiers,
            Function<String, NormalizedMatch> normalizer,
            boolean retryFailedMatches
    ) {
        List<Failure> failures = new ArrayList<>();
        // 스케줄러와 관리자 API가 동시에 호출되어도 한 프로세스에서는 정규화를 한 번씩만 실행한다.
        try {
            normalizeMatches(failures, tiers, normalizer, retryFailedMatches);
        } catch (RuntimeException exception) {
            // 대상 페이지 조회 자체가 실패하면 다음 실행에서 같은 범위를 다시 시도한다.
            log.error("미처리 매치 조회 실패", exception);
            failures.add(Failure.from("all-pending", exception));
        }
        return List.copyOf(failures);
    }

    private void normalizeMatches(
            List<Failure> failures,
            List<String> tiers,
            Function<String, NormalizedMatch> normalizer,
            boolean retryFailedMatches
    ) {
        String cursor = "";
        Set<String> retryMatchIds = new LinkedHashSet<>();
        List<StatsRetry> statsRetries = new ArrayList<>();

        while (true) {
            // Raw Match와 Timeline이 모두 있고, 아직 정규화 데이터가 없는 매치만 페이지 단위로 읽는다.
            List<String> matchIds = matchNormalizationService.findPendingMatchIdsAfter(cursor);
            if (matchIds.isEmpty()) {
                break;
            }

            for (String matchId : matchIds) {
                NormalizedMatch normalized;
                try {
                    normalized = normalizer.apply(matchId);
                    matchNormalizationService.save(normalized);
                } catch (RuntimeException exception) {
                    if (retryFailedMatches && !isRateLimitFailure(exception)) {
                        log.warn("매치 정규화 실패: matchId={}", matchId, exception);
                        retryMatchIds.add(matchId);
                    } else {
                        log.error("매치 정규화 실패: matchId={}", matchId, exception);
                        failures.add(Failure.from(matchId, exception));
                    }
                    continue;
                }
                aggregateStats(
                        normalized,
                        tiers,
                        failures,
                        statsRetries,
                        retryFailedMatches
                );
            }
            cursor = matchIds.getLast();
        }

        retryFailedMatches(retryMatchIds, failures, tiers, normalizer, statsRetries);
        retryFailedStats(statsRetries, failures);
    }

    private boolean isRateLimitFailure(RuntimeException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof HttpClientErrorException clientError
                    && clientError.getStatusCode().value() == 429) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void retryFailedMatches(
            Set<String> retryMatchIds,
            List<Failure> failures,
            List<String> tiers,
            Function<String, NormalizedMatch> normalizer,
            List<StatsRetry> statsRetries
    ) {
        for (String matchId : retryMatchIds) {
            try {
                NormalizedMatch normalized = normalizer.apply(matchId);
                matchNormalizationService.save(normalized);
                aggregateStats(normalized, tiers, failures, statsRetries, true);
            } catch (RuntimeException exception) {
                log.error("매치 정규화 재시도 실패: matchId={}", matchId, exception);
                failures.add(Failure.from(matchId, exception));
            }
        }
    }

    private void retryFailedStats(List<StatsRetry> statsRetries, List<Failure> failures) {
        for (StatsRetry retry : statsRetries) {
            try {
                statsMatchService.registerMatchStats(retry.normalized, retry.tier);
            } catch (RuntimeException exception) {
                log.error(
                        "매치 통계 집계 재시도 실패: matchId={}, tier={}",
                        retry.normalized.matchId(),
                        retry.tier,
                        exception
                );
                failures.add(Failure.from(
                        retry.normalized.matchId() + "/" + retry.tier,
                        exception
                ));
            }
        }
    }

    private void aggregateStats(NormalizedMatch normalized, List<String> tiers, List<Failure> failures) {
        aggregateStats(normalized, tiers, failures, new ArrayList<>(), false);
    }

    private void aggregateStats(
            NormalizedMatch normalized,
            List<String> tiers,
            List<Failure> failures,
            List<StatsRetry> statsRetries,
            boolean queueStatsRetries
    ) {
        // 저장이 끝난 정규화 객체를 그대로 전달해 정상 경로에서는 DB에서 다시 조립하지 않는다.
        for (String tier : tiers) {
            try {
                statsMatchService.registerMatchStats(normalized, tier);
            } catch (RuntimeException exception) {
                if (queueStatsRetries) {
                    log.warn(
                            "매치 통계 집계 실패: matchId={}, tier={}",
                            normalized.matchId(),
                            tier,
                            exception
                    );
                    statsRetries.add(new StatsRetry(normalized, tier));
                } else {
                    log.error(
                            "매치 통계 집계 실패: matchId={}, tier={}",
                            normalized.matchId(),
                            tier,
                            exception
                    );
                    failures.add(Failure.from(
                            normalized.matchId() + "/" + tier,
                            exception
                    ));
                }
            }
        }
    }

    private void validateProperties() {
        validateTiers();
        validateLeagueSettings();
        validateCollectionLimits();
    }

    private void validateCollectionLimits() {
        if (properties.getPlayerPageSize() < 1 || properties.getPlayerPageSize() > 100) {
            throw new IllegalArgumentException("플레이어 처리 페이지 크기는 1 이상 100 이하여야 합니다.");
        }
        if (properties.getPlayerLimit() < 1 || properties.getPlayerLimit() > 100) {
            throw new IllegalArgumentException("한 번에 수집할 플레이어 수는 1 이상 100 이하여야 합니다.");
        }
        if (properties.getMatchCount() < 1 || properties.getMatchCount() > 100) {
            throw new IllegalArgumentException("플레이어당 조회할 매치 수는 1 이상 100 이하여야 합니다.");
        }
    }

    private void validateLeagueSettings() {
        boolean onlyApexTiers = properties.getTiers().stream()
                .allMatch(APEX_TIERS::contains);
        if (onlyApexTiers) {
            return;
        }
        List<String> divisions = properties.getDivisions();

        if (divisions.isEmpty()) {
            throw new IllegalArgumentException("수집 대상 디비전은 하나 이상 설정해야 합니다.");
        }
        if (!DIVISION_ORDER.containsAll(divisions)) {
            throw new IllegalArgumentException("수집 대상 디비전은 IV, III, II, I 중에서 설정해야 합니다.");
        }
        if (properties.getLeaguePageCount() < 1) {
            throw new IllegalArgumentException("한 번에 조회할 리그 페이지 수는 1 이상이어야 합니다.");
        }

    }

    private void validateTiers() {
        List<String> tiers = properties.getTiers();

        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("수집 대상 티어는 하나 이상 설정해야 합니다.");
        }
        if (!SUPPORTED_TIERS.containsAll(tiers)) {
            throw new IllegalArgumentException("수집 대상에 지원하지 않는 티어가 포함되어 있습니다.");
        }
    }

    private record Failure(String targetId, String reason, RuntimeException cause) {

        private static Failure from(String targetId, RuntimeException exception) {
            String type = exception.getClass().getSimpleName();
            String message = exception.getMessage();
            return new Failure(
                    targetId,
                    message == null || message.isBlank() ? type : type + ": " + message,
                    exception
            );
        }
    }

    private record StatsRetry(NormalizedMatch normalized, String tier) {
    }
}
