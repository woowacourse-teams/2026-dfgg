package dfgg.application.champion;

import dfgg.domain.image.ImageKeys;
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

    /**
     * @param riotKey  저장 키. 응답 URL과 같은 규칙({@link ImageKeys})을 쓴다
     * @param filename ddragon 원본 파일명. 내려받을 때만 쓴다
     */
    public void store(String version, String dataVersion, String riotKey, String filename) {
        if (filename == null || !filename.matches("[A-Za-z0-9_-]+\\.png")) {
            throw new IllegalStateException("[Error] 챔피언 이미지 파일명이 올바르지 않습니다.");
        }
        storage.store(ImageKeys.champion(version, riotKey),
                () -> dataDragonClient.getChampionImage(dataVersion, filename));
    }
}
