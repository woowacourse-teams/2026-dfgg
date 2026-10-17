package dfgg.application.item;

import dfgg.domain.match.ItemPurchaseType;
import dfgg.infrastructure.external.client.DataDragonClient;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 호출자가 패치와 Data Dragon 빌드를 명시한다. 과거 경기를 최신 카탈로그로 대체하지 않는다. */
@Service
public class ItemPurchaseTypeMapService {

    private final DataDragonClient client;
    private final ItemPurchaseTypeClassifier classifier;

    public ItemPurchaseTypeMapService(DataDragonClient client, ItemPurchaseTypeClassifier classifier) {
        this.client = client;
        this.classifier = classifier;
    }

    public Map<Integer, ItemPurchaseType> classifyItemsForPatch(String patch, String dataVersion) {
        validatePatchAndDataVersion(patch, dataVersion);
        var response = client.getItems(dataVersion);
        if (!dataVersion.equals(response.dataVersion()) || !patch.equals(response.version())) {
            throw new IllegalStateException("요청한 패치와 아이템 카탈로그 버전이 다릅니다.");
        }
        return classifier.classify(response.data());
    }

    private void validatePatchAndDataVersion(String patch, String dataVersion) {
        if (patch == null || !patch.matches("[0-9]+\\.[0-9]+") || dataVersion == null
                || !dataVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || !dataVersion.startsWith(patch + ".")) {
            throw new IllegalArgumentException("게임 패치와 해당 패치의 Data Dragon 빌드가 필요합니다. 예: 16.18, 16.18.1");
        }
    }
}
