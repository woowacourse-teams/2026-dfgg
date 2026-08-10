package dfgg.application;

import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RecommendationBuildComposer {

    public List<Item> compose(List<ChampionBuildStats> matchingStats) {
        List<ChampionBuildStats> mostSpecificPerBuildKey = pickMostSpecificPerBuildKey(matchingStats);
        Map<Integer, Map<Long, SlotCandidate>> slots = accumulateBySlot(mostSpecificPerBuildKey);
        return pickBestPerSlot(slots);
    }

    private List<ChampionBuildStats> pickMostSpecificPerBuildKey(List<ChampionBuildStats> matchingStats) {
        return matchingStats.stream()
                .collect(Collectors.groupingBy(ChampionBuildStats::getBuildKey))
                .values().stream()
                .map(group -> group.stream()
                        .max(Comparator.comparingInt(this::specificity)
                                .thenComparingInt(stats -> orZero(stats.getGameCount())))
                        .orElseThrow())
                .toList();
    }

    private Map<Integer, Map<Long, SlotCandidate>> accumulateBySlot(List<ChampionBuildStats> stats) {
        Map<Integer, Map<Long, SlotCandidate>> slots = new LinkedHashMap<>();
        for (ChampionBuildStats buildStats : stats) {
            List<Item> items = buildStats.getItems();
            int gameCount = orZero(buildStats.getGameCount());
            int winCount = orZero(buildStats.getWinCount());
            for (int slotIndex = 0; slotIndex < items.size(); slotIndex++) {
                Item item = items.get(slotIndex);
                Map<Long, SlotCandidate> slotCandidates = slots.computeIfAbsent(slotIndex, key -> new LinkedHashMap<>());
                slotCandidates.merge(item.getItemId(), new SlotCandidate(item, gameCount, winCount), SlotCandidate::combine);
            }
        }
        return slots;
    }

    private List<Item> pickBestPerSlot(Map<Integer, Map<Long, SlotCandidate>> slots) {
        List<Item> composed = new ArrayList<>();
        Set<Long> chosenItemIds = new LinkedHashSet<>();
        int maxSlotIndex = slots.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int slotIndex = 0; slotIndex <= maxSlotIndex; slotIndex++) {
            Map<Long, SlotCandidate> slotCandidates = slots.getOrDefault(slotIndex, Map.of());
            slotCandidates.values().stream()
                    .filter(candidate -> !chosenItemIds.contains(candidate.item().getItemId()))
                    .max(Comparator.comparingInt(SlotCandidate::gameCount)
                            .thenComparingInt(SlotCandidate::winCount))
                    .ifPresent(best -> {
                        composed.add(best.item());
                        chosenItemIds.add(best.item().getItemId());
                    });
        }
        return composed;
    }

    private int specificity(ChampionBuildStats stats) {
        return countNonNull(
                stats.getEnemyTankHeavy(),
                stats.getEnemyApHeavy(),
                stats.getEnemyAssassinHeavy(),
                stats.getAllyHasMarksman(),
                stats.getAllyTankHeavy()
        );
    }

    private int countNonNull(Boolean... flags) {
        int count = 0;
        for (Boolean flag : flags) {
            if (flag != null) {
                count++;
            }
        }
        return count;
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record SlotCandidate(Item item, int gameCount, int winCount) {
        SlotCandidate combine(SlotCandidate other) {
            return new SlotCandidate(item, gameCount + other.gameCount, winCount + other.winCount);
        }
    }
}
