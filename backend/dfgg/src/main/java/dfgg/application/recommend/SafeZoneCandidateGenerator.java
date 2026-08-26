package dfgg.application.recommend;

import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.sequence.MinedSequentialPattern;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 사용자가 이미 산 아이템(prefix) 바로 뒤에 정확히 하나 더 이어지는 마이닝된 패턴을
 * Wilson 신뢰구간 하한 순으로 랭킹한 "80% 안전 구역" 다음 아이템 후보를 만든다.
 *
 * <p>PrefixSpan은 gap을 허용하므로(사이에 다른 아이템이 껴도 지지도를 인정) "다음 아이템"이
 * 아니라 "언젠가 뒤에 사는 아이템"까지 섞일 수 있다. 이 클래스는 그 애매함을 없애는 대신,
 * items 리스트의 앞부분이 구매한 prefix와 정확히 일치하고 길이가 정확히 하나만 더 긴 패턴만
 * 걸러서 "정확히 이 상태 다음"을 의미하도록 좁힌다.
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

    public List<RankedSequentialPattern> rankNextItemCandidates(
            List<Long> purchasedItemIds,
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
                .filter(pattern -> extendsExactPrefix(pattern, purchasedItemIds))
                .map(pattern -> new RankedSequentialPattern(
                        pattern,
                        wilsonScoreCalculator.lowerBound(pattern.getWinCount(), pattern.getSupportCount())
                ))
                .sorted(Comparator.comparingDouble(RankedSequentialPattern::wilsonLowerBound).reversed())
                .toList();
    }

    private boolean extendsExactPrefix(MinedSequentialPattern pattern, List<Long> purchasedItemIds) {
        List<Long> items = pattern.getItems();
        int prefixLength = purchasedItemIds.size();
        return items.size() == prefixLength + 1
                && items.subList(0, prefixLength).equals(purchasedItemIds);
    }
}
