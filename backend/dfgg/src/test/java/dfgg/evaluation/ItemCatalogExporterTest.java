package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.item.Item;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 학습 줄에는 아이템 번호만 있다. Python이 "마법 저항 아이템" 같은 묶음을 번호 목록 없이 태그로 고를 수 있게
 * 아이템의 이름·태그·가격을 함께 내보낸다.
 */
class ItemCatalogExporterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Item rabadon = new Item(
            3089L, Map.of("en_US", "Rabadon's Deathcap"), Map.of("total", 3600), List.of("SpellDamage"));
    private final Item kaenic = new Item(
            2504L, Map.of("en_US", "Kaenic Rookern"), Map.of("total", 2900), List.of("Health", "SpellBlock"));

    private JsonNode exportAndRead(Path directory, List<Item> items) throws IOException {
        Path path = new ItemCatalogExporter().export(directory, items);
        return objectMapper.readTree(Files.readString(path));
    }

    @Test
    @DisplayName("아이템마다 번호·이름·태그·총가격을 내보낸다")
    void export_WhenItemsGiven_WritesIdNameTagsAndTotalGold(@TempDir Path directory) throws IOException {
        // when
        JsonNode catalog = exportAndRead(directory, List.of(rabadon));

        // then
        JsonNode item = catalog.get("items").get(0);
        assertThat(item.get("item_id").asLong()).isEqualTo(3089L);
        assertThat(item.get("name").get("en_US").asText()).isEqualTo("Rabadon's Deathcap");
        assertThat(item.get("tags")).extracting(JsonNode::asText).containsExactly("SpellDamage");
        assertThat(item.get("total_gold").asInt()).isEqualTo(3600);
    }

    @Test
    @DisplayName("아이템 번호 순으로 내보낸다 — 같은 입력이면 같은 파일이 나온다")
    void export_WhenItemsUnordered_OrdersThemById(@TempDir Path directory) throws IOException {
        // when
        JsonNode catalog = exportAndRead(directory, List.of(rabadon, kaenic));

        // then
        assertThat(catalog.get("items")).extracting(item -> item.get("item_id").asLong())
                .containsExactly(2504L, 3089L);
    }

    @Test
    @DisplayName("가격을 모르는 아이템은 총가격을 비워 내보낸다 — 0으로 적으면 공짜 아이템으로 읽힌다")
    void export_WhenItemHasNoGold_LeavesTotalGoldNull(@TempDir Path directory) throws IOException {
        // given
        Item withoutGold = new Item(1L, Map.of("en_US", "Unknown"), List.of());

        // when
        JsonNode catalog = exportAndRead(directory, List.of(withoutGold));

        // then
        assertThat(catalog.get("items").get(0).get("total_gold").isNull()).isTrue();
    }

    @Test
    @DisplayName("파일 이름이 고정돼 있다 — Python이 스키마 파일 옆에서 이 이름으로 찾는다")
    void export_WhenCalled_UsesFixedFileName(@TempDir Path directory) throws IOException {
        // when
        Path path = new ItemCatalogExporter().export(directory, List.of(rabadon));

        // then
        assertThat(path.getFileName().toString()).isEqualTo("item_catalog.json");
    }
}
