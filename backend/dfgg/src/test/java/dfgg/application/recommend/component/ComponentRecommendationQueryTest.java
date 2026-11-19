package dfgg.application.recommend.component;

import static dfgg.application.recommend.context.TestCompositions.NO_TAGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.ItemInventory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ComponentRecommendationQueryTest {
    @Test
    void captures_target_order_without_sharing_mutable_list() {
        var targets = new ArrayList<>(List.of(6333, 3071));
        var inventory = ItemInventory.empty().add(1036).add(1036);
        var query = new ComponentRecommendationQuery(81, targets, inventory, 0, 0,
                ChampionPosition.BOTTOM, "16.18", NO_TAGS);
        targets.clear();

        assertThat(query.targetItemIds()).containsExactly(6333, 3071);
        assertThatThrownBy(() -> query.targetItemIds().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(query.inventory().quantityOf(1036)).isEqualTo(2);
        assertThat(query.inventory().bootsInQuestSlot()).isFalse();
        assertThat(query.positionName()).isEqualTo("BOTTOM");
    }

    @Test
    void preserves_empty_targets_and_missing_evaluation_context() {
        var query = new ComponentRecommendationQuery(null, List.of(), ItemInventory.empty(), 0, 0,
                null, "16.18", NO_TAGS);
        assertThat(query.championId()).isNull();
        assertThat(query.targetItemIds()).isEmpty();
        assertThat(query.positionName()).isEqualTo("UNKNOWN");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void supplied_champion_id_must_be_positive(int championId) {
        assertThatThrownBy(() -> new ComponentRecommendationQuery(championId, List.of(3071),
                ItemInventory.empty(), 0, 0, ChampionPosition.TOP, "16.18", NO_TAGS))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Champion ID");
    }

    @Test
    void rejects_missing_or_invalid_targets() {
        for (List<Integer> targets : Arrays.asList(null, Arrays.asList(3071, null), List.of(0), List.of(-1))) {
            assertThatThrownBy(() -> new ComponentRecommendationQuery(81, targets, ItemInventory.empty(),
                    0, 0, ChampionPosition.TOP, "16.18", NO_TAGS))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("목표");
        }
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "0,-1"})
    void rejects_negative_gold_or_time(long gold, long time) {
        assertThatThrownBy(() -> new ComponentRecommendationQuery(81, List.of(3071), ItemInventory.empty(),
                gold, time, ChampionPosition.TOP, "16.18", NO_TAGS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "16", "16.18.1", "latest"})
    void patch_is_explicit_and_distinct_from_data_dragon_build(String patch) {
        assertThatThrownBy(() -> new ComponentRecommendationQuery(81, List.of(3071), ItemInventory.empty(),
                0, 0, ChampionPosition.TOP, patch, NO_TAGS))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("패치");
    }

    @Test
    void inventory_and_composition_are_required() {
        assertThatThrownBy(() -> new ComponentRecommendationQuery(81, List.of(3071), null,
                0, 0, ChampionPosition.TOP, "16.18", NO_TAGS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComponentRecommendationQuery(81, List.of(3071), ItemInventory.empty(),
                0, 0, ChampionPosition.TOP, "16.18", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("경기 조합");
    }
}
