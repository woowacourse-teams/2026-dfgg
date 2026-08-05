package dfgg.application;

import dfgg.common.ChampionNotFoundException;
import dfgg.domain.team.ChampionRepository;
import dfgg.domain.champion.Champion;
import org.springframework.stereotype.Component;

/**
 * 챔피언 이름(영문/한국어)을 DB의 riot_key로 변환.
 * <p>
 * 조회 우선순위:
 * 1. riot_key 대소문자 무시 일치 (e.g. "jinx" → "Jinx")
 * 2. name_en 대소문자 무시 일치 (e.g. "kai'sa" → "Kaisa")
 * 3. name_ko 일치 (e.g. "징크스" → "Jinx")
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
                .or(() -> championRepository.findByNameEnIgnoreCase(trimmed))
                .or(() -> championRepository.findByNameKo(trimmed))
                .orElseThrow(() -> new ChampionNotFoundException(trimmed));
    }
}
