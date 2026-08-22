package dfgg.application;

import dfgg.domain.champion.ChampionPosition;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ChampionPositionNormalizer {

    public Optional<ChampionPosition> normalize(String position) {
        if (position == null || position.isBlank()) {
            return Optional.empty();
        }

        String normalized = position.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MIDDLE" -> Optional.of(ChampionPosition.MID);
            case "UTILITY" -> Optional.of(ChampionPosition.SUPPORT);
            default -> {
                try {
                    yield Optional.of(ChampionPosition.valueOf(normalized));
                } catch (IllegalArgumentException exception) {
                    yield Optional.empty();
                }
            }
        };
    }
}
