package dfgg.application.recommend;

import dfgg.domain.recommendation.CoreBuildCluster;
import dfgg.domain.recommendation.CoreBuildSequence;
import dfgg.domain.stats.ChampionBuildStats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CoreBuildClusterService {

    public List<CoreBuildCluster> groupCoreBuild(List<ChampionBuildStats> observedStats) {
        Map<List<Long>, List<ChampionBuildStats>> groupedStats = new LinkedHashMap<>();

        for (ChampionBuildStats stats : observedStats) {
            CoreBuildSequence.from(stats.getItems()).ifPresent(
                    sequence -> groupedStats.computeIfAbsent(sequence.clusterKey(),
                                    ignored -> new ArrayList<>())
                            .add(stats)
            );
        }
        return groupedStats.entrySet().stream()
                .map(entry -> CoreBuildCluster.from(
                        entry.getKey(),
                        entry.getValue()
                )).toList();
    }
}
