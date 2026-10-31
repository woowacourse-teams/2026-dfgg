package dfgg.application.recommend.context;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
        return featuresOf(ally, enemy);
    }

    /** 한 경기처럼 같은 챔피언 집합을 여러 번 셀 때, 태그를 한 번만 읽어 둔다. */
    public Map<Long, List<ChampionTag>> readTags(Collection<Long> championIds) {
        if (championIds.isEmpty()) return Map.of();
        return champions.findAllWithTagsByChampionIdIn(championIds).stream()
                .collect(Collectors.toMap(Champion::getChampionId, Champion::getChampionTags));
    }

    /** 미리 읽은 태그로 센다. 같은 챔피언 ID는 한쪽에서 한 번만 세고, 읽히지 않은 챔피언은 0 기여다. */
    public CompositionFeatures calculate(List<Long> allies, List<Long> enemies, Map<Long, List<ChampionTag>> tags) {
        return featuresOf(countTags(tagsOf(allies, tags)), countTags(tagsOf(enemies, tags)));
    }

    private Map<ChampionTag, Long> countTags(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return countTags(champions.findAllWithTagsByChampionIdIn(ids).stream().map(Champion::getChampionTags));
    }

    private Stream<List<ChampionTag>> tagsOf(List<Long> ids, Map<Long, List<ChampionTag>> tags) {
        return ids.stream().distinct().map(tags::get).filter(Objects::nonNull);
    }

    private Map<ChampionTag, Long> countTags(Stream<List<ChampionTag>> tagsPerChampion) {
        return tagsPerChampion.flatMap(List::stream).collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));
    }

    private CompositionFeatures featuresOf(Map<ChampionTag, Long> ally, Map<ChampionTag, Long> enemy) {
        return new CompositionFeatures(count(enemy, ChampionTag.TANK), count(enemy, ChampionTag.MAGE),
                count(enemy, ChampionTag.MARKSMAN), count(enemy, ChampionTag.ASSASSIN),
                count(enemy, ChampionTag.FIGHTER), count(ally, ChampionTag.TANK), count(ally, ChampionTag.MAGE),
                count(ally, ChampionTag.MARKSMAN), count(ally, ChampionTag.SUPPORT));
    }

    private int count(Map<ChampionTag, Long> counts, ChampionTag tag) {
        return Math.toIntExact(counts.getOrDefault(tag, 0L));
    }
}
