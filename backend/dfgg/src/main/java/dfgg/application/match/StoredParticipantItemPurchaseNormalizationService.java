package dfgg.application.match;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.item.ItemPurchaseTypeClassifier;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 저장된 Raw Match와 Timeline의 모든 구매를 경기 패치에 맞춰 정규화한다. */
@Service
public class StoredParticipantItemPurchaseNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(StoredParticipantItemPurchaseNormalizationService.class);
    private static final Pattern GAME_VERSION = Pattern.compile("^([0-9]+\\.[0-9]+)\\.[0-9]+(?:\\..*)?$");

    private final RawMatchRepository rawMatchRepository;
    private final DataDragonClient dataDragonClient;
    private final ItemPurchaseTypeClassifier classifier;
    private final ParticipantItemPurchaseNormalizationService normalizationService;
    private final ObjectMapper objectMapper;

    public StoredParticipantItemPurchaseNormalizationService(RawMatchRepository rawMatchRepository,
                                               DataDragonClient dataDragonClient,
                                               ItemPurchaseTypeClassifier classifier,
                                               ParticipantItemPurchaseNormalizationService normalizationService,
                                               ObjectMapper objectMapper) {
        this.rawMatchRepository = rawMatchRepository;
        this.dataDragonClient = dataDragonClient;
        this.classifier = classifier;
        this.normalizationService = normalizationService;
        this.objectMapper = objectMapper;
    }

    /** Raw와 Timeline이 모두 저장된 경기를 조회하고, 한 경기씩 독립적으로 정규화한다. */
    public PurchaseNormalizationResult normalizeAll() {
        Map<String, ClassifiedCatalog> catalogsByPatch = new HashMap<>();
        List<String> failures = new ArrayList<>();
        int succeeded = 0;
        int savedPurchases = 0;
        List<String> matchIds = rawMatchRepository.findMatchIdsWithTimeline();
        for (String matchId : matchIds) {
            MatchContext context;
            try {
                RawMatch rawMatch = rawMatchRepository.findById(matchId)
                        .orElseThrow(() -> new IllegalStateException("저장된 Raw Match가 없습니다."));
                context = contextOf(rawMatch);
            } catch (RuntimeException exception) {
                recordFailure(matchId, exception, failures);
                continue;
            }
            ClassifiedCatalog catalog = catalogsByPatch.computeIfAbsent(context.patch(), this::loadCatalog);
            try {
                int purchasesForMatch = normalizationService.normalize(matchId, context.patch(), catalog.purchaseTypes(),
                        catalog.response(), context.positionsByParticipant());
                savedPurchases += purchasesForMatch;
                succeeded++;
                log.info("구매 정규화 완료: matchId={}, savedPurchases={}", matchId, purchasesForMatch);
            } catch (RuntimeException exception) {
                recordFailure(matchId, exception, failures);
            }
        }
        return new PurchaseNormalizationResult(matchIds.size(), succeeded, failures.size(), savedPurchases, failures);
    }

    /** 원천 데이터가 잘못된 경기는 기록하고 다음 경기로 넘어간다. */
    private void recordFailure(String matchId, RuntimeException exception, List<String> failures) {
        String failure = matchId + ": " + exception.getMessage();
        failures.add(failure);
        log.warn("구매 정규화 실패: {}", failure, exception);
    }

    /** 해당 패치의 빌드·아이템 분류를 한 번만 준비해 모든 경기에서 공유한다. */
    private ClassifiedCatalog loadCatalog(String patch) {
        String dataVersion = dataDragonClient.resolveItemDataVersionForPatch(patch);
        ItemResponse response = dataDragonClient.getItems(dataVersion);
        if (response == null || response.data() == null || response.data().isEmpty()
                || !patch.equals(response.version()) || !dataVersion.equals(response.dataVersion())) {
            throw new IllegalStateException("패치 " + patch + "의 Data Dragon 아이템 카탈로그가 일치하지 않습니다.");
        }
        log.info("구매 정규화 카탈로그 선택: patch={}, dataVersion={}", patch, dataVersion);
        return new ClassifiedCatalog(response, classifier.classify(response.data()));
    }

    /** Raw Match에서 경기 패치와 구매 참가자의 Riot 원시 포지션을 읽는다. */
    private MatchContext contextOf(RawMatch rawMatch) {
        try {
            JsonNode info = objectMapper.readTree(rawMatch.getRawData()).path("info");
            JsonNode versionNode = info.path("gameVersion");
            if (!versionNode.isTextual()) {
                throw new IllegalArgumentException("Raw Match에 gameVersion이 없습니다.");
            }
            Matcher matcher = GAME_VERSION.matcher(versionNode.textValue());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Raw Match의 gameVersion 형식이 올바르지 않습니다.");
            }
            return new MatchContext(matcher.group(1), positionsOf(info.path("participants")));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Raw Match JSON을 읽을 수 없습니다.", exception);
        }
    }

    private Map<Integer, String> positionsOf(JsonNode participants) {
        if (!participants.isArray() || participants.isEmpty()) {
            throw new IllegalArgumentException("Raw Match의 참가자 목록이 없습니다.");
        }
        Map<Integer, String> positions = new HashMap<>();
        for (JsonNode participant : participants) {
            JsonNode id = participant.path("participantId");
            if (!id.isIntegralNumber() || !id.canConvertToInt() || id.intValue() <= 0) {
                throw new IllegalArgumentException("Raw Match의 참가자 ID가 유효하지 않습니다.");
            }
            JsonNode teamPosition = participant.path("teamPosition");
            if (!teamPosition.isMissingNode() && !teamPosition.isNull() && !teamPosition.isTextual()) {
                throw new IllegalArgumentException("Raw Match의 참가자 포지션이 문자열이 아닙니다.");
            }
            String position = teamPosition.isTextual() ? teamPosition.textValue() : null;
            if (position != null && position.length() > 32) {
                throw new IllegalArgumentException("Raw Match의 참가자 포지션이 32자를 초과합니다.");
            }
            if (positions.containsKey(id.intValue())) {
                throw new IllegalArgumentException("Raw Match의 참가자 ID가 중복됩니다: " + id.intValue());
            }
            positions.put(id.intValue(), position);
        }
        return Collections.unmodifiableMap(positions);
    }

    private record MatchContext(String patch, Map<Integer, String> positionsByParticipant) {
    }

    private record ClassifiedCatalog(ItemResponse response, Map<Integer, ItemPurchaseType> purchaseTypes) {
    }
}
