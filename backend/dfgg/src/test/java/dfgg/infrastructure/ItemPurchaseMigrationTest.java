package dfgg.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ItemPurchaseMigrationTest {

    private Connection connection;

    @BeforeEach
    void prepareIsolatedSchema() throws Exception {
        connection = DriverManager.getConnection(
                System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://127.0.0.1:5432/dfgg_test"),
                System.getenv().getOrDefault("TEST_DB_USERNAME", "dfgg"),
                System.getenv().getOrDefault("TEST_DB_PASSWORD", "dfgg"));
        connection.setAutoCommit(false);
        String schema = "purchase_test_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET LOCAL search_path TO " + schema);
        try (var migration = getClass().getResourceAsStream("/db/migration/V6__create_participant_item_purchases.sql")) {
            assertThat(migration).isNotNull();
            execute(new String(migration.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @AfterEach
    void rollbackSchema() throws Exception {
        if (connection != null) {
            try { connection.rollback(); } finally { connection.close(); }
        }
    }

    @Test
    void 구매_순번_중복을_막고_미확보_골드와_비용은_NULL을_허용한다() throws Exception {
        insert(1, "COMPONENT", "NULL", "NULL");
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT current_gold, item_cost FROM normalized_match_participant_item_purchases")) {
            rows.next();
            assertThat(rows.getObject(1)).isNull();
            assertThat(rows.getObject(2)).isNull();
        }
        assertThatThrownBy(() -> insert(1, "CORE", "NULL", "NULL"))
                .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23505"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STARTER", "COMPONENT", "CORE", "BOOTS", "CONSUMABLE", "OTHER"})
    void 권장_서비스_분류를_모두_저장한다(String type) throws Exception {
        insert(1, type, "1500", "350");
    }

    @Test
    void 구매_행동을_아이템_분류로_잘못_저장할_수_없다() {
        assertThatThrownBy(() -> insert(1, "ITEM_PURCHASED", "NULL", "NULL"))
                .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23514"));
    }

    private void insert(int order, String type, String gold, String cost) throws SQLException {
        execute("""
                INSERT INTO normalized_match_participant_item_purchases
                    (match_id, participant_id, purchase_order, item_id, purchase_type, patch, current_gold, item_cost)
                VALUES ('TEST', 1, %d, 1036, '%s', '16.18', %s, %s)
                """.formatted(order, type, gold, cost));
    }

    private void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
