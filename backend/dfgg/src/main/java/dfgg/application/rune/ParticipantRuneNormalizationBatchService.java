package dfgg.application.rune;

import dfgg.domain.match.RawMatchRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** 이미 저장된 경기의 룬을 패치 단위로 나눠 채운다. 경기 하나가 실패해도 다음 경기로 넘어간다. */
@Service
public class ParticipantRuneNormalizationBatchService {

    private static final Logger log = LoggerFactory.getLogger(ParticipantRuneNormalizationBatchService.class);

    private final RawMatchRepository rawMatchRepository;
    private final ParticipantRuneNormalizationService normalizationService;

    public ParticipantRuneNormalizationBatchService(RawMatchRepository rawMatchRepository,
                                                    ParticipantRuneNormalizationService normalizationService) {
        this.rawMatchRepository = rawMatchRepository;
        this.normalizationService = normalizationService;
    }

    /**
     * 지정한 패치의 경기만 커서 이후부터 limit개 정규화한다.
     * 응답의 nextCursor를 afterMatchId로 넘겨 hasMore가 false가 될 때까지 반복한다.
     */
    public RuneNormalizationBatchResult normalizePatch(String patch, String afterMatchId, int limit) {
        RuneNormalizationSummary summary = new RuneNormalizationSummary();
        List<String> matchIds = rawMatchRepository.findMatchIdsForPatchAfter(
                afterMatchId, patch, PageRequest.of(0, limit));
        matchIds.forEach(matchId -> normalizeMatch(matchId, summary));
        RuneNormalizationBatchResult result = summary.toBatchResult(matchIds.size(),
                nextCursor(matchIds, afterMatchId), matchIds.size() == limit);
        log.info("패치 룬 정규화 배치 완료: patch={}, processed={}, failed={}, nextCursor={}",
                patch, result.processed(), result.failed(), result.nextCursor());
        return result;
    }

    private void normalizeMatch(String matchId, RuneNormalizationSummary summary) {
        try {
            summary.succeed(normalizationService.normalize(matchId));
        } catch (RuntimeException exception) {
            summary.fail(matchId, String.valueOf(exception.getMessage()));
            log.warn("룬 정규화 실패: {}: {}", matchId, exception.getMessage(), exception);
        }
    }

    /** 실패한 경기가 있어도 이번 배치의 마지막 경기 다음부터 이어간다. */
    private String nextCursor(List<String> matchIds, String afterMatchId) {
        if (matchIds.isEmpty()) {
            return afterMatchId;
        }
        return matchIds.getLast();
    }
}
