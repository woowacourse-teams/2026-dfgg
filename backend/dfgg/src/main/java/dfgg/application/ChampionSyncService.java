package dfgg.application;

import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ChampionResponse;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChampionSyncService {

    private static final Logger log = LoggerFactory.getLogger(ChampionSyncService.class);

    private final DataDragonClient dataDragonClient;
    private final ChampionRepository championRepository;

    public ChampionSyncService(DataDragonClient dataDragonClient, ChampionRepository championRepository) {
        this.dataDragonClient = dataDragonClient;
        this.championRepository = championRepository;
    }


    public void syncChampions() {
        long startedAtNanos = System.nanoTime();
        log.info("Champion metadata sync started");
        ChampionResponse response = dataDragonClient.getChampions();

        List<Champion> champions = response.data().entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("Jade_"))
                .map(entry -> new Champion(
                        Long.parseLong(entry.getValue().key()),
                        entry.getKey(),
                        entry.getValue().name(),
                        entry.getValue().tags().stream()
                                .map(ChampionTag::from)
                                .toList()
                ))
                .toList();

        championRepository.saveAll(champions);
        log.info(
                "Champion metadata sync completed: savedChampions={}, durationMs={}",
                champions.size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
        );
    }
}
