package dfgg.domain.match;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComponentPolicyLookupTest {
    private static final int EZREAL = 81;
    private final ComponentPolicyLookup lookup = new ComponentPolicyLookup("16.18", "a".repeat(64),
            Map.of(new ComponentPolicyLookup.RoleKey(EZREAL, "BOTTOM"), new ComponentPolicyLookup.Prepared(1062, .85)));

    @Test void prepared_applies_by_champion_and_role_regardless_of_target() {
        assertThat(lookup.evidenceFor(EZREAL, "BOTTOM")).containsExactly(new ComponentPolicyEvidence(3070, .85, 1062));
        assertThat(lookup.evidenceFor(EZREAL, "MID")).isEmpty();
        assertThat(lookup.evidenceFor(222, "BOTTOM")).isEmpty();
    }

    @Test void disabled_lookup_and_unknown_role_produce_no_policy() {
        assertThat(ComponentPolicyLookup.disabled().evidenceFor(null, "BOTTOM")).isEmpty();
        assertThat(lookup.evidenceFor(EZREAL, "UNKNOWN")).isEmpty();
        assertThatThrownBy(() -> lookup.evidenceFor(null, "BOTTOM")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void entries_below_policy_thresholds_are_rejected() {
        assertThatThrownBy(() -> new ComponentPolicyLookup.Prepared(49, .85)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComponentPolicyLookup.Prepared(1062, .29)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComponentPolicyLookup.RoleKey(EZREAL, "ADC")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void patch_must_match() {
        assertThatCode(() -> lookup.validatePatch("16.18")).doesNotThrowAnyException();
        assertThatThrownBy(() -> lookup.validatePatch("16.19")).isInstanceOf(IllegalArgumentException.class);
    }
}
