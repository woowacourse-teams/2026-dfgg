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
    @DisplayName("참가자마다 (챔피언+구매 아이템) 참가자-빌드 윈도우를 하나씩 만든다")
    void buildMatchWindows_WhenParticipantsExist_CreatesParticipantBuildWindowPerParticipant() {
        // given
        List<NormalizedMatchParticipant> participants = matchParticipants(
                List.of(1, 2, 3, 4, 5), true,
                List.of(6, 7, 8, 9, 10), false
        );

        // when
        List<Window> windows = builder.buildMatchWindows(participants, WIN_WEIGHT);

        // then
        assertThat(windows)
                .filteredOn(window -> window.tokens().containsAll(List.of("1", "3071", "6653"))
                        && window.tokens().size() == 3)
                .hasSize(1)
                .first()
                .satisfies(window -> assertThat(window.weight()).isEqualTo(WIN_WEIGHT));
    }

    @Test
    @DisplayName("참가자마다 (적 5명 챔피언+자신이 구매한 아이템) 카운터 문맥 윈도우를 하나씩 만든다")
    void buildMatchWindows_WhenParticipantsExist_CreatesCounterContextWindowPerParticipant() {
        // given
        List<NormalizedMatchParticipant> participants = matchParticipants(
                List.of(1, 2, 3, 4, 5), true,
                List.of(6, 7, 8, 9, 10), false
        );

        // when
        List<Window> windows = builder.buildMatchWindows(participants, WIN_WEIGHT);

        // then
        assertThat(windows)
                .filteredOn(window -> window.tokens().containsAll(List.of("6", "7", "8", "9", "10", "3071", "6653"))
                        && window.tokens().size() == 7)
                .isNotEmpty()
                .first()
                .satisfies(window -> assertThat(window.weight()).isEqualTo(WIN_WEIGHT));
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
