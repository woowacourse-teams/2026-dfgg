package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.match.RawMatchRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/renormalization-test-data.sql")
class RenormalizationTargetQueryTest {

    @Autowired
    private RawMatchRepository rawMatchRepository;

    private List<String> targets(String cursor, String tier, int limit) {
        return rawMatchRepository.findNormalizedMatchIdsForRenormalizationAfter(
                cursor, tier, PageRequest.of(0, limit));
    }

    @Test
    @DisplayName("이미 정규화된 매치를 대상으로 삼는다 — 기존 pending 경로와 정반대 조건이다")
    void findTargets_WhenAlreadyNormalized_IncludesThem() {
        assertThat(targets("", "PLATINUM", 10)).contains("R1", "R2");
    }

    @Test
    @DisplayName("요청한 티어의 참가자가 있는 매치만 고른다 — replay가 티어별로 참가자를 찾기 때문")
    void findTargets_WhenTierDiffers_ExcludesThem() {
        assertThat(targets("", "PLATINUM", 10)).doesNotContain("R3");
        assertThat(targets("", "EMERALD", 10)).containsExactly("R3");
    }

    @Test
    @DisplayName("Timeline이 없으면 제외한다 — 원본이 불완전하면 재정규화해도 같은 결과다")
    void findTargets_WhenTimelineMissing_ExcludesThem() {
        assertThat(targets("", "PLATINUM", 10)).doesNotContain("R4");
    }

    @Test
    @DisplayName("아직 정규화되지 않은 매치는 제외한다 — 그건 기존 pending 경로의 몫이다")
    void findTargets_WhenNotYetNormalized_ExcludesThem() {
        assertThat(targets("", "PLATINUM", 10)).doesNotContain("R5");
    }

    @Test
    @DisplayName("커서 다음부터 매치 ID 오름차순으로 준다 — 중단해도 이어서 돌릴 수 있어야 한다")
    void findTargets_WhenCursorGiven_ReturnsAfterItInOrder() {
        assertThat(targets("", "PLATINUM", 10)).containsExactly("R1", "R2");
        assertThat(targets("R1", "PLATINUM", 10)).containsExactly("R2");
        assertThat(targets("R2", "PLATINUM", 10)).isEmpty();
    }

    @Test
    @DisplayName("limit만큼만 준다 — 한 번에 전량을 돌리지 않는다")
    void findTargets_WhenLimitGiven_ReturnsAtMostThatMany() {
        assertThat(targets("", "PLATINUM", 1)).containsExactly("R1");
    }
}
