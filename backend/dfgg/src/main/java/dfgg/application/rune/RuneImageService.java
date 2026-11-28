package dfgg.application.rune;

import dfgg.domain.image.ImageKeys;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.storage.S3ImageStorage;
import org.springframework.stereotype.Service;

@Service
public class RuneImageService {

    private final DataDragonClient dataDragonClient;
    private final S3ImageStorage storage;

    public RuneImageService(DataDragonClient dataDragonClient, S3ImageStorage storage) {
        this.dataDragonClient = dataDragonClient;
        this.storage = storage;
    }

    /**
     * @param patch    저장 폴더. 응답 URL과 같은 규칙({@link ImageKeys})을 쓴다
     * @param iconPath Data Dragon 원본 경로. 내려받을 때만 쓴다
     */
    public void store(String patch, int runeId, String iconPath) {
        storage.store(ImageKeys.rune(patch, runeId), () -> dataDragonClient.getRuneImage(iconPath));
    }
}
