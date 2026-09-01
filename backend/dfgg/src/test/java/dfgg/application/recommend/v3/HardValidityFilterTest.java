package dfgg.application.recommend.v3;

import static dfgg.application.recommend.v3.CandidateSource.BUILD;
import static dfgg.application.recommend.v3.CandidateSource.COUNTER;
import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.item.Item;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HardValidityFilterTest {

    private static final long BERSERKERS_GREAVES = 3006L;
    private static final long PLATED_STEELCAPS = 3047L;
    private static final long INFINITY_EDGE = 3031L;
    private static final long LIANDRY = 6653L;
    private static final long UNKNOWN_ITEM = 99999L;

    private final HardValidityFilter filter = new HardValidityFilter();

    private final Map<Long, Item> itemById = Map.of(
            BERSERKERS_GREAVES, new Item(BERSERKERS_GREAVES, "광전사의 군화", List.of("Boots")),
            PLATED_STEELCAPS, new Item(PLATED_STEELCAPS, "판금 장화", List.of("Boots")),
            INFINITY_EDGE, new Item(INFINITY_EDGE, "무한의 대검", List.of("Damage", "CriticalStrike")),
            LIANDRY, new Item(LIANDRY, "리안드리의 고통", List.of("SpellDamage", "Health"))
    ).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private CandidateUnion unionOf(long... itemIds) {
        List<ScoredItem> items = java.util.Arrays.stream(itemIds)
                .mapToObj(id -> new ScoredItem(id, 0.5))
                .toList();
        return CandidateUnion.merge(List.of(GeneratorResult.of(BUILD, items)));
    }

    private List<Long> itemIdsOf(CandidateUnion union) {
        return union.candidates().stream().map(ItemCandidate::itemId).toList();
    }

    @Test
    @DisplayName("이미 산 아이템은 제거한다")
    void filter_WhenCandidateIsAlreadyPurchased_RemovesIt() {
        // given
        CandidateUnion union = unionOf(INFINITY_EDGE, LIANDRY);

        // when
        CandidateUnion filtered = filter.filter(union, List.of(INFINITY_EDGE), itemById);

        // then
        assertThat(itemIdsOf(filtered)).containsExactly(LIANDRY);
    }

    @Test
    @DisplayName("신발을 이미 신고 있으면 다른 신발도 제거한다 — 신발은 한 켤레만 신는다")
    void filter_WhenBootsAlreadyOwned_RemovesEveryOtherBoots() {
        // given
        CandidateUnion union = unionOf(PLATED_STEELCAPS, INFINITY_EDGE);

        // when
        CandidateUnion filtered = filter.filter(union, List.of(BERSERKERS_GREAVES), itemById);

        // then
        assertThat(itemIdsOf(filtered)).containsExactly(INFINITY_EDGE);
    }

    @Test
    @DisplayName("신발이 없으면 신발 후보를 남긴다")
    void filter_WhenNoBootsOwned_KeepsBootsCandidates() {
        // given
        CandidateUnion union = unionOf(BERSERKERS_GREAVES, INFINITY_EDGE);

        // when
        CandidateUnion filtered = filter.filter(union, List.of(), itemById);

        // then
        assertThat(itemIdsOf(filtered)).containsExactlyInAnyOrder(BERSERKERS_GREAVES, INFINITY_EDGE);
    }

    @Test
    @DisplayName("아이템 메타데이터가 없는 후보는 제거한다 — 응답으로 만들 수 없다")
    void filter_WhenItemMetadataMissing_RemovesCandidate() {
        // given
        CandidateUnion union = unionOf(UNKNOWN_ITEM, INFINITY_EDGE);

        // when
        CandidateUnion filtered = filter.filter(union, List.of(), itemById);

        // then
        assertThat(itemIdsOf(filtered)).containsExactly(INFINITY_EDGE);
    }

    @Test
    @DisplayName("AD 챔피언에게 AP 아이템이 와도 제거하지 않는다 — 이 판단은 LTR이 한다")
    void filter_WhenApItemForAdChampion_DoesNotRemoveIt() {
        // given: 리안드리(AP)는 야스오 같은 AD 챔피언 후보로 들어와도 유효한 구매다.
        //        AD/AP hard filter를 넣지 않는다는 게 이 작업의 핵심 요구사항이다.
        CandidateUnion union = unionOf(LIANDRY, INFINITY_EDGE);

        // when
        CandidateUnion filtered = filter.filter(union, List.of(), itemById);

        // then
        assertThat(itemIdsOf(filtered)).contains(LIANDRY);
    }

    @Test
    @DisplayName("counter만 발견한 후보라도 구매 가능하면 남긴다 — counter 적합성은 hard filter가 아니다")
    void filter_WhenCandidateFoundOnlyByCounter_KeepsItIfPurchasable() {
        // given
        CandidateUnion union = CandidateUnion.merge(List.of(
                GeneratorResult.of(COUNTER, List.of(new ScoredItem(LIANDRY, 0.9)))
        ));

        // when
        CandidateUnion filtered = filter.filter(union, List.of(), itemById);

        // then
        assertThat(itemIdsOf(filtered)).containsExactly(LIANDRY);
    }

    @Test
    @DisplayName("후보의 source별 근거는 필터를 통과해도 그대로 보존된다")
    void filter_WhenCandidateSurvives_PreservesItsSourceEvidence() {
        // given
        CandidateUnion union = CandidateUnion.merge(List.of(
                GeneratorResult.of(BUILD, List.of(new ScoredItem(INFINITY_EDGE, 0.81))),
                GeneratorResult.of(COUNTER, List.of(new ScoredItem(INFINITY_EDGE, 0.12)))
        ));

        // when
        CandidateUnion filtered = filter.filter(union, List.of(), itemById);

        // then
        ItemCandidate candidate = filtered.candidateOf(INFINITY_EDGE);
        assertThat(candidate.evidenceOf(BUILD)).contains(new SourceEvidence(0.81, 1));
        assertThat(candidate.evidenceOf(COUNTER)).contains(new SourceEvidence(0.12, 1));
    }
}
