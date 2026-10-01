package dfgg.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dfgg.application.RiotMatchSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RiotMatchControllerTest {

    private RiotMatchSyncService riotMatchSyncService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        riotMatchSyncService = mock(RiotMatchSyncService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RiotMatchController(riotMatchSyncService))
                .build();
    }

    @Test
    void 기본_플레이어_배치와_매치_범위를_동기화한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches"))
                .andExpect(status().isNoContent());

        verify(riotMatchSyncService).syncMatches(0, 20, 0, 1);
    }

    @Test
    void 요청_파라미터로_플레이어_배치와_매치_조회_범위를_지정한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches")
                        .param("playerPage", "1")
                        .param("playerCount", "10")
                        .param("start", "20")
                        .param("count", "50"))
                .andExpect(status().isNoContent());

        verify(riotMatchSyncService).syncMatches(1, 10, 20, 50);
    }
}
