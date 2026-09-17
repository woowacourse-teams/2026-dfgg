package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.TierScope;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.ChampionRefDto;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Checkpoint B — evidence가 단어로 나오는지 챔피언·포지션을 넓게 확인한다.
 *
 * <p>비율만 보면 "채워졌다"는 알아도 <b>맞는 이름이 채워졌는지</b>는 모른다.
 * 여기서 거는 불변식은 셋이다 — counter는 적 중에서만, ally는 아군 중에서만,
 * 그리고 둘 다 최대 2명. 하나라도 깨지면 사용자에게 엉뚱한 챔피언 이름이 나간다.
 *
 * <p>실행:
 * {@code ./gradlew evaluationTest --tests '*EvidenceSanityEvaluationTest'}
 */
@SpringBootTest
@ActiveProfiles("evaluation")
@Tag("evaluation")
class EvidenceSanityEvaluationTest {

    private static final int SAMPLE_MATCHES = Integer.getInteger("evaluation.matches", 200);
    private static final int PARTICIPANTS_PER_MATCH = 2;
    private static final int RECENT_PATCH_WINDOW = 3;
    private static final int MAXIMUM_EVIDENCE_CHAMPIONS = 2;
    /** 포지션마다 이만큼은 눈으로 볼 수 있게 리포트에 남긴다. */
    private static final int SAMPLES_PER_POSITION = 6;

    @Autowired private NextItemRecommendationService recommendationService;
    @Autowired private NormalizedMatchParticipantRepository participantRepository;
    @Autowired private ChampionRepository championRepository;
    @Autowired private ItemStatsAggregationService aggregationService;
    @Autowired private TierScope tierScope;

    private final SnapshotQueryBuilder snapshotQueryBuilder = new SnapshotQueryBuilder();
    private final GameSplit gameSplit = new GameSplit(0.8);
    private ParticipantSampler participantSampler;

    @BeforeEach
    void prepareSampler() {
        participantSampler = new ParticipantSampler(tierScope);
    }

    @Test
    @DisplayName("evidence로 지목한 챔피언이 실제로 그 편에 있고, 챔피언·포지션 전반에서 나온다")
    void evidenceNamesOnlyChampionsFromTheRightSide() {
        aggregationService.aggregate(RECENT_PATCH_WINDOW);
        Map<Long, String> championNames = championRepository.findAll().stream()
                .collect(Collectors.toMap(Champion::getChampionId, champion -> champion.getName().get("ko-KR"), (a, b) -> a));

        Map<String, Map<String, String>> samplesByPosition = new LinkedHashMap<>();
        Map<String, Integer> championsWithEvidence = new LinkedHashMap<>();
        int slots = 0;
        int slotsWithEvidence = 0;

        for (SnapshotQuery snapshot : collectTestSnapshots()) {
            NextItemRecommendationRequest request = toRequest(snapshot, championNames);
            if (request == null) {
                continue;
            }
            Set<Long> allyIds = Set.copyOf(snapshot.query().allyChampionIds());
            Set<Long> enemyIds = Set.copyOf(snapshot.query().enemyChampionIds());
            String position = snapshot.query().position().name();
            String myName = championNames.get(snapshot.query().myChampionId());

            NextItemRecommendationResponse response;
            try {
                response = recommendationService.recommendNextItem(request);
            } catch (RuntimeException exception) {
                continue;
            }

            for (RecommendedItemDto item : response.recommendedItems()) {
                slots++;
                List<ChampionRefDto> counter = item.description().counter();
                List<ChampionRefDto> ally = item.description().ally();

                assertThat(ids(counter))
                        .as("%s(%s) → %s 의 counter 는 적 중에서만 나와야 한다",
                                myName, position, item.name())
                        .isSubsetOf(enemyIds);
                assertThat(ids(ally))
                        .as("%s(%s) → %s 의 ally 는 아군 중에서만 나와야 한다",
                                myName, position, item.name())
                        .isSubsetOf(allyIds);
                assertThat(counter).hasSizeLessThanOrEqualTo(MAXIMUM_EVIDENCE_CHAMPIONS);
                assertThat(ally).hasSizeLessThanOrEqualTo(MAXIMUM_EVIDENCE_CHAMPIONS);
                assertThat(ids(counter)).doesNotHaveDuplicates();
                assertThat(ids(ally)).doesNotHaveDuplicates();

                if (counter.isEmpty() && ally.isEmpty()) {
                    continue;
                }
                slotsWithEvidence++;
                championsWithEvidence.merge(myName, 1, Integer::sum);
                // 같은 참가자의 연속 구매 단계가 이어져 들어오므로 챔피언당 하나만 남긴다.
                // 그러지 않으면 포지션마다 챔피언 하나가 표본을 통째로 차지한다.
                Map<String, String> samples = samplesByPosition.computeIfAbsent(
                        position, key -> new LinkedHashMap<>());
                if (samples.size() < SAMPLES_PER_POSITION && !samples.containsKey(myName)) {
                    samples.put(myName, String.format("| %s | %s | %s | %s | %s |",
                            myName, item.name(), names(counter), names(ally),
                            String.join(", ", item.description().traits())));
                }
            }
        }

        System.out.println(render(samplesByPosition, championsWithEvidence, slots, slotsWithEvidence));
        assertThat(slots).as("측정한 추천 칸").isPositive();
        assertThat(samplesByPosition.keySet())
                .as("모든 포지션에서 evidence가 나와야 한다")
                .containsExactlyInAnyOrder("TOP", "JUNGLE", "MID", "BOTTOM", "SUPPORT");
    }

    private List<Long> ids(List<ChampionRefDto> refs) {
        return refs.stream().map(ChampionRefDto::id).toList();
    }

    private String names(List<ChampionRefDto> refs) {
        return refs.isEmpty() ? "-" : refs.stream()
                .map(ChampionRefDto::name).collect(Collectors.joining(", "));
    }

    private String render(Map<String, Map<String, String>> samplesByPosition,
                          Map<String, Integer> championsWithEvidence, int slots, int withEvidence) {
        StringBuilder out = new StringBuilder("# Checkpoint B — evidence 표본\n\n");
        out.append(String.format("추천 칸 %,d / evidence 있는 칸 %,d (%.1f%%) / "
                        + "evidence가 나온 챔피언 %d종%n%n",
                slots, withEvidence, slots == 0 ? 0.0 : 100.0 * withEvidence / slots,
                championsWithEvidence.size()));
        samplesByPosition.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            out.append(String.format("## %s%n%n", entry.getKey()));
            out.append("| 내 챔피언 | 아이템 | counter | ally | traits |\n|---|---|---|---|---|\n");
            entry.getValue().values().forEach(line -> out.append(line).append("\n"));
            out.append("\n");
        });
        new EvaluationReportWriter().write(Path.of("../tasks/eval-evidence-sanity.md"), out.toString());
        return out.toString();
    }

    private NextItemRecommendationRequest toRequest(
            SnapshotQuery snapshot, Map<Long, String> championNames) {
        List<ChampionDto> allies = championDtos(snapshot.query().allyChampionIds(), championNames);
        List<ChampionDto> enemies = championDtos(snapshot.query().enemyChampionIds(), championNames);
        String myName = championNames.get(snapshot.query().myChampionId());
        if (myName == null || allies == null || enemies == null) {
            return null;
        }
        return new NextItemRecommendationRequest(
                new ChampionDto(myName, snapshot.query().position().name()),
                snapshot.query().purchasedItemIds(), allies, enemies,
                snapshot.query().tier(), snapshot.query().patch());
    }

    private List<ChampionDto> championDtos(List<Long> championIds, Map<Long, String> names) {
        List<ChampionDto> dtos = new ArrayList<>();
        for (Long championId : championIds) {
            String name = names.get(championId);
            if (name == null) {
                return null;
            }
            dtos.add(new ChampionDto(name, "MID"));
        }
        return dtos;
    }

    private List<SnapshotQuery> collectTestSnapshots() {
        List<SnapshotQuery> snapshots = new ArrayList<>();
        int page = 0;
        int sampled = 0;
        while (sampled < SAMPLE_MATCHES) {
            List<String> matchIds = participantRepository
                    .findSampledMatchIds(tierScope.values(), PageRequest.of(page++, 500));
            if (matchIds.isEmpty()) {
                break;
            }
            for (String matchId : matchIds) {
                if (sampled >= SAMPLE_MATCHES) {
                    break;
                }
                if (gameSplit.isTrain(matchId)) {
                    continue;
                }
                List<NormalizedMatchParticipant> participants = participantRepository.findByMatchId(matchId);
                int used = 0;
                for (NormalizedMatchParticipant participant
                        : participantSampler.sample(participants, matchId, participants.size())) {
                    if (used >= PARTICIPANTS_PER_MATCH) {
                        break;
                    }
                    List<SnapshotQuery> built = snapshotQueryBuilder.build(
                            participants, participant.getPuuid(), participant.getPatch());
                    if (!built.isEmpty()) {
                        used++;
                        snapshots.addAll(built);
                    }
                }
                if (used > 0) {
                    sampled++;
                }
            }
        }
        return snapshots;
    }
}
