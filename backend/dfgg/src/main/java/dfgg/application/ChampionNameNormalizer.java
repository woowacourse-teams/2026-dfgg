package dfgg.application;

import dfgg.common.ChampionNotFoundException;
import dfgg.domain.champion.Champion;
import org.springframework.stereotype.Component;

/**
 * <p>
 */
@Component
public class ChampionNameNormalizer {

    private final ChampionRepository championRepository;

    public ChampionNameNormalizer(ChampionRepository championRepository) {
        this.championRepository = championRepository;
    }

    public Champion normalize(String name) {
        if (name == null || name.isBlank()) {
            throw new ChampionNotFoundException("(빈 이름)");
        }
        String trimmed = name.trim();

        return championRepository.findByRiotKeyIgnoreCase(trimmed)
                .orElseThrow(() -> new ChampionNotFoundException(trimmed));
    }
}
