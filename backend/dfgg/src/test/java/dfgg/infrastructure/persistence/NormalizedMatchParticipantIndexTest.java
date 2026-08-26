package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("추천 핫패스(챔피언+포지션+티어+패치) 조회를 받쳐주는 인덱스가 실제 스키마에 존재한다")
    void normalizedMatchParticipants_WhenSchemaCreated_HasIndexCoveringRecommendationHotPathColumns() {
        // given: findNextItemDistribution / findMostFrequentBuild가 이 네 컬럼으로 필터링한다

        // when
        List<String> indexDefinitions = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'normalized_match_participants'",
                String.class
        );

        // then: 네 컬럼을 모두 덮되, 선택도가 가장 높은 champion_id가 선두여야 한다.
        //       (선두 컬럼이 아니면 챔피언 하나로 좁히지 못해 인덱스 효과가 크게 떨어진다)
        assertThat(indexDefinitions)
                .anySatisfy(definition -> assertThat(columnListOf(definition))
                        .startsWith("champion_id")
                        .contains("position")
                        .contains("tier")
                        .contains("patch"));
    }

    private String columnListOf(String indexDefinition) {
        int open = indexDefinition.indexOf('(');
        int close = indexDefinition.lastIndexOf(')');
        return indexDefinition.substring(open + 1, close).replace("\"", "");
    }
}
