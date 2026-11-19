package dfgg.application.recommend.component;

import dfgg.application.recommend.context.CompositionFeatureCalculator;
import dfgg.application.recommend.context.CompositionFeatures;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 구매 결과와 무관한 경기 참가자 정보만 읽는다. 조합은 경기 중 변하지 않아 참가자별로 재사용하고, 챔피언 태그는 경기당 한 번만 읽는다. */
@Component
public class ComponentCompositionReader {
    private final NormalizedMatchParticipantRepository participants;
    private final CompositionFeatureCalculator calculator;

    public ComponentCompositionReader(NormalizedMatchParticipantRepository participants,
            CompositionFeatureCalculator calculator) {
        this.participants = participants;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public Map<Integer, CompositionFeatures> forMatch(String matchId) {
        var roster = participants.findByMatchId(matchId);
        if (roster.isEmpty()) throw new IllegalArgumentException("경기 조합 참가자가 없습니다: " + matchId);
        var tags = calculator.readTags(roster.stream().map(p -> p.getChampionId()).filter(Objects::nonNull)
                .map(Integer::longValue).distinct().toList());
        var contexts = new LinkedHashMap<Integer, CompositionFeatures>();
        for (var me : roster) {
            if (me.getParticipantId() == null || me.getTeamId() == null || me.getChampionId() == null) {
                throw new IllegalArgumentException("경기 조합 참가자 식별 정보가 없습니다: " + matchId);
            }
            var allies = roster.stream().filter(p -> !Objects.equals(p.getParticipantId(), me.getParticipantId()))
                    .filter(p -> Objects.equals(p.getTeamId(), me.getTeamId()))
                    .map(p -> p.getChampionId().longValue()).toList();
            var enemies = roster.stream().filter(p -> !Objects.equals(p.getTeamId(), me.getTeamId()))
                    .map(p -> p.getChampionId().longValue()).toList();
            if (contexts.putIfAbsent(me.getParticipantId(), calculator.calculate(allies, enemies, tags)) != null) {
                throw new IllegalArgumentException("중복 경기 참가자: " + matchId);
            }
        }
        return Map.copyOf(contexts);
    }
}
