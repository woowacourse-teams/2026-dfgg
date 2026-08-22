package dfgg.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dfgg.application.player.RiotPlayerSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RiotPlayerControllerTest {

    private RiotPlayerSyncService riotPlayerSyncService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        riotPlayerSyncService = mock(RiotPlayerSyncService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RiotPlayerController(riotPlayerSyncService))
                .build();
    }

    @Test
    void 기본값으로_플래티넘_1티어의_첫_페이지를_동기화한다() throws Exception {
        mockMvc.perform(post("/admin/riot/players"))
                .andExpect(status().isNoContent());

        verify(riotPlayerSyncService).syncLeagueEntries(
                "RANKED_SOLO_5x5",
                "PLATINUM",
                "I",
                1
        );
    }

    @Test
    void 요청_파라미터로_수집_대상을_지정한다() throws Exception {
        mockMvc.perform(post("/admin/riot/players")
                        .param("queue", "RANKED_FLEX_SR")
                        .param("tier", "EMERALD")
                        .param("division", "IV")
                        .param("page", "3"))
                .andExpect(status().isNoContent());

        verify(riotPlayerSyncService).syncLeagueEntries(
                "RANKED_FLEX_SR",
                "EMERALD",
                "IV",
                3
        );
    }
}
