package dfgg.application.patch;

import dfgg.domain.image.ImageKeys;
import dfgg.domain.match.PatchVersion;
import dfgg.infrastructure.storage.S3ImageStorage;

/**
 * 패치 그림이 S3에 다 올라갔는지 본다. 폴더가 있는 것만으로는 업로드가 중간에 끊겼을 수 있어,
 * 올리는 서버가 모두 올린 뒤 남기는 완료 표시로 판단한다.
 */
public class PatchImageChecker {

    private final S3ImageStorage storage;

    public PatchImageChecker(S3ImageStorage storage) {
        this.storage = storage;
    }

    public boolean isReady(PatchVersion patch) {
        return storage.exists(ImageKeys.ready(patch.value()));
    }

    public void markReady(PatchVersion patch) {
        storage.mark(ImageKeys.ready(patch.value()), patch.value());
    }
}
