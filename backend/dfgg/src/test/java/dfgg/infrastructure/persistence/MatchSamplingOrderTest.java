package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * 매치를 {@code match_id} 순으로 뽑으면 시간순으로 뽑히는 것과 같다 — 앞에서 N개를 자르면
 * 가장 오래된 패치만 표본에 들어간다. 실제로 학습 데이터 30,000 query가 전부 16.1~16.4에서만
 * 나와 최신 패치 test 세트가 비었다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/match-sampling-order-test-data.sql")
class MatchSamplingOrderTest {

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;

    @Test
    @DisplayName("표본 순서가 매치 ID 순서와 다르다 — 시간 편향이 표본에 새지 않는다")
    void findSampledMatchIds_DoesNotFollowMatchIdOrder() {
        // given: 매치 ID는 시간순으로 증가한다 (KR_001 → KR_030)
        List<String> byMatchId = participantRepository
                .findDistinctMatchIds(PageRequest.of(0, 30)).getContent();
        List<String> sampled = participantRepository
                .findSampledMatchIds(PageRequest.of(0, 30));

        // then: 같은 집합이지만 순서가 달라야 한다
        assertThat(sampled).containsExactlyInAnyOrderElementsOf(byMatchId);
        assertThat(sampled).isNotEqualTo(byMatchId);
    }

    @Test
    @DisplayName("앞에서 잘라도 최신 패치가 표본에 들어온다")
    void findSampledMatchIds_WhenTruncated_StillCoversLatestPatch() {
        // given: 절반만 뽑는다
        List<String> sampled = participantRepository.findSampledMatchIds(PageRequest.of(0, 15));

        // when: 그 매치들의 패치를 본다
        List<String> patches = sampled.stream()
                .flatMap(matchId -> participantRepository.findByMatchId(matchId).stream())
                .map(participant -> participant.getPatch())
                .distinct()
                .toList();

        // then: 오래된 패치만 나오면 안 된다
        assertThat(patches).contains("16.16");
    }

    @Test
    @DisplayName("같은 요청은 항상 같은 순서를 준다 — 학습 데이터를 재현할 수 있어야 한다")
    void findSampledMatchIds_IsDeterministic() {
        List<String> first = participantRepository.findSampledMatchIds(PageRequest.of(0, 20));
        List<String> second = participantRepository.findSampledMatchIds(PageRequest.of(0, 20));

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("페이지를 넘겨도 중복 없이 이어진다")
    void findSampledMatchIds_WhenPaged_DoesNotRepeat() {
        List<String> firstPage = participantRepository.findSampledMatchIds(PageRequest.of(0, 10));
        List<String> secondPage = participantRepository.findSampledMatchIds(PageRequest.of(1, 10));

        assertThat(firstPage).doesNotContainAnyElementsOf(secondPage);
    }
}
