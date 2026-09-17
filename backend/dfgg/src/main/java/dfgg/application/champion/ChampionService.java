package dfgg.application.champion;

import dfgg.common.exception.ChampionNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ChampionResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ChampionService {

    private final DataDragonClient dataDragonClient;
    private final ChampionRepository championRepository;
    private final ChampionImageService championImageService;

    public ChampionService(DataDragonClient dataDragonClient, ChampionRepository championRepository,
                           ChampionImageService championImageService) {
        this.dataDragonClient = dataDragonClient;
        this.championRepository = championRepository;
        this.championImageService = championImageService;
    }

    public void syncChampions() {
        ChampionResponse response = dataDragonClient.getChampions();

        boolean missingImage = response.data().entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("Jade_"))
                .anyMatch(entry -> entry.getValue().image() == null);
        if (missingImage) {
            throw new IllegalStateException("[Error] Data Dragon champion image metadata is missing");
        }

        List<Champion> champions = response.data().entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("Jade_"))
                .map(entry -> {
                    Long championId = Long.parseLong(entry.getValue().key());
                    championImageService.store(response.version(), response.dataVersion(),
                            entry.getValue().image().full());
                    return new Champion(
                            championId,
                            entry.getKey(),
                            Map.of("ko-KR", entry.getValue().name()),
                            entry.getValue().tags().stream()
                                    .map(ChampionTag::from)
                                    .toList()
                    );
                })
                .toList();

        championRepository.saveAll(champions);
    }

    public Champion findChampionByName(String name) {
        if (name == null || name.isBlank()) {
            throw new ChampionNotFoundException("(빈 이름)");
        }
        String trimmed = name.trim();

        return championRepository.findByRiotKeyIgnoreCase(trimmed)
                .or(() -> championRepository.findByNameIgnoreCase(trimmed))
                .orElseThrow(() -> new ChampionNotFoundException(trimmed));
    }
}
