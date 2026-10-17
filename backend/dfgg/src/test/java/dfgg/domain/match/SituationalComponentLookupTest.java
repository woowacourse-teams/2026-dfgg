package dfgg.domain.match;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SituationalComponentLookupTest {
    private static final int JINX = 222, WARWICK = 19, MUNDO = 36, SORAKA = 16, GAREN = 86;
    private final SituationalComponentLookup lookup = new SituationalComponentLookup("16.18",
            Map.of(new SituationalComponentLookup.LiftKey(3123, WARWICK, "BOTTOM"), new SituationalComponentLookup.Lift(47, 3.4),
                    new SituationalComponentLookup.LiftKey(3123, MUNDO, "BOTTOM"), new SituationalComponentLookup.Lift(58, 2.9),
                    new SituationalComponentLookup.LiftKey(3123, SORAKA, "BOTTOM"), new SituationalComponentLookup.Lift(38, 1.8),
                    new SituationalComponentLookup.LiftKey(3916, SORAKA, "BOTTOM"), new SituationalComponentLookup.Lift(40, 4.3)),
            Map.of(new SituationalComponentLookup.BaseKey(JINX, "BOTTOM", 3123), .12,
                    new SituationalComponentLookup.BaseKey(JINX, "BOTTOM", 3916), .005 + .006));
    private final SituationalThresholds defaults = new SituationalThresholds(2.0, .01);

    @Test void reason_champions_are_enemies_above_the_configured_lift_sorted_by_lift() {
        var evidence = lookup.evidenceFor(JINX, "BOTTOM", List.of(MUNDO, SORAKA, WARWICK, GAREN), defaults);
        assertThat(evidence).filteredOn(e -> e.itemId() == 3123).singleElement().satisfies(e -> {
            assertThat(e.reasonChampionIds()).containsExactly(WARWICK, MUNDO);
            assertThat(e.maxLift()).isEqualTo(3.4);
            assertThat(e.effect()).isEqualTo(SituationalComponentPolicy.Effect.GRIEVOUS_WOUNDS);
        });
    }

    @Test void configured_thresholds_change_what_fires_without_rebuilding_the_lookup() {
        var loose = new SituationalThresholds(1.5, .01);
        assertThat(lookup.evidenceFor(JINX, "BOTTOM", List.of(SORAKA), defaults))
                .extracting(SituationalComponentLookup.SituationalEvidence::itemId).containsExactly(3916);
        assertThat(lookup.evidenceFor(JINX, "BOTTOM", List.of(SORAKA), loose))
                .extracting(SituationalComponentLookup.SituationalEvidence::itemId).containsExactly(3916, 3123);
        assertThat(lookup.evidenceFor(JINX, "BOTTOM", List.of(SORAKA), new SituationalThresholds(2.0, .05)))
                .extracting(SituationalComponentLookup.SituationalEvidence::itemId).isEmpty();
    }

    @Test void no_enemy_reaction_or_no_base_rate_for_my_champion_fires_nothing() {
        assertThat(lookup.evidenceFor(JINX, "BOTTOM", List.of(GAREN), defaults)).isEmpty();
        assertThat(lookup.evidenceFor(GAREN, "BOTTOM", List.of(WARWICK), defaults)).isEmpty();
        assertThat(lookup.evidenceFor(JINX, "MID", List.of(WARWICK), defaults)).isEmpty();
        assertThat(lookup.evidenceFor(JINX, "UNKNOWN", List.of(WARWICK), defaults)).isEmpty();
    }

    @Test void thresholds_below_lookup_floors_and_entries_below_floors_are_rejected() {
        assertThatThrownBy(() -> new SituationalThresholds(1.4, .01)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SituationalThresholds(2.0, .005)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SituationalComponentLookup.Lift(29, 3.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SituationalComponentLookup.Lift(40, 1.4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SituationalComponentLookup.LiftKey(1036, WARWICK, "BOTTOM")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void patch_must_match_operating_patch() {
        assertThatCode(() -> lookup.validatePatch("16.18")).doesNotThrowAnyException();
        assertThatThrownBy(() -> lookup.validatePatch("16.19")).isInstanceOf(IllegalArgumentException.class);
    }
}
