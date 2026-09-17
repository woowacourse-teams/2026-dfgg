package dfgg.application.champion;

import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.storage.S3ImageStorage;
import org.springframework.stereotype.Service;

@Service
public class ChampionImageService {
    private final DataDragonClient dataDragonClient;
    private final S3ImageStorage storage;

    public ChampionImageService(DataDragonClient dataDragonClient, S3ImageStorage storage) {
        this.dataDragonClient = dataDragonClient;
        this.storage = storage;
    }

    public String store(String version, String dataVersion, String filename) {
        if (filename == null || !filename.matches("[A-Za-z0-9_-]+\\.png")) {
            throw new IllegalStateException("[Error] 챔피언 이미지 파일명이 올바르지 않습니다.");
        }
        String key = "images/" + version + "/champions/" + filename;
        return storage.store(key, () -> dataDragonClient.getChampionImage(dataVersion, filename));
    }
}
