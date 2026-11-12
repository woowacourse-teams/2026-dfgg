package dfgg.application.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.domain.match.RawMatchRepository;
import dfgg.infrastructure.external.client.RiotClient;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RawMatchServiceTest {

    @Mock
    private RiotClient riotClient;

    @Mock
    private RawMatchRepository rawMatchRepository;

    @InjectMocks
    private RawMatchService rawMatchService;

    @Test
    void 저장된_매치_ID를_조회한다() {
        when(rawMatchRepository.findExistingMatchIds(Set.of("KR_1", "KR_2")))
                .thenReturn(Set.of("KR_1"));

        Set<String> matchIds = rawMatchService.findExistingMatchIds(Set.of("KR_1", "KR_2"));

        assertThat(matchIds).containsExactly("KR_1");
    }

    @Test
    void Riot_API에서_매치_원본을_조회하고_저장한다() {
        when(riotClient.getRawMatch("KR_1")).thenReturn("{\"match\":1}");
        when(rawMatchRepository.insertIfAbsent("KR_1", "{\"match\":1}"))
                .thenReturn(1);

        boolean collected = rawMatchService.collectRawMatch("KR_1");

        assertThat(collected).isTrue();
        verify(riotClient).getRawMatch("KR_1");
        verify(rawMatchRepository).insertIfAbsent("KR_1", "{\"match\":1}");
    }
}
