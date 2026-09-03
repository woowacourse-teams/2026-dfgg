package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Test
    @DisplayName("챔피언과 포지션에서 가장 많이 등장한 빌드를 반환한다")
    @Sql("/sql/most-frequent-build-test-data.sql")
    void findMostFrequentBuild_WhenMultipleBuildsExist_ReturnsTheMostFrequentOne() {
        // given: 챔피언 222(BOTTOM)는 '3031,3072'를 3번, '3006,3031'을 1번 샀다

        // when
        Optional<String> build = participantRepository.findMostFrequentBuild(222L, List.of("BOTTOM"));

        // then
        assertThat(build).contains("3031,3072");
    }

    @Test
    @DisplayName("Riot 원시값(MIDDLE)으로 저장된 행도 MID의 별칭 목록으로 조회하면 매칭된다")
    @Sql("/sql/most-frequent-build-test-data.sql")
    void findMostFrequentBuild_WhenPositionStoredAsRiotAlias_MatchesViaAliasList() {
        // given: 챔피언 103은 position이 Riot 원시값 'MIDDLE'로 저장돼 있다

        // when
        Optional<String> build = participantRepository.findMostFrequentBuild(103L, List.of("MID", "MIDDLE"));

        // then
        assertThat(build).contains("3020,3089");
    }

    @Test
    @DisplayName("해당 챔피언과 포지션의 데이터가 없으면 빈 값을 반환한다")
    @Sql("/sql/most-frequent-build-test-data.sql")
    void findMostFrequentBuild_WhenNoDataForChampionAndPosition_ReturnsEmpty() {
        // given: 픽스처에 없는 챔피언

        // when
        Optional<String> build = participantRepository.findMostFrequentBuild(99999L, List.of("TOP"));

        // then
        assertThat(build).isEmpty();
    }

    @Test
    @DisplayName("prefix가 비어있으면(1코어) 실제로 1번째 위치에 있는 아이템 분포를 반환한다")
    @Sql("/sql/most-frequent-build-test-data.sql")
    void findNextItemDistribution_WhenPrefixIsEmpty_ReturnsFirstPositionItemDistribution() {
        // given: 챔피언 222(BOTTOM)는 '3031,3072'를 3번(승2패1), '3006,3031'을 1번(패) 샀다

        // when
        List<Object[]> rows = participantRepository.findNextItemDistribution(
                222L, List.of("BOTTOM"), "16.16","", 1
        );

        // then
        Map<String, long[]> byItem = rows.stream().collect(Collectors.toMap(
                row -> (String) row[0],
                row -> new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()}
        ));
        assertThat(byItem.get("3031")).containsExactly(3L, 2L);
        assertThat(byItem.get("3006")).containsExactly(1L, 0L);
    }

    @Test
    @DisplayName("prefix가 주어지면 그 prefix로 정확히 시작하는 사람들만 걸러 다음 위치 아이템 분포를 반환한다")
    @Sql("/sql/most-frequent-build-test-data.sql")
    void findNextItemDistribution_WhenPrefixGiven_ReturnsNextPositionItemDistributionForExactMatches() {
        // given: '3031'로 시작하는 건 '3031,3072' 3건뿐('3006,3031'은 3031로 시작하지 않음)

        // when
        List<Object[]> rows = participantRepository.findNextItemDistribution(
                222L, List.of("BOTTOM"), "16.16","3031", 2
        );

        // then
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo("3072");
        assertThat(((Number) rows.get(0)[1]).longValue()).isEqualTo(3L);
        assertThat(((Number) rows.get(0)[2]).longValue()).isEqualTo(2L);
    }

    @Test
    @DisplayName("어느 누구도 그 prefix로 시작하지 않으면 빈 리스트를 반환한다")
    @Sql("/sql/most-frequent-build-test-data.sql")
    void findNextItemDistribution_WhenNoOneMatchesPrefix_ReturnsEmptyList() {
        // given & when
        List<Object[]> rows = participantRepository.findNextItemDistribution(
                222L, List.of("BOTTOM"), "16.16","9999", 2
        );

        // then
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("MID 포지션은 Riot 원시값 MIDDLE까지 조회 대상에 포함해야 데이터를 찾는다")
    @Sql("/sql/most-frequent-build-test-data.sql")
    void findNextItemDistribution_WhenPositionIsMid_RequiresRiotAliasToFindData() {
        // given: 챔피언 103은 position이 Riot 원시값 'MIDDLE'로 저장돼 있다

        // when
        List<Object[]> rows = participantRepository.findNextItemDistribution(
                103L, List.of("MID", "MIDDLE"), "16.16","", 1
        );

        // then
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo("3020");
        assertThat(((Number) rows.get(0)[1]).longValue()).isEqualTo(2L);
        assertThat(((Number) rows.get(0)[2]).longValue()).isEqualTo(2L);
    }

    @Test
    @DisplayName("티어가 다른 참가자도 집계에 포함한다")
    @Sql("/sql/most-frequent-build-test-data.sql")
    void findNextItemDistribution_WhenParticipantsHaveDifferentTiers_AggregatesAcrossAllTiers() {
        // given: 챔피언 222(BOTTOM)에 PLATINUM 4건과 GOLD 1건('3078,3072')이 섞여 있다.
        //        추천은 요청자 티어와 무관하게 수집된 전 티어 데이터를 근거로 삼는다.

        // when
        List<Object[]> rows = participantRepository.findNextItemDistribution(
                222L, List.of("BOTTOM"), "16.16", "", 1
        );

        // then: GOLD 참가자의 1코어(3078)가 PLATINUM 참가자들과 나란히 집계된다
        Map<String, long[]> byItem = rows.stream().collect(Collectors.toMap(
                row -> (String) row[0],
                row -> new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()}
        ));
        assertThat(byItem).containsOnlyKeys("3031", "3006", "3078");
        assertThat(byItem.get("3078")).containsExactly(1L, 1L);
    }

    @Test
    @DisplayName("이 챔피언·포지션이 코어 아이템으로 산 적 있는 모든 아이템을 중복 없이 반환한다")
    @Sql("/sql/most-frequent-build-test-data.sql")
    void findDistinctPurchasedItemIds_WhenChampionHasMultipleBuilds_ReturnsAllItemsEverPurchased() {
        // given: 챔피언 222(BOTTOM)의 빌드는 '3031,3072'(3건), '3006,3031'(1건), '3078,3072'(1건)

        // when
        List<String> itemIds = participantRepository.findDistinctPurchasedItemIds(222L, List.of("BOTTOM"));

        // then
        assertThat(itemIds).containsExactlyInAnyOrder("3031", "3072", "3006", "3078");
    }

    @Test
    @DisplayName("MID 포지션은 Riot 원시값 MIDDLE까지 조회 대상에 포함해야 데이터를 찾는다")
    @Sql("/sql/most-frequent-build-test-data.sql")
    void findDistinctPurchasedItemIds_WhenPositionIsMid_RequiresRiotAliasToFindData() {
        // given: 챔피언 103은 position이 Riot 원시값 'MIDDLE'로 저장돼 있다

        // when
        List<String> itemIds = participantRepository.findDistinctPurchasedItemIds(103L, List.of("MID", "MIDDLE"));

        // then
        assertThat(itemIds).containsExactlyInAnyOrder("3020", "3089");
    }

    @Test
    @DisplayName("해당 챔피언·포지션의 데이터가 없으면 빈 리스트를 반환한다")
    @Sql("/sql/most-frequent-build-test-data.sql")
    void findDistinctPurchasedItemIds_WhenNoDataForScope_ReturnsEmptyList() {
        // given & when
        List<String> itemIds = participantRepository.findDistinctPurchasedItemIds(999L, List.of("TOP"));

        // then
        assertThat(itemIds).isEmpty();
    }
}
