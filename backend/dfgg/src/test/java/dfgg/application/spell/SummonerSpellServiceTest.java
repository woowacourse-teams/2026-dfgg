package dfgg.application.spell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import dfgg.domain.spell.SummonerSpell;
import dfgg.domain.spell.SummonerSpellRepository;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.infrastructure.external.dto.SummonerSpellResponse;
import dfgg.infrastructure.external.dto.SummonerSpellResponse.SummonerSpellData;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SummonerSpellService.class)
class SummonerSpellServiceTest {

    @MockitoBean
    private DataDragonClient dataDragonClient;

    @MockitoBean
    private SummonerSpellImageService spellImageService;

    @Autowired
    private SummonerSpellService spellService;

    @Autowired
    private SummonerSpellRepository spellRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("협곡(CLASSIC) 주문만 저장한다 — 아레나 점멸처럼 영문 이름이 겹치는 다른 모드 주문은 뺀다")
    void syncSpells_WhenOtherModeSpellsExist_SaveOnlyClassic() {
        // given
        given(dataDragonClient.getSummonerSpells()).willReturn(spells("점멸"));

        // when
        spellService.syncSpells();

        // then
        assertThat(spellRepository.findAll())
                .extracting(SummonerSpell::getSpellId, SummonerSpell::getRiotKey, SummonerSpell::getIconKey)
                .containsExactly(tuple(4, "SummonerFlash", "SummonerFlash.png"));
    }

    @Test
    @DisplayName("다시 적재해도 행이 늘지 않고, 바뀐 이름은 반영된다")
    void syncSpells_WhenRunTwice_KeepSingleRowAndUpdateName() {
        // given
        given(dataDragonClient.getSummonerSpells()).willReturn(spells("점멸"), spells("순간이동 점멸"));
        spellService.syncSpells();
        entityManager.flush();
        entityManager.clear();

        // when
        spellService.syncSpells();
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(spellRepository.count()).isEqualTo(1);
        assertThat(spellRepository.findById(4).orElseThrow().getName()).containsEntry("ko-KR", "순간이동 점멸");
    }

    @Test
    @DisplayName("저장한 주문 아이콘을 버전별 이미지 저장소에 올린다")
    void syncSpells_WhenLoaded_StoreIcons() {
        // given
        given(dataDragonClient.getSummonerSpells()).willReturn(spells("점멸"));

        // when
        spellService.syncSpells();

        // then
        verify(spellImageService).store("16.20", "16.20.1", "SummonerFlash", "SummonerFlash.png");
    }

    private static SummonerSpellResponse spells(String flashName) {
        return new SummonerSpellResponse("16.20", "16.20.1", Map.of(
                "SummonerFlash", new SummonerSpellData("SummonerFlash", "4", flashName,
                        new SummonerSpellData.Image("SummonerFlash.png"), List.of("CLASSIC", "ARAM")),
                "SummonerCherryFlash", new SummonerSpellData("SummonerCherryFlash", "2202", flashName,
                        new SummonerSpellData.Image("SummonerCherryFlash.png"), List.of("CHERRY"))),
                Map.of("SummonerFlash", "Flash", "SummonerCherryFlash", "Flash"));
    }
}
