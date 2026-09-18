package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.PatchVersion;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("evaluation")
@Tag("evaluation")
class EvaluationDataPreconditionTest {

    /** 재정규화가 반영됐다면 SUPPORT도 다른 포지션과 비슷한 수준이어야 한다. */
    private static final double MINIMUM_SUPPORT_USABLE_RATE = 90.0;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("서포터 재정규화가 반영된 덤프를 보고 있다 — 구 덤프면 SUPPORT가 9.7%로 무너져 있다")
    void evaluationDatabase_HasTheSupportQuestRenormalization() throws SQLException {
        double supportUsableRate = supportUsableRate();

        System.out.printf("평가 DB: %s, 참가자=%d, SUPPORT 사용가능=%.1f%%%n",
                currentDatabase(), participantRepository.count(), supportUsableRate);

        assertThat(supportUsableRate)
                .as("SUPPORT의 core_item_purchase_order 사용가능 비율")
                .isGreaterThan(MINIMUM_SUPPORT_USABLE_RATE);
    }

    @Test
    @DisplayName("patch split의 test 패치는 16.16이다 — 경계가 바뀌면 지표를 비교할 수 없다")
    void evaluationDatabase_LatestPatchIsTheExpectedSplitBoundary() {
        List<String> patches = participantRepository.findDistinctPatches();

        String latestPatch = patches.stream()
                .map(PatchVersion::of)
                .max(Comparator.naturalOrder())
                .orElseThrow()
                .value();

        assertThat(latestPatch).isEqualTo("16.16");
    }

    @Test
    @DisplayName("실 매치 데이터가 충분하다 — 빈 DB를 가리키면 모든 지표가 무의미해진다")
    void evaluationDatabase_HasEnoughRealMatches() {
        assertThat(participantRepository.count()).isGreaterThan(600_000L);
    }

    private double supportUsableRate() throws SQLException {
        String sql = """
                SELECT round(100.0 * count(*) FILTER (
                           WHERE core_item_purchase_order_complete AND core_item_purchase_order <> ''
                       ) / count(*), 1)
                FROM normalized_match_participants
                WHERE position IN ('UTILITY', 'SUPPORT')
                """;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getDouble(1);
        }
    }

    private String currentDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT current_database()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
