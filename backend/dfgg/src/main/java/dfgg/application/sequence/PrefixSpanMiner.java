package dfgg.application.sequence;

import dfgg.domain.sequence.SequentialPattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PrefixSpanMiner {

    public List<SequentialPattern> mine(List<List<Long>> sequences, int minSupport) {
        List<SequentialPattern> patterns = new ArrayList<>();
        mine(List.of(), sequences, minSupport, patterns);
        return patterns;
    }

    public boolean matches(List<Long> sequence, List<Long> patternItems) {
        int cursor = 0;
        for (Long item : patternItems) {
            int index = sequence.subList(cursor, sequence.size()).indexOf(item);
            if (index < 0) {
                return false;
            }
            cursor += index + 1;
        }
        return true;
    }

    private void mine(
            List<Long> prefix,
            List<List<Long>> projectedDatabase,
            int minSupport,
            List<SequentialPattern> patterns
    ) {
        for (Map.Entry<Long, Integer> entry : countItemSupport(projectedDatabase).entrySet()) {
            Long item = entry.getKey();
            int support = entry.getValue();
            if (support < minSupport) {
                continue;
            }
            List<Long> extendedPrefix = new ArrayList<>(prefix);
            extendedPrefix.add(item);
            patterns.add(new SequentialPattern(extendedPrefix, support));
            mine(extendedPrefix, project(projectedDatabase, item), minSupport, patterns);
        }
    }

    private Map<Long, Integer> countItemSupport(List<List<Long>> database) {
        Map<Long, Integer> support = new LinkedHashMap<>();
        for (List<Long> sequence : database) {
            for (Long item : new LinkedHashSet<>(sequence)) {
                support.merge(item, 1, Integer::sum);
            }
        }
        return support;
    }

    private List<List<Long>> project(List<List<Long>> database, Long item) {
        List<List<Long>> projected = new ArrayList<>();
        for (List<Long> sequence : database) {
            int index = sequence.indexOf(item);
            if (index >= 0) {
                projected.add(sequence.subList(index + 1, sequence.size()));
            }
        }
        return projected;
    }
}
