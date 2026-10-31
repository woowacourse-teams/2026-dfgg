package dfgg.application.recommend.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.application.recommend.context.CompositionFeatureCalculator;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComponentCompositionReaderTest {

    @Test
    @DisplayName("경기 참가자 10명의 조합을 챔피언 조회 1번으로 만든다 — 참가자마다 조회하지 않는다")
    void forMatch_WhenTenParticipants_QueriesChampionsOnce() {
        var repository = mock(ChampionRepository.class);
        var champions = List.of(champion(1, ChampionTag.TANK), champion(2, ChampionTag.MAGE, ChampionTag.SUPPORT),
                champion(3, ChampionTag.MARKSMAN), champion(4, ChampionTag.ASSASSIN), champion(5, ChampionTag.FIGHTER),
                champion(6, ChampionTag.TANK, ChampionTag.FIGHTER), champion(7, ChampionTag.MAGE),
                champion(8), champion(10, ChampionTag.MARKSMAN, ChampionTag.SUPPORT));
        when(repository.findAllWithTagsByChampionIdIn(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return champions.stream().filter(c -> ids.contains(c.getChampionId())).toList();
        });
        var calculator = new CompositionFeatureCalculator(repository);
        var participants = mock(NormalizedMatchParticipantRepository.class);
        // 챔피언 9는 DB에 없다.
        var roster = List.of(participant(1, 100, 1), participant(2, 100, 2), participant(3, 100, 3),
                participant(4, 100, 4), participant(5, 100, 5), participant(6, 200, 6), participant(7, 200, 7),
                participant(8, 200, 8), participant(9, 200, 9), participant(10, 200, 10));
        when(participants.findByMatchId("M")).thenReturn(roster);

        var contexts = new ComponentCompositionReader(participants, calculator).forMatch("M");

        verify(repository, times(1)).findAllWithTagsByChampionIdIn(any());
        assertThat(contexts).hasSize(10);
        // 참가자별 값이 기존 방식(참가자마다 아군·적군을 따로 조회)과 같다.
        for (var me : roster) {
            var allies = roster.stream().filter(p -> p != me && p.getTeamId().equals(me.getTeamId()))
                    .map(p -> p.getChampionId().longValue()).toList();
            var enemies = roster.stream().filter(p -> !p.getTeamId().equals(me.getTeamId()))
                    .map(p -> p.getChampionId().longValue()).toList();
            assertThat(contexts.get(me.getParticipantId())).isEqualTo(calculator.calculate(allies, enemies));
        }
    }

    private Champion champion(long id, ChampionTag... tags) {
        return new Champion(id, "C" + id, Map.of("en", "C" + id), List.of(tags));
    }

    private NormalizedMatchParticipant participant(int id, int team, int champion) {
        var p = mock(NormalizedMatchParticipant.class);
        when(p.getParticipantId()).thenReturn(id);
        when(p.getTeamId()).thenReturn(team);
        when(p.getChampionId()).thenReturn(champion);
        return p;
    }
}
