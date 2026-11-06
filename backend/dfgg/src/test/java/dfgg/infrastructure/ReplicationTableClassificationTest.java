package dfgg.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ReplicationTableClassificationTest {
    private static final Path CLASSIFICATION = Path.of("../ops/pipeline/replication-tables.txt");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Map<String, String> classified;

    @BeforeEach
    void setUp() throws IOException {
        classified = readClassification();
    }

    @Test
    @DisplayName("Flyway 스키마의 모든 테이블이 복제 또는 제외로 분류돼 있다")
    void classification_WhenSchemaMigrated_CoverEveryTable() {
        // given
        Set<String> tables = new TreeSet<>(jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """, String.class));

        // when
        Set<String> unclassified = new TreeSet<>(tables);
        unclassified.removeAll(classified.keySet());
        Set<String> unknown = new TreeSet<>(classified.keySet());
        unknown.removeAll(tables);

        // then
        assertThat(unclassified)
                .as("새 테이블은 backend/ops/pipeline/replication-tables.txt에 replicate 또는 exclude로 적는다")
                .isEmpty();
        assertThat(unknown).as("없는 테이블이 분류 파일에 남아 있다").isEmpty();
    }

    @Test
    @DisplayName("대상 DB가 직접 쓰는 테이블은 복제 대상이 아니다")
    void classification_WhenTargetOwnsTable_Exclude() {
        // when & then
        assertThat(List.of("feedback", "flyway_schema_history", "champion_counters", "champions", "items"))
                .allSatisfy(table -> assertThat(classified).containsEntry(table, "exclude"));
    }

    @Test
    @DisplayName("복제 대상 테이블은 다른 테이블을 외래 키로 참조하거나 참조받지 않는다")
    void classification_WhenReplicated_HaveNoForeignKeys() {
        // given
        List<String> replicated = classified.entrySet().stream()
                .filter(entry -> entry.getValue().equals("replicate"))
                .map(Map.Entry::getKey)
                .toList();

        // when
        List<String> related = jdbcTemplate.queryForList("""
                SELECT DISTINCT c.conrelid::regclass::text || ' -> ' || c.confrelid::regclass::text
                FROM pg_constraint c
                WHERE c.contype = 'f'
                  AND (c.conrelid::regclass::text = ANY (?) OR c.confrelid::regclass::text = ANY (?))
                """, String.class, replicated.toArray(String[]::new), replicated.toArray(String[]::new));

        // then
        assertThat(related).as("교체 방식은 외래 키가 없는 테이블에만 안전하다").isEmpty();
    }

    private static Map<String, String> readClassification() throws IOException {
        Map<String, String> result = new HashMap<>();
        for (String line : Files.readAllLines(CLASSIFICATION)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            assertThat(parts).as("형식: replicate|exclude <테이블> — " + line).hasSize(2);
            assertThat(parts[0]).as(line).isIn("replicate", "exclude");
            assertThat(result.put(parts[1], parts[0])).as("중복 분류: " + parts[1]).isNull();
        }
        return result;
    }
}
