package dfgg.domain.aram;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** 한 챔피언의 증강을 등급별로 묶고 등급 안에서 픽률이 높은 순으로 순위를 매긴다. 등급 사이의 픽률은 비교하지 않는다. */
public final class ChampionAugmentRanking {

    private static final Comparator<ChampionAugment> HIGHEST_PICK_RATE_FIRST =
            Comparator.comparingDouble(ChampionAugment::pickRate).reversed()
                    .thenComparingInt(ChampionAugment::augmentId);

    private final List<RankedAugment> augments;

    private ChampionAugmentRanking(List<RankedAugment> augments) {
        this.augments = augments;
    }

    public static ChampionAugmentRanking of(List<Augment> catalog, List<ChampionAugment> picks) {
        Map<Integer, Augment> augmentsById = catalog.stream()
                .collect(Collectors.toMap(Augment::augmentId, Function.identity()));
        List<RankedAugment> ranked = Arrays.stream(AugmentRarity.values())
                .flatMap(rarity -> rankWithin(rarity, picks, augmentsById).stream())
                .toList();
        return new ChampionAugmentRanking(ranked);
    }

    private static List<RankedAugment> rankWithin(AugmentRarity rarity, List<ChampionAugment> picks,
                                                  Map<Integer, Augment> augmentsById) {
        List<ChampionAugment> sorted = picks.stream()
                .filter(pick -> augmentsById.get(pick.augmentId()).rarity() == rarity)
                .sorted(HIGHEST_PICK_RATE_FIRST)
                .toList();
        return IntStream.range(0, sorted.size())
                .mapToObj(index -> {
                    ChampionAugment pick = sorted.get(index);
                    return new RankedAugment(augmentsById.get(pick.augmentId()), index + 1, pick.pickRate());
                })
                .toList();
    }

    public List<RankedAugment> augments() {
        return augments;
    }
}
