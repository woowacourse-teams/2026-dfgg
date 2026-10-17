package dfgg.application.recommend.context;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 기존 Core 태그 집계를 공유한다. 미등록 챔피언/누락 태그는 0 기여로 유지한다. */
@Component
public class CompositionFeatureCalculator {
    private final ChampionRepository champions;

    public CompositionFeatureCalculator(ChampionRepository champions) {
        this.champions = champions;
    }

    /** allies는 자기 자신을 제외한 아군이다. 한 챔피언의 여러 태그는 각각 센다. */
    public CompositionFeatures calculate(List<Long> allies, List<Long> enemies) {
        var enemy = countTags(enemies);
        var ally = countTags(allies);
        return new CompositionFeatures(count(enemy, ChampionTag.TANK), count(enemy, ChampionTag.MAGE),
                count(enemy, ChampionTag.MARKSMAN), count(enemy, ChampionTag.ASSASSIN),
                count(enemy, ChampionTag.FIGHTER), count(ally, ChampionTag.TANK), count(ally, ChampionTag.MAGE),
                count(ally, ChampionTag.MARKSMAN), count(ally, ChampionTag.SUPPORT));
    }

    private Map<ChampionTag, Long> countTags(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return champions.findAllWithTagsByChampionIdIn(ids).stream()
                .map(Champion::getChampionTags).flatMap(List::stream)
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));
    }

    private int count(Map<ChampionTag, Long> counts, ChampionTag tag) {
        return Math.toIntExact(counts.getOrDefault(tag, 0L));
    }
}
