package dfgg.application.champion;

import dfgg.common.ChampionNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ChampionResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChampionService {

    private final DataDragonClient dataDragonClient;
    private final ChampionRepository championRepository;

    public ChampionService(DataDragonClient dataDragonClient, ChampionRepository championRepository) {
        this.dataDragonClient = dataDragonClient;
        this.championRepository = championRepository;
    }

    public void syncChampions() {
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
