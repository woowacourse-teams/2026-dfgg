package dfgg.application.recommend;

import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.sequence.MinedSequentialPattern;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * PrefixSpan으로 마이닝된 패턴을 Wilson 신뢰구간 하한 순으로 랭킹한 "80% 안전 구역" 후보를 만든다.
 */
@Component
public class SafeZoneCandidateGenerator {

    private final MinedSequentialPatternRepository patternRepository;
    private final WilsonScoreCalculator wilsonScoreCalculator;

    public SafeZoneCandidateGenerator(
            MinedSequentialPatternRepository patternRepository,
            WilsonScoreCalculator wilsonScoreCalculator
    ) {
        this.patternRepository = patternRepository;
        this.wilsonScoreCalculator = wilsonScoreCalculator;
    }

    public List<RankedSequentialPattern> rankByWilsonScore(
            Long championId,
            ChampionPosition position,
            String tier,
            String patch,
            String algorithmVersion
    ) {
        List<MinedSequentialPattern> patterns = patternRepository
                .findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
                        algorithmVersion, championId, position, tier, patch
                );

        return patterns.stream()
                .map(pattern -> new RankedSequentialPattern(
                        pattern,
                        wilsonScoreCalculator.lowerBound(pattern.getWinCount(), pattern.getSupportCount())
                ))
                .sorted(Comparator.comparingDouble(RankedSequentialPattern::wilsonLowerBound).reversed())
                .toList();
    }
}
