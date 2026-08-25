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
import java.util.Map;
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
    @DisplayName("카운터 문맥 윈도우는 (적 챔피언 수 × 우리 팀 아이템 종류 수)개만큼, 적-아이템 쌍 하나당 하나씩(토큰 2개) 생긴다")
    void buildCounterWindows_WhenMatchHasTwoTeams_CreatesOneWindowPerEnemyItemPair() {
        // given: 아군은 아이템 2종(3071, 6653), 적군은 아이템 1종(3020)을 산다 (matchParticipants 기본 픽스처)
        List<NormalizedMatchParticipant> participants = matchParticipants(
                List.of(1, 2, 3, 4, 5), true,
                List.of(6, 7, 8, 9, 10), false
        );

        // when
        List<Window> windows = builder.buildCounterWindows(participants, WIN_WEIGHT, neutralItemFrequencyWeights());

        // then: 아군 관점(적 5명 × 아이템 2종=10) + 적 관점(아군 5명 × 아이템 1종=5) = 15개,
        //       모든 윈도우가 정확히 토큰 2개(챔피언 1명+아이템 1개)라 챔피언-챔피언/아이템-아이템 쌍이 생길 수 없다
        assertThat(windows).hasSize(15);
        assertThat(windows).allSatisfy(window -> assertThat(window.tokens()).hasSize(2));
    }

    @Test
    @DisplayName("카운터 문맥 윈도우 가중치에는 아이템 빈도 가중치가 곱해진다")
    void buildCounterWindows_MultipliesWindowWeightByItemFrequencyWeight() {
        // given: 아군은 아이템 3071(자주 나옴, 가중치 0.5)/6653(드묾, 가중치 4.0)을 산다
        List<NormalizedMatchParticipant> participants = matchParticipants(
                List.of(1, 2, 3, 4, 5), true,
                List.of(6, 7, 8, 9, 10), false
        );
        ItemFrequencyWeights itemFrequencyWeights =
                ItemFrequencyWeights.from(Map.of("3071", 900L, "6653", 5L), 1000L);

        // when
        List<Window> windows = builder.buildCounterWindows(participants, WIN_WEIGHT, itemFrequencyWeights);

        // then: 같은 승리팀 window라도 아이템에 따라 최종 가중치가 다르다(WIN_WEIGHT * 빈도 가중치)
        double item3071Weight = itemFrequencyWeights.weightFor("3071");
        double item6653Weight = itemFrequencyWeights.weightFor("6653");
        assertThat(windows)
                .filteredOn(window -> window.tokens().contains("3071"))
                .allSatisfy(window -> assertThat(window.weight()).isEqualTo(WIN_WEIGHT * item3071Weight));
        assertThat(windows)
                .filteredOn(window -> window.tokens().contains("6653"))
                .allSatisfy(window -> assertThat(window.weight()).isEqualTo(WIN_WEIGHT * item6653Weight));
        assertThat(item6653Weight).isGreaterThan(item3071Weight);
    }

    @Test
    @DisplayName("카운터 문맥 윈도우는 적-아이템 쌍마다 정확히 하나씩 생기고, 아이템은 팀 내에서 중복 제거된다")
    void buildCounterWindows_WhenParticipantsBuyOverlappingItems_CreatesOneWindowPerDeduplicatedEnemyItemPair() {
        // given: 아군(1~5)은 3071을 두 명이 겹쳐서 사서 실제 종류는 3071/6653/3020 세 가지, 적군(6~10)은 전원 3040만 산다
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
        List<Window> windows = builder.buildCounterWindows(participants, WIN_WEIGHT, neutralItemFrequencyWeights());

        // then: 아군팀 관점 윈도우는 (적 5명 × 아이템 3종=15개), 전부 토큰 2개이고 승리팀이라 가중치가 적용된다
        List<Window> allyPerspectiveWindows = windows.stream()
                .filter(window -> window.tokens().stream().anyMatch(List.of("3071", "6653", "3020")::contains))
                .toList();
        assertThat(allyPerspectiveWindows).hasSize(15);
        assertThat(allyPerspectiveWindows).allSatisfy(window -> {
            assertThat(window.tokens()).hasSize(2);
            assertThat(window.weight()).isEqualTo(WIN_WEIGHT);
        });

        // then: 아이템 3071은 두 참가자가 겹쳐서 샀지만 중복 제거되어, 적 5명과 각각 한 번씩 총 5개 윈도우만 생긴다
        List<Window> item3071Windows = windows.stream()
                .filter(window -> window.tokens().contains("3071"))
                .toList();
        assertThat(item3071Windows).hasSize(5);
        List<String> enemiesPairedWith3071 = item3071Windows.stream()
                .flatMap(window -> window.tokens().stream())
                .filter(token -> List.of("6", "7", "8", "9", "10").contains(token))
                .toList();
        assertThat(enemiesPairedWith3071).containsExactlyInAnyOrder("6", "7", "8", "9", "10");

        // then: 적팀 관점 윈도우는 (아군 5명 × 아이템 1종=5개), 패배팀이라 가중치가 1.0이다
        List<Window> enemyPerspectiveWindows = windows.stream()
                .filter(window -> window.tokens().contains("3040"))
                .toList();
        assertThat(enemyPerspectiveWindows).hasSize(5);
        assertThat(enemyPerspectiveWindows).allSatisfy(window -> {
            assertThat(window.tokens()).hasSize(2);
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

    private ItemFrequencyWeights neutralItemFrequencyWeights() {
        return ItemFrequencyWeights.from(Map.of(), 1L);
    }
}
