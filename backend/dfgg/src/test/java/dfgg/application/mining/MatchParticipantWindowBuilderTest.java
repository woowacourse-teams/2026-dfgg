package dfgg.application.mining;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.embedding.WindowFactory;
import dfgg.domain.embedding.Window;
import dfgg.domain.item.Item;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedParticipant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchParticipantWindowBuilderTest {

    private static final double WIN_WEIGHT = 3.0;

    private final MatchParticipantWindowBuilder builder = new MatchParticipantWindowBuilder(new WindowFactory());

    @Test
    @DisplayName("한 매치의 참가자들로부터 아군/적군 팀 구성 윈도우 2개를 만든다")
    void buildMatchWindows_WhenMatchHasTwoTeams_CreatesTeamCompositionWindowsForBothTeams() {
        // given
        List<NormalizedMatchParticipant> participants = matchParticipants(
                List.of(1, 2, 3, 4, 5), true,
                List.of(6, 7, 8, 9, 10), false
        );

        // when
        List<Window> windows = builder.buildMatchWindows(participants, WIN_WEIGHT);

        // then
        assertThat(windows)
                .filteredOn(window -> window.tokens().containsAll(List.of("1", "2", "3", "4", "5"))
                        && window.tokens().size() == 5)
                .hasSize(1)
                .first()
                .satisfies(window -> assertThat(window.weight()).isEqualTo(WIN_WEIGHT));

        assertThat(windows)
                .filteredOn(window -> window.tokens().containsAll(List.of("6", "7", "8", "9", "10"))
                        && window.tokens().size() == 5)
                .hasSize(1)
                .first()
                .satisfies(window -> assertThat(window.weight()).isEqualTo(1.0));
    }

    @Test
    @DisplayName("참가자마다 (내 챔피언+구매 아이템) 빌드 문맥 윈도우를 하나씩 만든다")
    void buildMatchWindows_WhenParticipantsExist_CreatesBuildContextWindowPerParticipant() {
        // given
        List<NormalizedMatchParticipant> participants = matchParticipants(
                List.of(1, 2, 3, 4, 5), true,
                List.of(6, 7, 8, 9, 10), false
        );

        // when
        List<Window> windows = builder.buildMatchWindows(participants, WIN_WEIGHT);

        // then: 챔피언 1의 빌드 문맥 윈도우는 자신(1)과 자신이 산 아이템(3071, 6653)만 포함하고,
        //       아군/적군 챔피언은 섞이지 않는다
        assertThat(windows)
                .filteredOn(window -> window.tokens().contains("1") && window.tokens().contains("3071"))
                .hasSize(1)
                .first()
                .satisfies(window -> {
                    assertThat(window.tokens()).containsExactlyInAnyOrder("1", "3071", "6653");
                    assertThat(window.weight()).isEqualTo(WIN_WEIGHT);
                });
    }

    @Test
    @DisplayName("2팀 매치면 팀당 1개씩, 총 2개의 카운터 문맥 윈도우를 만든다")
    void buildCounterWindows_WhenMatchHasTwoTeams_CreatesOneCounterWindowPerTeam() {
        // given
        List<NormalizedMatchParticipant> participants = matchParticipants(
                List.of(1, 2, 3, 4, 5), true,
                List.of(6, 7, 8, 9, 10), false
        );

        // when
        List<Window> windows = builder.buildCounterWindows(participants, WIN_WEIGHT);

        // then
        assertThat(windows).hasSize(2);
    }

    @Test
    @DisplayName("카운터 문맥 윈도우는 상대팀 챔피언 전원과, 우리 팀이 산 아이템을 중복 없이 합쳐서 포함한다")
    void buildCounterWindows_WhenParticipantsBuyOverlappingItems_IncludesEnemyChampionsAndDeduplicatedTeamItems() {
        // given: 아군(1~5)은 3071을 두 명이 겹쳐서 사고, 적군(6~10)은 전원 3040만 산다
        NormalizedMatch match = new NormalizedMatch("KR_3", "14.1", 420, List.of());
        List<NormalizedMatchParticipant> participants = new ArrayList<>();
        List<List<Integer>> allyItemsByParticipant = List.of(
                List.of(3071, 6653), List.of(3071, 3020), List.of(6653), List.of(3071), List.of(3020)
        );
        int championId = 1;
        for (List<Integer> items : allyItemsByParticipant) {
            participants.add(new NormalizedMatchParticipant(match, new NormalizedParticipant(
                    "puuid-" + championId, championId, championId, 100, "TOP", true, items, items, true)));
            championId++;
        }
        for (int enemyChampionId : List.of(6, 7, 8, 9, 10)) {
            participants.add(new NormalizedMatchParticipant(match, new NormalizedParticipant(
                    "puuid-" + enemyChampionId, enemyChampionId, enemyChampionId, 200, "TOP", false,
                    List.of(3040), List.of(3040), true)));
        }

        // when
        List<Window> windows = builder.buildCounterWindows(participants, WIN_WEIGHT);

        // then: 아군팀 관점 카운터 윈도우는 적팀 챔피언(6~10) + 아군팀이 산 아이템 합집합(3071, 6653, 3020)만 포함하고,
        //       승리팀이므로 가중치가 적용된다
        assertThat(windows)
                .filteredOn(window -> window.tokens().contains("3071"))
                .hasSize(1)
                .first()
                .satisfies(window -> {
                    assertThat(window.tokens())
                            .containsExactlyInAnyOrder("6", "7", "8", "9", "10", "3071", "6653", "3020");
                    assertThat(window.weight()).isEqualTo(WIN_WEIGHT);
                });

        // then: 적팀 관점 카운터 윈도우는 아군팀 챔피언(1~5) + 적팀이 산 아이템(3040)만 포함하고,
        //       패배팀이므로 가중치가 1.0이다
        assertThat(windows)
                .filteredOn(window -> window.tokens().contains("3040"))
                .hasSize(1)
                .first()
                .satisfies(window -> {
                    assertThat(window.tokens())
                            .containsExactlyInAnyOrder("1", "2", "3", "4", "5", "3040");
                    assertThat(window.weight()).isEqualTo(1.0);
                });
    }

    @Test
    @DisplayName("태그가 있는 아이템만 콘텐츠 문맥 윈도우로 만들고, 태그 없는 아이템은 건너뛴다")
    void buildContentContextWindows_WhenItemHasNoTags_SkipsItemAndCreatesWindowOnlyForItemsWithTags() {
        // given
        Item withTags = new Item(3071L, "칠흑의 양날 도끼", List.of("Armor", "Mana"));
        Item withoutTags = new Item(9999L, "태그 없는 아이템", List.of());

        // when
        List<Window> windows = builder.buildContentContextWindows(List.of(withTags, withoutTags));

        // then
        assertThat(windows).hasSize(1);
        assertThat(windows.get(0).tokens()).containsExactlyInAnyOrder("3071", "Armor", "Mana");
    }

    private List<NormalizedMatchParticipant> matchParticipants(
            List<Integer> allyChampionIds,
            boolean allyWin,
            List<Integer> enemyChampionIds,
            boolean enemyWin
    ) {
        NormalizedMatch match = new NormalizedMatch("KR_1", "14.1", 420, List.of());
        List<NormalizedMatchParticipant> participants = new ArrayList<>();
        int participantId = 1;
        for (Integer championId : allyChampionIds) {
            participants.add(new NormalizedMatchParticipant(match, new NormalizedParticipant(
                    "puuid-" + participantId,
                    participantId++,
                    championId,
                    100,
                    "TOP",
                    allyWin,
                    List.of(3071, 6653),
                    List.of(3071, 6653),
                    true
            )));
        }
        for (Integer championId : enemyChampionIds) {
            participants.add(new NormalizedMatchParticipant(match, new NormalizedParticipant(
                    "puuid-" + participantId,
                    participantId++,
                    championId,
                    200,
                    "TOP",
                    enemyWin,
                    List.of(3020),
                    List.of(3020),
                    true
            )));
        }
        return participants;
    }
}
