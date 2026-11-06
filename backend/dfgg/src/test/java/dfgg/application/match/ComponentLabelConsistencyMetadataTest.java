package dfgg.application.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.ComponentTargetPolicy;
import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.PurchaseRecipeMatcher;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 기존 도메인 레시피 판정으로 오프라인 진단용 Inventory projection을 만든다. */
@Tag("evaluation")
class ComponentLabelConsistencyMetadataTest {
    @Test
    void export_recipe_inventory_metadata() throws Exception {
        var mapper = new ObjectMapper();
        var response = mapper.readValue(Path.of(System.getProperty("evaluation.component.catalog")).toFile(), ItemResponse.class);
        var catalog = new DataDragonPurchaseItemCatalog(response.data());
        var recipes = new PurchaseRecipeMatcher(catalog);
        var policy = new ComponentTargetPolicy(catalog);
        Path input = Path.of(System.getProperty("evaluation.consistency.input"));
        Path output = Path.of(System.getProperty("evaluation.consistency.output"));
        try (var reader = Files.newBufferedReader(input); var writer = Files.newBufferedWriter(output)) {
            String line;
            while ((line = reader.readLine()) != null) {
                var row = mapper.readTree(line);
                int targetId = row.get("targetItemId").asInt();
                var inventory = ItemInventory.empty();
                var fields = row.get("inventoryBefore").fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    for (int i = 0; i < field.getValue().asInt(); i++) inventory = inventory.add(Integer.parseInt(field.getKey()));
                }
                var target = catalog.find(targetId).orElseThrow();
                var relevantIds = new HashSet<Integer>();
                recipes.remainingPath(target, ItemInventory.empty()).forEach(item -> relevantIds.add(item.id()));
                relevantIds.addAll(recipes.materialsFor(target, inventory).ids());
                Map<Integer, Integer> relevant = new HashMap<>();
                inventory.quantities().forEach((id, quantity) -> {
                    if (relevantIds.contains(id)) relevant.put(id, quantity);
                });
                writer.write(mapper.writeValueAsString(Map.of("key", row.get("key").asText(),
                        "recipeRelevantInventory", relevant, "targetKind", policy.kind(targetId).name())));
                writer.newLine();
            }
        }
    }
}
