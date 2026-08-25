package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/normalized-match-participant-repository-test-data.sql")
class NormalizedMatchParticipantRepositoryTest {

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;

    @Test
    @DisplayName("distinct matchId를 여러 페이지로 나눠 읽어도 빠지거나 중복되는 matchId 없이 전부 순회한다")
    void findDistinctMatchIds_WhenPagedAcrossMultiplePages_EnumeratesAllDistinctMatchIdsWithoutGapsOrDuplicates() {
        // given: data.sql이 KR_A~KR_G 7개 매치를 시딩한다(일부는 참가자 2명)
        List<String> collected = new ArrayList<>();

        // when
        int page = 0;
        Slice<String> slice;
        do {
            slice = participantRepository.findDistinctMatchIds(PageRequest.of(page, 3));
            collected.addAll(slice.getContent());
            page++;
        } while (slice.hasNext());

        // then
        assertThat(collected).containsExactlyInAnyOrder("KR_A", "KR_B", "KR_C", "KR_D", "KR_E", "KR_F", "KR_G");
        assertThat(collected).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("matchId 목록으로 조회하면 그 매치들의 참가자만 반환한다")
    void findByMatchIdIn_WhenGivenMatchIds_ReturnsOnlyParticipantsForThoseMatches() {
        // given & when
        List<NormalizedMatchParticipant> participants =
                participantRepository.findByMatchIdIn(List.of("KR_B", "KR_D"));

        // then
        assertThat(participants).hasSize(4);
        assertThat(participants)
                .extracting(NormalizedMatchParticipant::getMatchId)
                .containsOnly("KR_B", "KR_D");
    }

    @Test
    @DisplayName("아이템별 등장 횟수를 참가자 전체에서 집계한다")
    @Sql("/sql/item-occurrence-count-repository-test-data.sql")
    void countItemOccurrences_WhenGivenParticipants_CountsOccurrencesPerItem() {
        // given: 참가자 4명 중 아이템 1001은 3명, 2002는 2명이 샀다(KR_FREQ_3은 둘 다 삼)

        // when
        List<Object[]> counts = participantRepository.countItemOccurrences();

        // then
        Map<String, Long> occurrenceCountByItem = counts.stream()
                .collect(Collectors.toMap(row -> String.valueOf(row[0]), row -> ((Number) row[1]).longValue()));
        assertThat(occurrenceCountByItem).containsEntry("1001", 3L);
        assertThat(occurrenceCountByItem).containsEntry("2002", 2L);
    }
}
