package dfgg.application.match;

import dfgg.infrastructure.external.client.RiotClient;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집할 플레이어의 매치 ID를 찾고 Raw Match와 Raw Timeline 수집 순서를 조율한다.
 * 정규화와 통계 집계는 이 서비스의 책임이 아니며
 * {@link dfgg.application.RiotCollectionOrchestrator}가 후속 단계로 호출한다.
 */
@Service
public class RiotMatchSyncService {

    private final RiotClient riotClient;
    private final RawMatchService rawMatchService;
    private final RawMatchTimelineService rawMatchTimelineService;

    public RiotMatchSyncService(
            RiotClient riotClient,
            RawMatchService rawMatchService,
            RawMatchTimelineService rawMatchTimelineService
    ) {
        this.riotClient = riotClient;
        this.rawMatchService = rawMatchService;
        this.rawMatchTimelineService = rawMatchTimelineService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void syncMatches(
            List<String> puuids,
            int matchStart,
            int matchCount
    ) {
        List<String> distinctPuuids = new ArrayList<>(new LinkedHashSet<>(puuids));
        if (distinctPuuids.isEmpty()) {
            return;
        }

        RuntimeException firstFailure = null;
        LinkedHashSet<String> matchIds = new LinkedHashSet<>();
        for (String puuid : distinctPuuids) {
            try {
                matchIds.addAll(findMatchIds(puuid, matchStart, matchCount));
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                }
            }
        }

        if (!matchIds.isEmpty()) {
            Set<String> existingMatchIds = rawMatchService.findExistingMatchIds(matchIds);
            Set<String> existingTimelineIds = rawMatchTimelineService.findExistingMatchIds(matchIds);
            for (String matchId : matchIds) {
                try {
                    collectMatch(
                            matchId,
                            existingMatchIds.contains(matchId),
                            existingTimelineIds.contains(matchId)
                    );
                } catch (RuntimeException exception) {
                    if (firstFailure == null) {
                        firstFailure = exception;
                    }
                }
            }
        }

        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    /**
     * 한 플레이어의 매치 ID를 조회한다.
     *
     * <p>정상 스케줄링 경로에서는 반환된 매치 ID를 하나씩 원본 수집과 정규화 단계로
     * 넘겨, 한 매치의 처리가 끝난 뒤 다음 매치를 조회할 수 있도록 사용한다.
     */
    public List<String> findMatchIds(String puuid, int matchStart, int matchCount) {
        return List.copyOf(riotClient.getMatchIds(puuid, matchStart, matchCount));
    }

    /**
     * 한 매치의 Raw Match와 Timeline을 순서대로 수집한다.
     * 이미 저장된 원본은 건너뛰고, 하나라도 새로 준비했으면 true를 반환한다.
     * 수집에 실패하면 예외를 그대로 전달해 호출자가 해당 매치의 후속 단계를 건너뛸 수 있게 한다.
     */
    public boolean syncMatch(String matchId) {
        boolean matchAlreadyPersisted = rawMatchService.findExistingMatchIds(Set.of(matchId))
                .contains(matchId);
        boolean timelineAlreadyPersisted = rawMatchTimelineService.findExistingMatchIds(Set.of(matchId))
                .contains(matchId);

        collectMatch(matchId, matchAlreadyPersisted, timelineAlreadyPersisted);
        return !matchAlreadyPersisted || !timelineAlreadyPersisted;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void syncMissingTimelines() {
        rawMatchTimelineService.collectMissingTimelines();
    }

    private void collectMatch(
            String matchId,
            boolean matchAlreadyPersisted,
            boolean timelineAlreadyPersisted
    ) {
        // Timeline보다 Match 원본을 먼저 저장한다. Match 수집이 실패하면 이 매치의 Timeline은 수집하지 않는다.
        if (!matchAlreadyPersisted) {
            rawMatchService.collectRawMatch(matchId);
        }
        if (!timelineAlreadyPersisted) {
            rawMatchTimelineService.collectRawMatchTimeline(matchId);
        }
    }
}
