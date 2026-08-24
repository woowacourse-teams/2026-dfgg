package dfgg.application;

import dfgg.application.match.MatchNormalizationService;
import dfgg.application.match.RiotMatchSyncService;
import dfgg.application.player.RiotPlayerSyncService;
import dfgg.application.stats.ChampionBuildStatsMatchService;
import dfgg.domain.match.NormalizedMatch;
import dfgg.infrastructure.config.RiotSchedulerProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Riot 데이터 자동 수집의 전체 순서만 조율한다.
 * 플레이어를 조회한 뒤 매치별로 원본 수집 → 정규화 → 통계 집계를 이어서 실행하고,
 * 이전 실행에서 남은 미완료 데이터는 마지막에 보완한다.
 */
@Service
public class RiotCollectionOrchestrator {

    private static final String QUEUE_TYPE = "RANKED_SOLO_5x5";
    private static final List<String> DIVISION_ORDER = List.of("IV", "III", "II", "I");

    private final RiotSchedulerProperties properties;
    private final RiotPlayerSyncService playerSyncService;
    private final RiotMatchSyncService matchSyncService;
    private final MatchNormalizationService matchNormalizationService;
    private final ChampionBuildStatsMatchService statsMatchService;
    private int nextLeaguePage;
    private int nextDivisionIndex;
    private boolean currentLeagueRangeHasPlayers;

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
        this.nextLeaguePage = 1;
        this.nextDivisionIndex = 0;
        this.currentLeagueRangeHasPlayers = false;
    }

    public void runOnce() {
        try {
            validateProperties();
        } catch (RuntimeException ignored) {
            return;
        }

        List<String> collectedPuuids = collectPlayers();
        collectMatches(collectedPuuids);
        // 이번 실행에서 실패했거나 이전 실행에 남은 Timeline을 보완한다.
        collectMissingTimelines();
        // 매치 단위 처리로 끝내지 못한 기존 미완료 매치를 복구한다.
        normalizePendingMatches(properties.getTiers());
    }

    private List<String> collectPlayers() {
        boolean completed = true;
        LinkedHashSet<String> collectedPuuids = new LinkedHashSet<>();
        String division = currentDivision();
        for (String tier : properties.getTiers()) {
            int pageEnd = nextLeaguePage + properties.getLeaguePageCount();
            for (int page = nextLeaguePage; page < pageEnd; page++) {
                try {
                    RiotPlayerSyncService.SyncResult syncResult = playerSyncService.syncLeagueEntries(
                            QUEUE_TYPE, tier, division, page
                    );
                    collectedPuuids.addAll(syncResult.puuids());
                } catch (RuntimeException ignored) {
                    completed = false;
                }
            }
        }
        currentLeagueRangeHasPlayers |= !collectedPuuids.isEmpty();
        if (completed) {
            moveToNextLeagueRange();
        }
        return List.copyOf(collectedPuuids);
    }

    private void moveToNextLeagueRange() {
        nextDivisionIndex++;
        if (nextDivisionIndex >= progressiveDivisions().size()) {
            nextDivisionIndex = 0;
            if (currentLeagueRangeHasPlayers) {
                nextLeaguePage += properties.getLeaguePageCount();
            } else {
                nextLeaguePage = 1;
            }
            currentLeagueRangeHasPlayers = false;
        }
    }

    private String currentDivision() {
        List<String> divisions = progressiveDivisions();
        return divisions.get(Math.min(nextDivisionIndex, divisions.size() - 1));
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

    private void collectMatches(List<String> puuids) {
        Set<String> processedMatchIds = new LinkedHashSet<>();
        int playerCount = properties.getPlayerPageSize();
        for (int fromIndex = 0; fromIndex < puuids.size(); fromIndex += playerCount) {
            List<String> targets = puuids.subList(
                    fromIndex,
                    Math.min(fromIndex + playerCount, puuids.size())
            );
            for (String puuid : targets) {
                collectPlayerMatches(puuid, processedMatchIds);
            }
        }
    }

    /**
     * 한 플레이어의 매치 ID를 조회하고, 각 매치를 원본 수집부터 통계 집계까지 처리한다.
     * 매치 ID 조회가 실패해도 다른 플레이어의 수집은 계속한다.
     */
    private void collectPlayerMatches(String puuid, Set<String> processedMatchIds) {
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
                processMatch(matchId);
            }
        }
    }

    /**
     * 한 매치를 Raw Match → Timeline → 정규화 → 통계 순서로 처리한다.
     * 원본 수집에 실패한 매치는 정규화하지 않고 다음 실행의 복구 대상으로 남긴다.
     */
    private void processMatch(String matchId) {
        boolean collected;
        try {
            collected = matchSyncService.syncMatch(matchId);
        } catch (RuntimeException ignored) {
            return;
        }

        // 이미 원본이 모두 있던 매치는 마지막 복구 단계에서 미정규화 여부를 확인한다.
        if (!collected) {
            return;
        }

        try {
            NormalizedMatch normalized = matchNormalizationService.normalize(matchId);
            matchNormalizationService.save(normalized);
            aggregateStats(normalized, properties.getTiers(), new ArrayList<>());
        } catch (RuntimeException ignored) {
            // 실패한 매치는 Raw 데이터와 함께 남겨 다음 실행의 복구 단계에서 다시 처리한다.
        }
    }

    private void collectMissingTimelines() {
        try {
            matchSyncService.syncMissingTimelines();
        } catch (RuntimeException ignored) {
            // 누락 Timeline 보완이 실패해도 준비된 매치의 정규화는 계속한다.
        }
    }

    /**
     * 저장된 Raw Match와 Raw Timeline 중 아직 처리하지 않은 매치를 정규화하고 바로 통계를 집계한다.
     * 관리자 API에서도 스케줄러와 같은 정상 처리 흐름을 재사용할 수 있도록 공개한다.
     *
     * @throws IllegalStateException 한 건이라도 정규화하거나 집계하지 못한 경우
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void normalizeAndAggregatePendingMatches(String tier) {
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        List<Failure> failures = normalizePendingMatches(List.of(tier));
        if (!failures.isEmpty()) {
            Failure firstFailure = failures.getFirst();
            throw new IllegalStateException(
                    "failed to normalize or aggregate " + failures.size() + " match(es): "
                            + firstFailure.targetId + " - " + firstFailure.reason
            );
        }
    }

    private synchronized List<Failure> normalizePendingMatches(List<String> tiers) {
        List<Failure> failures = new ArrayList<>();
        // 스케줄러와 관리자 API가 동시에 호출되어도 한 프로세스에서는 정규화를 한 번씩만 실행한다.
        try {
            normalizeMatches(failures, tiers);
        } catch (RuntimeException exception) {
            // 대상 페이지 조회 자체가 실패하면 다음 실행에서 같은 범위를 다시 시도한다.
            failures.add(Failure.from("all-pending", exception));
        }
        return List.copyOf(failures);
    }

    private void normalizeMatches(List<Failure> failures, List<String> tiers) {
        String cursor = "";

        while (true) {
            // Raw Match와 Timeline이 모두 있고, 아직 정규화 데이터가 없는 매치만 페이지 단위로 읽는다.
            List<String> matchIds = matchNormalizationService.findPendingMatchIdsAfter(cursor);
            if (matchIds.isEmpty()) {
                return;
            }

            for (String matchId : matchIds) {
                NormalizedMatch normalized;
                try {
                    // 외부 티어 조회와 변환을 먼저 끝낸 뒤 저장만 짧은 트랜잭션으로 실행한다.
                    // 두 호출 모두 다른 Spring Bean을 통하므로 save()의 @Transactional이 정상 적용된다.
                    normalized = matchNormalizationService.normalize(matchId);
                    matchNormalizationService.save(normalized);
                } catch (RuntimeException exception) {
                    // 한 매치가 실패해도 다음 매치를 계속 처리한다. 실패한 매치는 다음 실행에서 다시 조회된다.
                    failures.add(Failure.from(matchId, exception));
                    continue;
                }
                aggregateStats(normalized, tiers, failures);
            }
            cursor = matchIds.getLast();
        }
    }

    private void aggregateStats(NormalizedMatch normalized, List<String> tiers, List<Failure> failures) {
        // 저장이 끝난 정규화 객체를 그대로 전달해 정상 경로에서는 DB에서 다시 조립하지 않는다.
        for (String tier : tiers) {
            try {
                statsMatchService.registerMatchStats(normalized, tier);
            } catch (RuntimeException exception) {
                failures.add(Failure.from(
                        normalized.matchId() + "/" + tier,
                        exception
                ));
            }
        }
    }

    private void validateProperties() {
        if (properties.getTiers().isEmpty()) {
            throw new IllegalArgumentException("collection scheduler tiers must not be empty");
        }
        if (properties.getDivisions().isEmpty()) {
            throw new IllegalArgumentException("collection scheduler divisions must not be empty");
        }
        if (properties.getDivisions().stream().anyMatch(division -> !DIVISION_ORDER.contains(division))) {
            throw new IllegalArgumentException("collection scheduler divisions must be one of IV, III, II, I");
        }
        if (properties.getLeaguePageCount() < 1) {
            throw new IllegalArgumentException("collection scheduler league page count must be positive");
        }
        if (properties.getPlayerPageSize() < 1 || properties.getPlayerPageSize() > 100) {
            throw new IllegalArgumentException("collection scheduler player page size must be between 1 and 100");
        }
        if (properties.getMatchCount() < 1 || properties.getMatchCount() > 100) {
            throw new IllegalArgumentException("collection scheduler match count must be between 1 and 100");
        }
    }

    private record Failure(String targetId, String reason) {

        private static Failure from(String targetId, RuntimeException exception) {
            String type = exception.getClass().getSimpleName();
            String message = exception.getMessage();
            return new Failure(
                    targetId,
                    message == null || message.isBlank() ? type : type + ": " + message
            );
        }
    }
}
