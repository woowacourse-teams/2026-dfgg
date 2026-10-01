package dfgg.application.recommend.component;

import dfgg.domain.match.BuildPathResolver;
import dfgg.application.ChampionPositionNormalizer;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.PurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import java.util.Map;

/** 구매 후보와 Feature. 관측 정답·학습 라벨 없이 학습과 추론에서 공통으로 사용한다. */
public record ComponentCandidateFeatures(Map<Integer, Integer> itemQuantities, List<Integer> purchaseOrder,
                                         Map<Integer, Integer> inventoryAfter, List<Double> features) {
    public ComponentCandidateFeatures {
        itemQuantities = Map.copyOf(itemQuantities);
        purchaseOrder = List.copyOf(purchaseOrder);
        inventoryAfter = Map.copyOf(inventoryAfter);
        features = List.copyOf(features);
        if (features.size() != FEATURE_NAMES.size() || features.stream().anyMatch(value -> !Double.isFinite(value))) {
            throw new IllegalArgumentException("Component Feature 계약과 값이 맞지 않습니다.");
        }
    }

    private static final ChampionPositionNormalizer POSITIONS = new ChampionPositionNormalizer();

    private static final List<String> STAT_KEYS = List.of("FlatPhysicalDamageMod", "FlatMagicDamageMod",
            "FlatHPPoolMod", "FlatMPPoolMod", "FlatArmorMod", "FlatSpellBlockMod", "PercentAttackSpeedMod",
            "FlatCritChanceMod", "PercentLifeStealMod", "FlatMovementSpeedMod", "PercentMovementSpeedMod",
            "FlatHPRegenMod");
    public static final List<String> FEATURE_NAMES = List.of("additional_cost", "purchase_quantity",
            "distinct_purchase_items", "slots_before", "slots_after", "completes_target_core",
            "consumed_owned_quantity", "consumed_owned_value", "basic_purchase_quantity", "intermediate_purchase_quantity",
            "attack_damage_delta", "ability_power_delta", "health_delta", "mana_delta", "armor_delta",
            "magic_resist_delta", "attack_speed_delta", "crit_chance_delta", "life_steal_delta",
            "flat_move_speed_delta", "percent_move_speed_delta", "health_regen_delta",
            "game_time_minutes", "position_top", "position_jungle", "position_mid", "position_bottom", "position_support", "position_unknown",
            "attack_damage_before", "ability_power_before", "health_before", "mana_before", "armor_before", "magic_resist_before", "attack_speed_before", "crit_chance_before", "life_steal_before", "flat_move_speed_before", "percent_move_speed_before", "health_regen_before");

    /** 학습과 추론에 같은 계산을 사용한다. Gold 추정치·미래 구매 정보는 입력받지 않는다. */
    public static ComponentCandidateFeatures calculate(dfgg.domain.match.ItemInventory before,
            BuildPathResolver.PurchaseBundle candidate, int targetCoreId, PurchaseItemCatalog catalog, ItemResponse response, long gameTimeMs, String position) {
        if (gameTimeMs < 0) throw new IllegalArgumentException("게임 시간은 음수일 수 없습니다.");
        var after = candidate.inventoryAfter();
        int consumedQuantity = 0;
        long consumedValue = 0;
        for (var held : before.quantities().entrySet()) {
            // before + 구매 수량 - after = 조합에 소비된 수량. 묶음 내부 재조합은 후보 생성에서 제외한다.
            int consumed = held.getValue() + candidate.itemQuantities().getOrDefault(held.getKey(), 0)
                    - after.quantityOf(held.getKey());
            if (consumed > 0) {
                consumedQuantity += consumed;
                consumedValue += (long) consumed * catalog.find(held.getKey()).orElseThrow().totalPrice();
            }
        }
        int basic = 0;
        int intermediate = 0;
        for (var purchased : candidate.itemQuantities().entrySet()) {
            if (purchased.getKey() == targetCoreId) continue;
            if (catalog.find(purchased.getKey()).orElseThrow().ingredients().isEmpty()) basic += purchased.getValue();
            else intermediate += purchased.getValue();
        }
        List<Double> vector = new java.util.ArrayList<>(List.of((double) candidate.additionalCost(),
                (double) candidate.itemQuantities().values().stream().mapToInt(Integer::intValue).sum(),
                (double) candidate.itemQuantities().size(), (double) catalog.occupiedSlots(before),
                (double) catalog.occupiedSlots(after), after.quantityOf(targetCoreId) > before.quantityOf(targetCoreId) ? 1.0 : 0.0,
                (double) consumedQuantity, (double) consumedValue, (double) basic, (double) intermediate));
        for (String stat : STAT_KEYS) vector.add(statDelta(before, after, response, stat));
        // 첫 구매 직전의 공통 맥락. 후보마다 복사되어 능력치·비용과 함께 트리에서 비교된다.
        vector.add(gameTimeMs / 60_000.0);
        var normalizedPosition = POSITIONS.normalize(position);
        for (ChampionPosition known : ChampionPosition.values()) {
            vector.add(normalizedPosition.filter(known::equals).isPresent() ? 1.0 : 0.0);
        }
        vector.add(normalizedPosition.isEmpty() ? 1.0 : 0.0);
        // 현재 보유 아이템의 기본 능력치 합. 챔피언 기본 능력치·패시브는 포함하지 않는다.
        for (String stat : STAT_KEYS) vector.add(statDelta(dfgg.domain.match.ItemInventory.empty(), before, response, stat));
        return new ComponentCandidateFeatures(candidate.itemQuantities(), candidate.purchaseOrder(),
                after.quantities(), vector);
    }

    private static double statDelta(dfgg.domain.match.ItemInventory before,
            dfgg.domain.match.ItemInventory after, ItemResponse response, String stat) {
        // 그대로 보유한 아이템은 먼저 상쇄한다. 전체 합끼리 빼면 무관한 능력치에 반올림 오차가 남는다.
        var changedIds = new java.util.TreeSet<>(before.quantities().keySet());
        changedIds.addAll(after.quantities().keySet());
        double total = 0;
        for (int itemId : changedIds) {
            int quantityDelta = after.quantityOf(itemId) - before.quantityOf(itemId);
            if (quantityDelta == 0) continue;
            var item = response.data().get(Integer.toString(itemId));
            if (item == null) throw new IllegalArgumentException("능력치 카탈로그에 아이템이 없습니다: " + itemId);
            // stats에 없는 능력치는 0이다. 패시브·고유 효과·챔피언별 실제 최종 능력치를 뜻하지 않는다.
            double value = item.stats() == null ? 0.0 : item.stats().getOrDefault(stat, 0.0);
            if (!Double.isFinite(value)) throw new IllegalArgumentException("유효하지 않은 아이템 능력치입니다.");
            total += quantityDelta * value;
        }
        return total;
    }
}
