package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.item.ItemService;
import dfgg.application.match.MatchStateTimelineReader;
import dfgg.domain.match.MatchStateTimeline;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 경기 타임라인으로 "시각 t 직전의 10명 상태"를 얼마나 만들 수 있는지와 경기당 걸린 시간을 잰다
 * (game-state 스펙 성공 기준 7). 시간은 읽기와, 내보내기처럼 Core를 산 시각마다 {@code before(t)}를 부르는 비용을
 * 나눠 잰다. {@code before(t)}는 부를 때마다 기록을 처음부터 다시 따라가기 때문이다.
 * <p>
 * ⚠️ 평가 프로필은 대상 DB에 Flyway 마이그레이션을 실행한다. 운영·공유 DB를 가리키지 않는다.
 * <p>
 * 파괴된 Core가 보유에 남는 경우도 센다. 보유 규칙은 Core 구매 순서 계산기와 같아서 파괴를 보유에서 빼지 않는다.
 * 그대로 두기로 했고(계획 Q-f), 얼마나 흔한지와 어떤 아이템인지를 보고 다음 모듈에서 판단한다.
 * <p>
 * 표본은 경기 번호가 큰(최근에 수집된) 쪽부터 고른다. 타임라인 저장소에 표본을 고르는 메서드가 없어서,
 * 운영 코드를 늘리지 않고 이 테스트에서 경기 번호만 직접 조회한다.
 * <p>
 * 실행:
 * {@code EVALUATION_DB_URL=<평가용 DB> ./gradlew evaluationTest --tests '*MatchStateRestoreProbeTest'}
 * (표본 크기는 {@code -Devaluation.matches=100})
 */
@SpringBootTest
@ActiveProfiles("evaluation")
@Tag("evaluation")
class MatchStateRestoreProbeTest {

    private static final int SAMPLE_MATCHES = Integer.getInteger("evaluation.matches", 100);
    private static final int PARTICIPANTS_PER_MATCH = 10;
    private static final int TOP_DESTROYED_ITEMS = 10;
    private static final String ITEM_PURCHASED = "ITEM_PURCHASED";
    private static final String ITEM_DESTROYED = "ITEM_DESTROYED";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RawMatchTimelineRepository rawMatchTimelineRepository;
    @Autowired private MatchStateTimelineReader reader;
    @Autowired private ItemService itemService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("경기 표본에서 상태를 만든 비율, 경기당 걸린 시간, 파괴된 Core가 보유에 남는 경우를 출력한다")
    void probeMatchStateRestore() throws Exception {
        // given
        List<String> matchIds = jdbcTemplate.queryForList(
                "SELECT match_id FROM raw_match_timelines ORDER BY match_id DESC LIMIT ?",
                String.class,
                SAMPLE_MATCHES
        );
        assertThat(matchIds).as("평가용 DB에 타임라인이 있어야 한다").isNotEmpty();
        Set<Integer> coreItemIds = itemService.findCoreItemIds();

        // when
        int restored = 0;
        long fetchNanos = 0;
        long readNanos = 0;
        long beforeNanos = 0;
        int beforeCalls = 0;
        List<String> notRestored = new ArrayList<>();
        int destroyedCoreEvents = 0;
        int destroyedCoresStillHeld = 0;
        Set<String> affectedParticipants = new HashSet<>();
        Map<Integer, Integer> stillHeldByItem = new HashMap<>();

        for (String matchId : matchIds) {
            long fetchStart = System.nanoTime();
            String rawTimeline = rawMatchTimelineRepository.findById(matchId)
                    .map(RawMatchTimeline::getRawData)
                    .orElse(null);
            fetchNanos += System.nanoTime() - fetchStart;

            long readStart = System.nanoTime();
            Optional<MatchStateTimeline> timeline = reader.read(rawTimeline, coreItemIds);
            readNanos += System.nanoTime() - readStart;

            if (timeline.isEmpty()) {
                notRestored.add(matchId);
                continue;
            }
            restored++;

            long beforeStart = System.nanoTime();
            for (JsonNode purchase : coreEventsOf(rawTimeline, ITEM_PURCHASED, coreItemIds)) {
                timeline.get().before(purchase.path("timestamp").longValue());
                beforeCalls++;
            }
            beforeNanos += System.nanoTime() - beforeStart;

            for (JsonNode destroyed : coreEventsOf(rawTimeline, ITEM_DESTROYED, coreItemIds)) {
                destroyedCoreEvents++;
                int participantId = destroyed.path("participantId").intValue();
                int itemId = destroyed.path("itemId").intValue();
                long justAfter = destroyed.path("timestamp").longValue() + 1;
                if (timeline.get().before(justAfter).of(participantId).coreItemIds().contains(itemId)) {
                    destroyedCoresStillHeld++;
                    affectedParticipants.add(matchId + "#" + participantId);
                    stillHeldByItem.merge(itemId, 1, Integer::sum);
                }
            }
        }

        // then
        int sampled = matchIds.size();
        System.out.printf("[game-state] 표본 %d경기, 상태를 만든 경기 %d (%.1f%%)%n",
                sampled, restored, percent(restored, sampled));
        System.out.printf("[game-state] 경기당 평균: 타임라인 조회 %.1fms, 상태 읽기 %.1fms%n",
                millis(fetchNanos) / sampled, millis(readNanos) / sampled);
        System.out.printf("[game-state] 상태를 만든 경기당 평균: Core 구매 시각마다 before(t) %.1f번, %.1fms%n",
                restored == 0 ? 0 : (double) beforeCalls / restored, restored == 0 ? 0 : millis(beforeNanos) / restored);
        System.out.printf("[game-state] 만들지 못한 경기(앞 10개): %s%n",
                notRestored.subList(0, Math.min(10, notRestored.size())));
        System.out.printf("[game-state] Core 파괴 이벤트 %d건 중 파괴 뒤에도 보유에 남은 것 %d건%n",
                destroyedCoreEvents, destroyedCoresStillHeld);
        System.out.printf("[game-state] 영향을 받은 참가자 %d / %d명 (%.1f%%)%n",
                affectedParticipants.size(), restored * PARTICIPANTS_PER_MATCH,
                percent(affectedParticipants.size(), restored * PARTICIPANTS_PER_MATCH));
        System.out.printf("[game-state] 보유에 남은 Core 상위 %d개(아이템 번호=건수): %s%n",
                TOP_DESTROYED_ITEMS, topItems(stillHeldByItem));
    }

    private List<JsonNode> coreEventsOf(String rawTimeline, String type, Set<Integer> coreItemIds) throws Exception {
        List<JsonNode> events = new ArrayList<>();
        for (JsonNode frame : objectMapper.readTree(rawTimeline).path("info").path("frames")) {
            for (JsonNode event : frame.path("events")) {
                if (type.equals(event.path("type").asText())
                        && coreItemIds.contains(event.path("itemId").intValue())) {
                    events.add(event);
                }
            }
        }
        return events;
    }

    private List<String> topItems(Map<Integer, Integer> countsByItem) {
        return countsByItem.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(TOP_DESTROYED_ITEMS)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
    }

    private double percent(int part, int whole) {
        return whole == 0 ? 0 : part * 100.0 / whole;
    }

    private double millis(long nanos) {
        return nanos / 1_000_000.0;
    }
}
