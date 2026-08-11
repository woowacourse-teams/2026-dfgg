package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.infrastructure.external.client.RiotClient;
import dfgg.infrastructure.external.dto.LeagueEntryResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiotPlayerSyncServiceTest {

    @Mock
    private RiotClient riotClient;

    @Mock
    private LeagueEntryPersistenceService persistenceService;

    @InjectMocks
    private RiotPlayerSyncService riotPlayerSyncService;

    @Test
    void 리그_엔트리를_조회해_플레이어를_저장한다() {
        List<LeagueEntryResponse> entries = List.of(new LeagueEntryResponse(
                "puuid-1",
                "RANKED_SOLO_5x5",
                "PLATINUM",
                "I",
                50,
                20,
                10
        ));
        when(riotClient.getLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenReturn(entries);
        when(persistenceService.persist(eq("KR"), eq(entries), any(Instant.class))).thenReturn(1);
        Instant beforeSync = Instant.now();

        RiotPlayerSyncService.SyncResult result = riotPlayerSyncService.syncLeagueEntries(
                "RANKED_SOLO_5x5", "PLATINUM", "I", 1
        );

        Instant afterSync = Instant.now();
        ArgumentCaptor<Instant> collectedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(persistenceService).persist(
                eq("KR"),
                eq(entries),
                collectedAtCaptor.capture()
        );
        assertThat(collectedAtCaptor.getValue()).isBetween(beforeSync, afterSync);
        assertThat(result.newPlayers()).isEqualTo(1);
        assertThat(result.puuids()).containsExactly("puuid-1");
    }

    @Test
    void Riot_API_조회가_실패하면_저장하지_않는다() {
        IllegalStateException exception = new IllegalStateException("API failure");
        when(riotClient.getLeagueEntries(any(), any(), any(), eq(1)))
                .thenThrow(exception);

        assertThatThrownBy(() -> riotPlayerSyncService.syncLeagueEntries(
                "RANKED_SOLO_5x5",
                "PLATINUM",
                "I",
                1
        )).isSameAs(exception);
        verifyNoInteractions(persistenceService);
    }
}
