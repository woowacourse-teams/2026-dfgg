package dfgg.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dfgg.application.rune.RuneService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuneControllerTest {

    private final RuneService runeService = mock(RuneService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RuneController(runeService)).build();

    @Test
    void 관리자_룬_동기화를_실행한다() throws Exception {
        mockMvc.perform(post("/admin/runes"))
                .andExpect(status().isNoContent());

        verify(runeService).syncRunes();
    }
}
