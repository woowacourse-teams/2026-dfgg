package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 엔티티 매핑이 선언한 인덱스가 실제 DB 스키마에 만들어지는지 검증한다.
 *
 * 추천 API의 안전 구역 조회({@code findNextItemDistribution})는 1~2코어 추천마다 호출되는
 * 핫패스인데, 받쳐주는 인덱스가 없으면 참가자 테이블 전체를 순차 스캔한다. 인덱스 선언이
 * 실수로 빠지거나 컬럼명이 바뀌어 조용히 무효해지는 걸 막기 위한 회귀 방어선이다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NormalizedMatchParticipantIndexTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("추천 핫패스(챔피언+포지션+패치) 조회를 받쳐주는 인덱스가 실제 스키마에 존재한다")
    void normalizedMatchParticipants_WhenSchemaCreated_HasIndexCoveringRecommendationHotPathColumns() {
        // given: findNextItemDistribution이 이 세 컬럼으로 필터링한다
        //        (findMostFrequentBuild는 champion_id, position 둘만 쓴다)

        // when
        List<String> indexDefinitions = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'normalized_match_participants'",
                String.class
        );

        // then: 세 컬럼을 정확히 이 순서로 덮어야 한다.
        //       champion_id가 선두인 이유는 findMostFrequentBuild가 (champion_id, position)
        //       좌측 prefix를 재사용하기 때문이고, tier가 없어야 하는 이유는 중간에 끼면
        //       뒤따르는 patch가 인덱스 경계로 못 쓰이기 때문이다.
        assertThat(indexDefinitions)
                .anySatisfy(definition -> assertThat(indexedColumnsOf(definition))
                        .containsExactly("champion_id", "position", "patch"));
    }

    /**
     * {@code CREATE INDEX ... USING btree (champion_id, "position", patch)}에서 컬럼 토큰만 뽑는다.
     *
     * <p>Postgres는 예약어인 position을 큰따옴표로 감싸고, 부분 인덱스는 뒤에 {@code WHERE (...)}
     * 절을 덧붙인다. 첫 괄호쌍만 잘라내야 그 WHERE절이 컬럼 목록으로 새어들지 않는다.
     */
    private List<String> indexedColumnsOf(String indexDefinition) {
        int open = indexDefinition.indexOf('(');
        int close = indexDefinition.indexOf(')', open);
        return Arrays.stream(indexDefinition.substring(open + 1, close).split(","))
                .map(column -> column.replace("\"", "").trim())
                .toList();
    }
}
