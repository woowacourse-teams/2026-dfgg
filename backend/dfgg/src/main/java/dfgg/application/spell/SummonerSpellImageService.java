package dfgg.application.spell;

import dfgg.domain.image.ImageKeys;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.storage.S3ImageStorage;
import org.springframework.stereotype.Service;

@Service
public class SummonerSpellImageService {

    private final DataDragonClient dataDragonClient;
    private final S3ImageStorage storage;

    public SummonerSpellImageService(DataDragonClient dataDragonClient, S3ImageStorage storage) {
        this.dataDragonClient = dataDragonClient;
        this.storage = storage;
    }

    /**
     * @param riotKey  저장 키. 응답 URL과 같은 규칙({@link ImageKeys})을 쓴다
     * @param filename ddragon 원본 파일명. 내려받을 때만 쓴다
     */
    public void store(String version, String dataVersion, String riotKey, String filename) {
        if (filename == null || !filename.matches("[A-Za-z0-9_-]+\\.png")) {
            throw new IllegalStateException("[Error] 소환사 주문 이미지 파일명이 올바르지 않습니다.");
        }
        storage.store(ImageKeys.summonerSpell(version, riotKey),
                () -> dataDragonClient.getSummonerSpellImage(dataVersion, filename));
    }
}
