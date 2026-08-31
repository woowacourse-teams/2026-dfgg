package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.application.player.RiotPlayerSyncService;
import dfgg.domain.player.Player;
import dfgg.domain.player.PlayerRepository;
import dfgg.infrastructure.external.client.RiotClient;
import dfgg.infrastructure.external.dto.LeagueEntryResponse;
import dfgg.infrastructure.external.dto.LeagueListResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private PlayerRepository playerRepository;

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
        when(playerRepository.findById("puuid-1")).thenReturn(Optional.empty());
        Instant beforeSync = Instant.now();

        RiotPlayerSyncService.SyncResult result = riotPlayerSyncService.syncLeagueEntries(
                "RANKED_SOLO_5x5", "PLATINUM", "I", 1
        );

        Instant afterSync = Instant.now();
        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(playerCaptor.capture());
        assertThat(playerCaptor.getValue()).satisfies(player -> {
            assertThat(player.getPuuid()).isEqualTo("puuid-1");
            assertThat(player.getPlatform()).isEqualTo("KR");
            assertThat(player.getTier()).isEqualTo("PLATINUM");
            assertThat(player.getDivision()).isEqualTo("I");
            assertThat(player.getFirstSeenAt()).isBetween(beforeSync, afterSync);
            assertThat(player.getLastSeenAt()).isBetween(beforeSync, afterSync);
        });
        assertThat(result.newPlayers()).isEqualTo(1);
        assertThat(result.puuids()).containsExactly("puuid-1");
    }

    @Test
    void Master_리그의_wrapper_정보로_플레이어를_저장한다() {
        LeagueEntryResponse entry = new LeagueEntryResponse(
                "master-puuid",
                null,
                null,
                "I",
                1399,
                242,
                183
        );
        when(riotClient.getMasterLeague("RANKED_SOLO_5x5"))
                .thenReturn(new LeagueListResponse(
                        "MASTER",
                        "RANKED_SOLO_5x5",
                        List.of(entry)
                ));
        when(playerRepository.findById("master-puuid")).thenReturn(Optional.empty());

        RiotPlayerSyncService.SyncResult result = riotPlayerSyncService.syncLeagueEntries(
                "RANKED_SOLO_5x5", "MASTER", "IV", 7
        );

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(playerCaptor.capture());
        assertThat(playerCaptor.getValue()).satisfies(player -> {
            assertThat(player.getPuuid()).isEqualTo("master-puuid");
            assertThat(player.getTier()).isEqualTo("MASTER");
            assertThat(player.getDivision()).isEqualTo("I");
        });
        assertThat(result.puuids()).containsExactly("master-puuid");
        verify(riotClient, never()).getLeagueEntries(any(), any(), any(), anyInt());
    }

    @Test
    void Grandmaster_리그의_wrapper_정보로_플레이어를_저장한다() {
        LeagueEntryResponse entry = new LeagueEntryResponse(
                "grandmaster-puuid",
                null,
                null,
                "I",
                821,
                190,
                151
        );
        when(riotClient.getGrandmasterLeague("RANKED_SOLO_5x5"))
                .thenReturn(new LeagueListResponse(
                        "GRANDMASTER",
                        "RANKED_SOLO_5x5",
                        List.of(entry)
                ));
        when(playerRepository.findById("grandmaster-puuid")).thenReturn(Optional.empty());

        RiotPlayerSyncService.SyncResult result = riotPlayerSyncService.syncLeagueEntries(
                "RANKED_SOLO_5x5", "GRANDMASTER", "IV", 7
        );

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(playerCaptor.capture());
        assertThat(playerCaptor.getValue()).satisfies(player -> {
            assertThat(player.getPuuid()).isEqualTo("grandmaster-puuid");
            assertThat(player.getTier()).isEqualTo("GRANDMASTER");
            assertThat(player.getDivision()).isEqualTo("I");
        });
        assertThat(result.puuids()).containsExactly("grandmaster-puuid");
        verify(riotClient, never()).getLeagueEntries(any(), any(), any(), anyInt());
        verify(riotClient, never()).getMasterLeague(any());
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
        verifyNoInteractions(playerRepository);
    }

    @Test
    void 매치_참가자의_솔로_랭크_티어를_조회하고_저장한다() {
        LeagueEntryResponse soloRank = new LeagueEntryResponse(
                "puuid-1",
                "RANKED_SOLO_5x5",
                "PLATINUM",
                "I",
                50,
                20,
                10
        );
        LeagueEntryResponse flexRank = new LeagueEntryResponse(
                "puuid-1",
                "RANKED_FLEX_SR",
                "GOLD",
                "II",
                20,
                10,
                10
        );
        when(riotClient.getLeagueEntriesByPuuid("puuid-1"))
                .thenReturn(List.of(flexRank, soloRank));
        when(riotClient.getLeagueEntriesByPuuid("puuid-2"))
                .thenReturn(List.of());
        when(playerRepository.findById("puuid-1")).thenReturn(Optional.empty());
        when(playerRepository.findById("puuid-2")).thenReturn(Optional.empty());

        Map<String, String> tiers = riotPlayerSyncService.syncPlayerTiers(
                List.of("puuid-1", "puuid-2", "puuid-1")
        );

        assertThat(tiers).containsExactlyInAnyOrderEntriesOf(Map.of(
                "puuid-1", "PLATINUM",
                "puuid-2", "UNRANKED"
        ));
        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository, times(2)).save(playerCaptor.capture());
        assertThat(playerCaptor.getAllValues())
                .extracting(Player::getPuuid, Player::getTier, Player::getDivision)
                .containsExactly(
                        tuple("puuid-1", "PLATINUM", "I"),
                        tuple("puuid-2", "UNRANKED", "NONE")
                );
    }

    @Test
    void 이미_저장된_플레이어의_티어를_갱신한다() {
        Instant firstObservedAt = Instant.parse("2026-08-06T08:00:00Z");
        Player player = new Player("puuid-1", "KR", "GOLD", "I", firstObservedAt);
        LeagueEntryResponse entry = new LeagueEntryResponse(
                "puuid-1",
                "RANKED_SOLO_5x5",
                "PLATINUM",
                "II",
                50,
                20,
                10
        );
        when(riotClient.getLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "II", 1))
                .thenReturn(List.of(entry));
        when(playerRepository.findById("puuid-1")).thenReturn(Optional.of(player));

        RiotPlayerSyncService.SyncResult result = riotPlayerSyncService.syncLeagueEntries(
                "RANKED_SOLO_5x5",
                "PLATINUM",
                "II",
                1
        );

        assertThat(result.newPlayers()).isZero();
        assertThat(player.getTier()).isEqualTo("PLATINUM");
        assertThat(player.getDivision()).isEqualTo("II");
        assertThat(player.getFirstSeenAt()).isEqualTo(firstObservedAt);
        assertThat(player.getLastSeenAt()).isAfter(firstObservedAt);
        verify(playerRepository).save(player);
    }
}
