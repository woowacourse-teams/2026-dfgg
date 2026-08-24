package dfgg.application.recommend;

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

/**
 * 조건에 맞는 통계 행들을 실제 추천 아이템 목록으로 조합한다.
 *
 * <p>같은 빌드가 여러 통계 범위에 존재할 수 있으므로 대표 행을 먼저 선택한 뒤
 * 슬롯별 후보를 합산한다.
 */
@Component
public class RecommendationBuildComposer {

    /**
     * 조회된 통계 행을 아이템 슬롯 순서의 추천 목록으로 변환한다.
     *
     * <ol>
     *     <li>같은 빌드의 중복 통계를 제거하고 표본이 가장 많은 대표 행을 선택한다.</li>
     *     <li>선택된 통계를 아이템 슬롯과 아이템 ID별 후보로 합산한다.</li>
     *     <li>각 슬롯에서 게임 수와 승리 수가 높은 아이템을 선택한다.</li>
     * </ol>
     */
    public List<Item> compose(List<ChampionBuildStats> matchingStats) {
        List<ChampionBuildStats> representativePerBuildKey = pickRepresentativePerBuildKey(matchingStats);
        Map<Integer, Map<Long, SlotCandidate>> slots = accumulateBySlot(representativePerBuildKey);
        return pickBestPerSlot(slots);
    }

    /**
     * 같은 buildKey를 가진 통계 중 표본이 가장 많은 행 하나만 남긴다.
     *
     * <p>조회 범위가 다른 동일 빌드를 여러 번 합산하지 않으면서 가장 대표성 있는 행을 사용한다.
     */
    private List<ChampionBuildStats> pickRepresentativePerBuildKey(List<ChampionBuildStats> matchingStats) {
        return matchingStats.stream()
                .collect(Collectors.groupingBy(ChampionBuildStats::getBuildKey))
                .values().stream()
                .map(group -> group.stream()
                        .max(Comparator.comparingInt(stats -> orZero(stats.getGameCount())))
                        .orElseThrow())
                .toList();
    }

    /**
     * 선택된 통계를 슬롯 번호와 아이템 ID별 후보로 누적한다.
     *
     * <p>서로 다른 조합 조건에서 같은 슬롯에 같은 아이템이 등장하면 게임 수와 승리 수를
     * 합산해 하나의 후보로 만든다. 슬롯별로 맵을 분리해 아이템이 어느 순서에 등장했는지 보존한다.
     */
    private Map<Integer, Map<Long, SlotCandidate>> accumulateBySlot(List<ChampionBuildStats> stats) {
        Map<Integer, Map<Long, SlotCandidate>> slots = new LinkedHashMap<>();
        for (ChampionBuildStats buildStats : stats) {
            List<Item> items = buildStats.getItems();
            int gameCount = orZero(buildStats.getGameCount());
            int winCount = orZero(buildStats.getWinCount());
            for (int slotIndex = 0; slotIndex < items.size(); slotIndex++) {
                Item item = items.get(slotIndex);
                // 같은 슬롯과 같은 아이템이 여러 통계 행에 있으면 관측량을 합친다.
                Map<Long, SlotCandidate> slotCandidates = slots.computeIfAbsent(slotIndex,
                        key -> new LinkedHashMap<>());
                slotCandidates.merge(item.getItemId(), new SlotCandidate(item, gameCount, winCount),
                        SlotCandidate::combine);
            }
        }
        return slots;
    }

    /**
     * 슬롯 순서대로 가장 좋은 아이템을 선택해 최종 빌드를 만든다.
     *
     * <p>동일 아이템이 여러 슬롯에서 선택되는 것을 막기 위해 이미 선택한 아이템 ID를 기록한다.
     * 후보의 우선순위는 해당 아이템이 관찰된 게임 수이며, 게임 수가 같으면 승리 수를 사용한다.
     */
    private List<Item> pickBestPerSlot(Map<Integer, Map<Long, SlotCandidate>> slots) {
        List<Item> composed = new ArrayList<>();
        Set<Long> chosenItemIds = new LinkedHashSet<>();
        int maxSlotIndex = slots.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int slotIndex = 0; slotIndex <= maxSlotIndex; slotIndex++) {
            Map<Long, SlotCandidate> slotCandidates = slots.getOrDefault(slotIndex, Map.of());
            // 이미 앞 슬롯에서 선택된 아이템은 중복 빌드가 되지 않도록 후보에서 제외한다.
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

    /**
     * 과거 데이터에서 집계 수가 null로 조회되는 경우 추천 계산에서는 0으로 취급한다.
     */
    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 한 슬롯에서 하나의 아이템이 차지하는 누적 추천 후보다.
     */
    private record SlotCandidate(Item item, int gameCount, int winCount) {

        /**
         * 같은 아이템 후보의 관측 게임 수와 승리 수를 합친다.
         */
        SlotCandidate combine(SlotCandidate other) {
            return new SlotCandidate(item, gameCount + other.gameCount, winCount + other.winCount);
        }
    }
}
