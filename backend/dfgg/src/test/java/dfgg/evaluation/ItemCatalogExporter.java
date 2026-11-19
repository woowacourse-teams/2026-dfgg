package dfgg.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.item.Item;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 아이템의 이름·태그·가격을 Python이 읽을 수 있게 내보낸다.
 *
 * <p>학습 데이터(JSONL)에는 아이템 번호만 있어서, Python 혼자서는 어느 후보가 마법 저항 아이템인지 알 수 없다.
 * 번호 목록을 Python에 손으로 적어 두면 패치마다 아이템이 바뀔 때 조용히 낡는다. 그래서 내보낼 때의
 * 아이템 정보를 스키마 파일 옆에 함께 쓴다.
 */
public final class ItemCatalogExporter {

    private static final String FILE_NAME = "item_catalog.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Path export(Path directory, Collection<Item> items) throws IOException {
        Files.createDirectories(directory);
        Path path = directory.resolve(FILE_NAME);

        List<Map<String, Object>> rows = items.stream()
                .sorted(Comparator.comparing(Item::getItemId))
                .map(this::rowOf)
                .toList();
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), Map.of("items", rows));
        return path;
    }

    private Map<String, Object> rowOf(Item item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("item_id", item.getItemId());
        row.put("name", item.getName());
        row.put("tags", item.getTags());
        // 가격을 모르면 비워 둔다. 0으로 적으면 공짜 아이템으로 읽힌다.
        row.put("total_gold", item.totalGold().isPresent() ? item.totalGold().getAsInt() : null);
        return row;
    }
}
