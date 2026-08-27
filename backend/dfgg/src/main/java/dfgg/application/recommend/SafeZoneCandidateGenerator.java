package dfgg.application.recommend;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.sequence.MinedSequentialPattern;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * "80% 안전 구역" 다음 아이템 후보를 만든다. 이미 산 아이템 개수(prefix 길이)에 따라
 * 두 가지 데이터 소스를 쓴다:
 *
 * <ul>
 *     <li>prefix 길이가 {@code anchoredPrefixLimit} 미만(기본 1~2코어) — 실제 구매 순서에
 *     정확히 anchoring된 {@code normalized_match_participants} 원본 집계. 1~2코어는
 *     챔피언 정체성과 강하게 묶여있어 정확도가 중요하다.</li>
 *     <li>그 이상(기본 3코어~) — gap 허용 PrefixSpan 마이닝 결과({@code
 *     mined_sequential_patterns}). 3코어부터는 상황 대응 비중이 커지는 반면 정확히
 *     일치하는 표본이 급격히 줄어들어, 느슨한 매칭으로 더 큰 표본을 확보하는 쪽이 낫다.</li>
 * </ul>
 *
 * <p>실측으로 확인된 이유: 길이 1(anchoring 없는) 패턴은 "빌드 어딘가에 있음"을 세지
 * "1번째로 샀음"을 세지 않아서, 1코어 추천에 실제로는 거의 아무도 1코어로 사지 않는
 * 아이템이 섞여 나오는 결함이 있었다.
 */
@Component
public class SafeZoneCandidateGenerator {

    private final NormalizedMatchParticipantRepository participantRepository;
    private final MinedSequentialPatternRepository patternRepository;
    private final ChampionPositionNormalizer positionNormalizer;
    private final WilsonScoreCalculator wilsonScoreCalculator;

    public SafeZoneCandidateGenerator(
            NormalizedMatchParticipantRepository participantRepository,
            MinedSequentialPatternRepository patternRepository,
            ChampionPositionNormalizer positionNormalizer,
            WilsonScoreCalculator wilsonScoreCalculator
    ) {
        this.participantRepository = participantRepository;
        this.patternRepository = patternRepository;
        this.positionNormalizer = positionNormalizer;
        this.wilsonScoreCalculator = wilsonScoreCalculator;
    }

    public List<RankedItemCandidate> rankNextItemCandidates(
            List<Long> purchasedItemIds,
            Long championId,
            ChampionPosition position,
            String tier,
            String patch,
            String algorithmVersion,
            int anchoredPrefixLimit
    ) {
        if (purchasedItemIds.size() < anchoredPrefixLimit) {
            return rankByActualPurchaseOrder(purchasedItemIds, championId, position, patch);
        }
        return rankByMinedPattern(purchasedItemIds, championId, position, tier, patch, algorithmVersion);
    }

    private List<RankedItemCandidate> rankByActualPurchaseOrder(
            List<Long> purchasedItemIds, Long championId, ChampionPosition position, String patch
    ) {
        List<String> positions = positionNormalizer.riotValuesOf(position);
        String prefix = purchasedItemIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        int nextPosition = purchasedItemIds.size() + 1;

        List<Object[]> rows = participantRepository.findNextItemDistribution(
                championId, positions, patch, prefix, nextPosition
        );

        return rows.stream()
                .map(NextItemRow::from)
                .map(row -> new RankedItemCandidate(
                        row.itemId(),
                        wilsonScoreCalculator.lowerBound(row.winCount(), row.support())
                ))
                .sorted(Comparator.comparingDouble(RankedItemCandidate::score).reversed())
                .toList();
    }

    private List<RankedItemCandidate> rankByMinedPattern(
            List<Long> purchasedItemIds, Long championId, ChampionPosition position, String tier, String patch,
            String algorithmVersion
    ) {
        List<MinedSequentialPattern> patterns = patternRepository
                .findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
                        algorithmVersion, championId, position, tier, patch
                );

        return patterns.stream()
                .filter(pattern -> extendsExactPrefix(pattern, purchasedItemIds))
                .map(pattern -> new RankedItemCandidate(
                        lastItem(pattern.getItems()),
                        wilsonScoreCalculator.lowerBound(pattern.getWinCount(), pattern.getSupportCount())
                ))
                .sorted(Comparator.comparingDouble(RankedItemCandidate::score).reversed())
                .toList();
    }

    private boolean extendsExactPrefix(MinedSequentialPattern pattern, List<Long> purchasedItemIds) {
        List<Long> items = pattern.getItems();
        int prefixLength = purchasedItemIds.size();
        return items.size() == prefixLength + 1
                && items.subList(0, prefixLength).equals(purchasedItemIds);
    }

    private Long lastItem(List<Long> items) {
        return items.get(items.size() - 1);
    }

    private record NextItemRow(Long itemId, int support, int winCount) {

        private static NextItemRow from(Object[] row) {
            return new NextItemRow(
                    Long.valueOf((String) row[0]),
                    ((Number) row[1]).intValue(),
                    ((Number) row[2]).intValue()
            );
        }
    }
}
