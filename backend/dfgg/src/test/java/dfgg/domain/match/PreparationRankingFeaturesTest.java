package dfgg.domain.match;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PreparationRankingFeaturesTest {
    @Test void evidence_uses_relative_frequency_and_log_support() {
        var pattern = new PreparationLookup.Pattern(3070, 459, 396.0 / 459, 2000, 396);
        assertThat(pattern.rankingFeatures()).containsExactly(1.0, .2295, Math.log1p(459), 396.0 / 459);
        var target = new ComponentCandidateGenerator.Candidate(null, ComponentCandidateGenerator.Source.TARGET_PATH, pattern);
        assertThat(target.preparationFeatures()).containsExactly(0.0, 0.0, 0.0, 0.0);
    }
    @Test void inconsistent_statistics_cannot_create_a_pattern() {
        assertThatThrownBy(() -> new PreparationLookup.Pattern(3070, 50, .8, 49, 40))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PreparationLookup.Pattern(3070, 50, .8, 100, 39))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
