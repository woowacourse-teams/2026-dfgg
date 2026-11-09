package dfgg.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dfgg.domain.player.Player;
import dfgg.domain.player.PlayerRepository;
import dfgg.infrastructure.external.client.RiotClient;
import dfgg.infrastructure.external.dto.LeagueEntryResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
@Transactional
class RiotPlayerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RiotClient riotClient;

    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void Riot_리그_응답에서_플레이어를_저장한다() throws Exception {
        when(riotClient.getLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenReturn(List.of(new LeagueEntryResponse(
                        "puuid-1",
                        "RANKED_SOLO_5x5",
                        "PLATINUM",
                        "I",
                        50,
                        20,
                        10
                )));

        mockMvc.perform(post("/admin/riot/players"))
                .andExpect(status().isNoContent());

        Player player = playerRepository.findById("puuid-1").orElseThrow();
        assertThat(player.getPlatform()).isEqualTo("KR");
    }

    @Test
    void 페이지가_양수가_아니면_요청을_거부한다() throws Exception {
        mockMvc.perform(post("/admin/riot/players")
                        .param("page", "0"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(riotClient);
    }
}
