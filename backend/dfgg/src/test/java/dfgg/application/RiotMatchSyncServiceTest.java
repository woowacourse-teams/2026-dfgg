package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.player.PlayerRepository;
import dfgg.infrastructure.external.client.RiotClient;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class RiotMatchSyncServiceTest {

    @Mock
    private RiotClient riotClient;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private RawMatchRepository rawMatchRepository;

    @Mock
    private RawMatchPersistenceService persistenceService;

    @InjectMocks
    private RiotMatchSyncService riotMatchSyncService;

    @Test
    void 플레이어의_매치_ID를_중복_제거하고_새로운_매치만_저장한다() {
        when(playerRepository.findPuuidsByPlatform("KR", PageRequest.of(1, 10)))
                .thenReturn(List.of("puuid-1", "puuid-2"));
        when(riotClient.getMatchIds("puuid-1", 0, 20))
                .thenReturn(List.of("KR_1", "KR_2"));
        when(riotClient.getMatchIds("puuid-2", 0, 20))
                .thenReturn(List.of("KR_2", "KR_3"));
        when(rawMatchRepository.findExistingMatchIds(
                new LinkedHashSet<>(List.of("KR_1", "KR_2", "KR_3"))
        )).thenReturn(Set.of("KR_2"));
        when(riotClient.getRawMatch("KR_1")).thenReturn("{\"match\":1}");
        when(riotClient.getRawMatch("KR_3")).thenReturn("{\"match\":3}");

        riotMatchSyncService.syncMatches(1, 10, 0, 20);

        verify(riotClient, never()).getRawMatch("KR_2");
        ArgumentCaptor<RawMatch> rawMatchCaptor = ArgumentCaptor.forClass(RawMatch.class);
        verify(persistenceService, times(2)).persist(rawMatchCaptor.capture());
        assertThat(rawMatchCaptor.getAllValues())
                .extracting(RawMatch::getMatchId, RawMatch::getRawData)
                .containsExactly(
                        tuple("KR_1", "{\"match\":1}"),
                        tuple("KR_3", "{\"match\":3}")
                );
    }

    @Test
    void 저장된_플레이어가_없으면_Riot_API를_호출하지_않는다() {
        when(playerRepository.findPuuidsByPlatform("KR", PageRequest.of(0, 20)))
                .thenReturn(List.of());

        riotMatchSyncService.syncMatches(0, 20, 0, 20);

        verifyNoInteractions(riotClient, rawMatchRepository, persistenceService);
    }

    @Test
    void 이미_저장된_매치는_상세_API를_호출하지_않는다() {
        when(playerRepository.findPuuidsByPlatform("KR", PageRequest.of(0, 20)))
                .thenReturn(List.of("puuid-1"));
        when(riotClient.getMatchIds("puuid-1", 0, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchRepository.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of("KR_1"));

        riotMatchSyncService.syncMatches(0, 20, 0, 20);

        verify(riotClient, never()).getRawMatch(any());
        verifyNoInteractions(persistenceService);
    }

    @Test
    void 조회_범위를_검증한다() {
        assertThatThrownBy(() -> riotMatchSyncService.syncMatches(-1, 20, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("playerPage must not be negative");
        assertThatThrownBy(() -> riotMatchSyncService.syncMatches(0, 101, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("playerCount must be between 1 and 100");
        assertThatThrownBy(() -> riotMatchSyncService.syncMatches(0, 20, -1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("start must not be negative");
        assertThatThrownBy(() -> riotMatchSyncService.syncMatches(0, 20, 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("count must be between 1 and 100");

        verifyNoInteractions(riotClient, playerRepository, rawMatchRepository, persistenceService);
    }
}
