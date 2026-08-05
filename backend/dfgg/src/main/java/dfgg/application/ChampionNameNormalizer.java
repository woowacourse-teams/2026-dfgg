package dfgg.application;

import dfgg.common.ChampionNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import org.springframework.stereotype.Component;

/**
 * 챔피언 이름(영문 Riot 키/현지화 이름)을 DB의 챔피언으로 변환.
 * <p>
 * 조회 우선순위: 1. riot_key 대소문자 무시 일치 (e.g. "jinx" → "Jinx") 2. 현지화 이름 대소문자 무시 일치 (e.g. "징크스" → "Jinx")
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
                .or(() -> championRepository.findByNameIgnoreCase(trimmed))
                .orElseThrow(() -> new ChampionNotFoundException(trimmed));
    }
}
