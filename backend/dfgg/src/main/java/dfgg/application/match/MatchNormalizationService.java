package dfgg.application.match;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.item.ItemService;
import dfgg.application.player.RiotPlayerSyncService;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.player.Player;
import dfgg.domain.player.PlayerRepository;
import dfgg.infrastructure.external.dto.MatchParticipant;
import dfgg.infrastructure.external.dto.MatchResponse;
import dfgg.infrastructure.external.dto.MatchTimelineResponse;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집이 끝난 Riot Match를 추천 통계에 사용할 수 있는 형태로 정규화하고 저장한다.
 *
 * <p>정규화의 전체 흐름은 다음과 같다.
 * <ol>
 *     <li>Raw Match와 Raw Timeline이 모두 준비된 매치를 조회한다.</li>
 *     <li>저장된 참가자 티어를 우선 사용하고, 없는 참가자만 Riot API로 동기화한다.</li>
 *     <li>원본 JSON에서 최종 아이템과 구매 순서를 계산한다.</li>
 *     <li>추천 통계에 필요한 참가자 데이터만 {@link NormalizedMatch}로 만든다.</li>
 *     <li>정규화된 참가자 전체를 한 트랜잭션으로 저장한다.</li>
 * </ol>
 */
@Service
public class MatchNormalizationService {

    private static final int NORMALIZATION_BATCH_SIZE = 100;
    private static final String BOTTOM_POSITION = "BOTTOM";
    private static final String UNRANKED_TIER = "UNRANKED";

    // 2026 역할 퀘스트로 자동 업그레이드된 3티어 신발을
    // 플레이어가 Timeline에서 실제로 구매한 2티어 신발 ID로 되돌린다.
    // 그래야 Match의 최종 아이템과 Timeline의 구매 이벤트를 같은 ID로 비교할 수 있다.
    private static final Map<Integer, Integer> PURCHASED_BOOT_BY_TIER_THREE_BOOT = Map.of(
            3168, 3008,
            3170, 3009,
            3171, 3158,
            3172, 3006,
            3173, 3111,
            3174, 3047,
            3175, 3020,
            3176, 3010
    );

    private final ObjectMapper objectMapper;
    private final CoreItemPurchaseOrderCalculator purchaseOrderCalculator;
    private final PlayerRepository playerRepository;
    private final NormalizedMatchParticipantRepository participantRepository;
    private final RawMatchRepository rawMatchRepository;
    private final RawMatchTimelineRepository rawMatchTimelineRepository;
    private final ItemService itemService;
    private final RiotPlayerSyncService riotPlayerSyncService;

    public MatchNormalizationService(
            ObjectMapper objectMapper,
            CoreItemPurchaseOrderCalculator purchaseOrderCalculator,
            PlayerRepository playerRepository,
            NormalizedMatchParticipantRepository participantRepository,
            RawMatchRepository rawMatchRepository,
            RawMatchTimelineRepository rawMatchTimelineRepository,
            ItemService itemService,
            RiotPlayerSyncService riotPlayerSyncService
    ) {
        this.objectMapper = objectMapper;
        this.purchaseOrderCalculator = purchaseOrderCalculator;
        this.playerRepository = playerRepository;
        this.participantRepository = participantRepository;
        this.rawMatchRepository = rawMatchRepository;
        this.rawMatchTimelineRepository = rawMatchTimelineRepository;
        this.itemService = itemService;
        this.riotPlayerSyncService = riotPlayerSyncService;
    }

    /**
     * 아직 정규화하지 않은 매치 ID를 커서 다음부터 한 묶음 조회한다.
     * Raw Match와 Raw Timeline이 모두 존재하는 매치만 반환한다.
     */
    public List<String> findPendingMatchIdsAfter(String cursor) {
        return rawMatchRepository.findMatchIdsReadyForNormalizationAfter(
                cursor,
                PageRequest.of(0, NORMALIZATION_BATCH_SIZE)
        );
    }

    /**
     * 저장된 Raw Match와 Raw Timeline을 읽고 한 매치를 정규화한다.
     * 저장된 티어가 없는 참가자의 API 호출은 DB 저장 트랜잭션을 시작하기 전에 모두 끝낸다.
     */
    public NormalizedMatch normalize(String matchId) {
        StoredMatchData storedMatch = loadStoredMatch(matchId);

        List<String> participantPuuids = findParticipantPuuids(storedMatch.rawMatchData());
        if (participantPuuids.isEmpty()) {
            throw new IllegalArgumentException("match participant puuids must not be empty: " + matchId);
        }
        syncMissingPlayerTiers(participantPuuids);

        return normalizeStoredMatch(matchId, storedMatch);
    }

    /**
     * 저장된 Raw Match, Raw Timeline, Player 티어를 우선 사용해 한 매치를 정규화한다.
     * 저장된 티어가 없는 참가자만 동기화해 대량 재집계의 Riot API 호출을 최소화한다.
     */
    public NormalizedMatch normalizeForRebuild(String matchId) {
        StoredMatchData storedMatch = loadStoredMatch(matchId);
        List<String> participantPuuids = findParticipantPuuids(storedMatch.rawMatchData());
        if (participantPuuids.isEmpty()) {
            throw new IllegalArgumentException("match participant puuids must not be empty: " + matchId);
        }

        syncMissingPlayerTiers(participantPuuids);
        return normalizeStoredMatch(matchId, storedMatch);
    }

    /**
     * 참가자별 실제 티어를 조회하지 않고, 수집 시작점의 티어를 매치 전체의 표본 티어로 사용한다.
     * 기존 실제 티어 기반 정규화 경로는 정책을 되돌릴 수 있도록 그대로 유지한다.
     */
    public NormalizedMatch normalizeAsTierSample(String matchId, String sampleTier) {
        validateSampleTier(sampleTier);
        StoredMatchData storedMatch = loadStoredMatch(matchId);
        return normalizeStoredMatch(matchId, storedMatch, sampleTier);
    }

    private void syncMissingPlayerTiers(List<String> participantPuuids) {
        List<String> missingTierPuuids = findMissingTierPuuids(participantPuuids);
        if (!missingTierPuuids.isEmpty()) {
            riotPlayerSyncService.syncPlayerTiers(missingTierPuuids);
        }
    }

    private StoredMatchData loadStoredMatch(String matchId) {
        RawMatch rawMatch = rawMatchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalStateException("raw match not found: " + matchId));
        RawMatchTimeline timeline = rawMatchTimelineRepository.findById(matchId)
                .orElseThrow(() -> new IllegalStateException("raw match timeline not found: " + matchId));
        return new StoredMatchData(rawMatch.getRawData(), timeline.getRawData());
    }

    private NormalizedMatch normalizeStoredMatch(String matchId, StoredMatchData storedMatch) {
        Set<Integer> coreItemIds = itemService.findCoreItemIds();
        return normalize(
                matchId,
                storedMatch.rawMatchData(),
                storedMatch.rawTimelineData(),
                coreItemIds
        );
    }

    private NormalizedMatch normalizeStoredMatch(
            String matchId,
            StoredMatchData storedMatch,
            String sampleTier
    ) {
        Set<Integer> coreItemIds = itemService.findCoreItemIds();
        return normalizeAsTierSample(
                matchId,
                storedMatch.rawMatchData(),
                storedMatch.rawTimelineData(),
                coreItemIds,
                sampleTier
        );
    }

    /**
     * 정규화가 끝난 매치로 DB 내용을 교체한다.
     * 삭제와 저장을 한 트랜잭션으로 묶어 중간 상태가 외부에 보이지 않게 한다.
     *
     * Raw 데이터가 변경된 매치를 replay할 때 기존 통계를 먼저 제거한 뒤 호출한다.
     */
    @Transactional
    public void save(NormalizedMatch match) {
        participantRepository.deleteByMatchId(match.matchId());
        participantRepository.flush();
        participantRepository.saveAll(match.participants());
    }

    /**
     * 매치 원본과 Timeline 원본을 참가자별 추천 통계용 데이터로 정규화한다.
     *
     * @param matchId         정규화할 Riot Match ID
     * @param rawMatchData    Riot Match API 원본 JSON
     * @param rawTimelineData Riot Timeline API 원본 JSON
     * @param coreItemIds     추천 후보로 인정할 코어 아이템 ID 목록
     */
    public NormalizedMatch normalize(
            String matchId,
            String rawMatchData,
            String rawTimelineData,
            Collection<Integer> coreItemIds
    ) {
        return normalize(
                matchId,
                rawMatchData,
                rawTimelineData,
                coreItemIds,
                null
        );
    }

    /**
     * 원본 매치 참가자 모두에게 동일한 표본 티어를 적용한다.
     * Player 조회나 Riot 참가자별 티어 API 호출은 수행하지 않는다.
     */
    public NormalizedMatch normalizeAsTierSample(
            String matchId,
            String rawMatchData,
            String rawTimelineData,
            Collection<Integer> coreItemIds,
            String sampleTier
    ) {
        validateSampleTier(sampleTier);
        return normalize(
                matchId,
                rawMatchData,
                rawTimelineData,
                coreItemIds,
                sampleTier
        );
    }

    private NormalizedMatch normalize(
            String matchId,
            String rawMatchData,
            String rawTimelineData,
            Collection<Integer> coreItemIds,
            String sampleTier
    ) {
        // 참가자마다 같은 목록을 조회하므로 한 번만 Set으로 만들어 빠르게 포함 여부를 검사한다.
        Set<Integer> coreItems = Set.copyOf(coreItemIds);

        // 원본 JSON은 DB에 그대로 보존하고, 정규화할 때 필요한 필드만 DTO로 읽는다.
        MatchResponse match = read(rawMatchData, MatchResponse.class, "match");
        MatchTimelineResponse timeline = read(rawTimelineData, MatchTimelineResponse.class, "timeline");
        List<MatchParticipant> matchParticipants = participantsOf(match);
        if (matchParticipants.isEmpty()) {
            throw new IllegalArgumentException("match participants must not be empty");
        }

        // sampleTier가 주어지면 참가자별 Player 조회를 건너뛰고 매치 전체에 같은 티어를 적용한다.
        // 주어지지 않은 기존 경로에서는 저장된 실제 티어를 계속 사용한다.
        Map<String, String> tiersByPuuid = sampleTier == null
                ? findTiersByPuuid(matchParticipants)
                : Map.of();

        // Match 참가자 배열의 순서를 유지한다.
        // 배열 위치(index + 1)는 participantId가 없을 때 사용하는 마지막 보완값이다.
        String patch = normalizePatch(match.info().gameVersion());
        List<NormalizedMatchParticipant> participants = Stream.iterate(0, index -> index + 1)
                .limit(matchParticipants.size())
                .map(index -> normalizeParticipant(
                        matchParticipants.get(index),
                        index + 1,
                        timeline,
                        coreItems,
                        tiersByPuuid,
                        sampleTier
                ))
                .toList();

        // 패치는 세부 빌드 버전을 버리고 major.minor까지만 통계 기준으로 사용한다.
        return new NormalizedMatch(
                matchId,
                patch,
                match.info().queueId(),
                participants
        );
    }

    private NormalizedMatchParticipant normalizeParticipant(
            MatchParticipant participant,
            int fallbackParticipantId,
            MatchTimelineResponse timeline,
            Set<Integer> coreItemIds,
            Map<String, String> tiersByPuuid,
            String sampleTier
    ) {
        // participantId 결정 우선순위:
        // 1. Match 응답의 participantId
        // 2. Timeline metadata에서 PUUID가 위치한 순번
        // 3. Match 참가자 배열의 순번
        int participantId = participant.participantId() != null
                ? participant.participantId()
                : timeline.participantIdForPuuid(participant.puuid())
                        .orElse(fallbackParticipantId);

        // item0~item5와 필요한 roleBoundItem을 모은 뒤 다음 순서로 정리한다.
        // 1. 3티어 신발을 실제 구매한 신발 ID로 보정
        // 2. 코어 아이템만 유지
        // 3. 같은 아이템 ID 중복 제거
        List<Integer> finalCoreItems = itemIds(participant)
                .filter(coreItemIds::contains)
                .distinct()
                .toList();

        // 최종 코어 아이템이 Timeline에서 언제 구매됐는지 복원한다.
        // 하나라도 구매 이력을 확인할 수 없으면 Optional.empty()가 반환된다.
        var purchaseOrder = purchaseOrderCalculator.calculate(timeline, participantId, finalCoreItems, coreItemIds);

        return new NormalizedMatchParticipant(
                participant.puuid(),
                participantId,
                participant.championId(),
                participant.teamId(),
                participant.teamPosition(),
                sampleTier != null
                        ? sampleTier
                        : tiersByPuuid.getOrDefault(participant.puuid(), UNRANKED_TIER),
                participant.win(),
                finalCoreItems,
                // 구매 순서를 완전히 복원하지 못한 경우 빈 목록으로 저장하고,
                // complete=false를 함께 남겨 통계 집계 대상에서 제외한다.
                purchaseOrder.orElse(List.of()),
                purchaseOrder.isPresent()
        );
    }

    /**
     * 매치에 참가한 고유 PUUID를 추출한다. 정규화 전에 저장된 티어의 누락 여부를 확인할 때 사용한다.
     */
    private List<String> findParticipantPuuids(String rawMatchData) {
        MatchResponse match = read(rawMatchData, MatchResponse.class, "match");
        return participantsOf(match).stream()
                .map(MatchParticipant::puuid)
                .filter(Objects::nonNull)
                .filter(puuid -> !puuid.isBlank())
                .distinct()
                .toList();
    }

    private List<String> findMissingTierPuuids(List<String> participantPuuids) {
        Set<String> puuidsWithTier = new HashSet<>();
        for (Player player : playerRepository.findAllById(participantPuuids)) {
            if (player.getTier() != null && !player.getTier().isBlank()) {
                puuidsWithTier.add(player.getPuuid());
            }
        }
        return participantPuuids.stream()
                .filter(puuid -> !puuidsWithTier.contains(puuid))
                .toList();
    }

    private List<MatchParticipant> participantsOf(MatchResponse match) {
        if (match.info() == null || match.info().participants() == null) {
            throw new IllegalArgumentException("match participants must not be null");
        }
        List<MatchParticipant> participants = match.info().participants();
        if (participants.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("match participant must not be null");
        }
        return participants;
    }

    private Map<String, String> findTiersByPuuid(List<MatchParticipant> participants) {
        // PlayerRepository.findAllById()에 전달할 참가자 식별자 목록이다.
        List<String> puuids = participants.stream()
                .map(MatchParticipant::puuid)
                .toList();

        // 비어 있는 티어는 저장하지 않는다.
        // 이 Map에 없는 참가자는 normalizeParticipant()에서 UNRANKED가 된다.
        Map<String, String> tiersByPuuid = new HashMap<>();
        for (Player player : playerRepository.findAllById(puuids)) {
            if (player.getTier() != null && !player.getTier().isBlank()) {
                tiersByPuuid.put(player.getPuuid(), player.getTier());
            }
        }
        return tiersByPuuid;
    }

    private void validateSampleTier(String sampleTier) {
        if (sampleTier == null || sampleTier.isBlank()) {
            throw new IllegalArgumentException("sampleTier must not be blank");
        }
    }

    private Stream<Integer> itemIds(MatchParticipant participant) {
        // 일반 인벤토리의 최종 보유 아이템은 item0~item5에 들어 있다.
        Stream<Integer> inventoryItemIds = Stream.of(
                participant.item0(),
                participant.item1(),
                participant.item2(),
                participant.item3(),
                participant.item4(),
                participant.item5()
        );

        // BOTTOM 역할 퀘스트 완료 후 신발은 일반 인벤토리에서 빠지고
        // roleBoundItem에 남을 수 있으므로 BOTTOM 참가자에게만 추가한다.
        Stream<Integer> roleBoundItemIds = BOTTOM_POSITION.equals(participant.teamPosition())
                ? Stream.of(participant.roleBoundItem())
                : Stream.empty();

        // 아직 코어 아이템 여부는 검사하지 않는다.
        // 먼저 신발 ID를 구매 기준으로 통일한 뒤 호출부에서 코어 아이템만 남긴다.
        return Stream.concat(inventoryItemIds, roleBoundItemIds)
                .filter(Predicate.not(Objects::isNull))
                .filter(itemId -> itemId > 0)
                .map(MatchNormalizationService::normalizeTierThreeBoot);
    }

    private static int normalizeTierThreeBoot(int itemId) {
        // 일반 아이템은 그대로 두고, 지원하는 3티어 신발만 2티어 구매 ID로 치환한다.
        return PURCHASED_BOOT_BY_TIER_THREE_BOOT.getOrDefault(itemId, itemId);
    }

    private String normalizePatch(String gameVersion) {
        if (gameVersion == null || gameVersion.isBlank()) {
            throw new IllegalArgumentException("gameVersion must not be blank");
        }
        String[] components = gameVersion.split("\\.");
        if (components.length < 2) {
            throw new IllegalArgumentException("gameVersion must contain major and minor versions");
        }

        // 예: 16.15.1.1 -> 16.15
        return components[0] + "." + components[1];
    }

    private <T> T read(String rawData, Class<T> type, String dataName) {
        if (rawData == null || rawData.isBlank()) {
            throw new IllegalArgumentException(dataName + " raw data must not be blank");
        }
        try {
            return objectMapper.readValue(rawData, type);
        } catch (JsonProcessingException exception) {
            // 잘못된 원본을 일부만 정규화하지 않고 매치 전체를 실패시킨다.
            throw new IllegalArgumentException(dataName + " raw data is invalid", exception);
        }
    }

    private record StoredMatchData(String rawMatchData, String rawTimelineData) {
    }
}
