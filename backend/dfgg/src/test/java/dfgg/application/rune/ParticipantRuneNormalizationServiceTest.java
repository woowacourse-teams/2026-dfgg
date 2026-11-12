package dfgg.application.rune;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.rune.NormalizedMatchParticipantRune;
import dfgg.domain.rune.NormalizedMatchParticipantRuneRepository;
import dfgg.domain.rune.RunePage;
import dfgg.domain.rune.StatShards;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ParticipantRuneNormalizationServiceTest {

    private static final String MATCH_ID = "KR_1";
    private static final RunePage JAYCE_PAGE = new RunePage(8400, List.of(8437, 8446, 8473, 8451),
            8300, List.of(8304, 8345), new StatShards(5005, 5008, 5001));
    private static final RunePage JAX_PAGE = new RunePage(8000, List.of(8010, 9111, 9104, 8299),
            8400, List.of(8444, 8242), new StatShards(5005, 5008, 5001));

    @Autowired
    private RawMatchRepository rawMatchRepository;

    @Autowired
    private NormalizedMatchParticipantRuneRepository runeRepository;

    @Autowired
    private EntityManager entityManager;

    private ParticipantRuneNormalizationService service;

    @BeforeEach
    void setUp() {
        service = new ParticipantRuneNormalizationService(rawMatchRepository, runeRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("저장된 Raw Match에서 참가자마다 룬 페이지를 꺼내 경기 패치와 함께 저장한다")
    void normalize_SavesRunePagePerParticipant() {
        // given
        rawMatchRepository.save(new RawMatch(MATCH_ID, rawMatch()));

        // when
        int saved = service.normalize(MATCH_ID);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(saved).isEqualTo(2);
        assertThat(runeRepository.findAll())
                .extracting(NormalizedMatchParticipantRune::runePage)
                .containsExactlyInAnyOrder(JAYCE_PAGE, JAX_PAGE);
        assertThat(entityManager.createQuery("SELECT rune.patch FROM NormalizedMatchParticipantRune rune",
                String.class).getResultList()).containsOnly("16.19");
    }

    @Test
    @DisplayName("같은 경기를 다시 정규화해도 참가자 룬이 중복으로 쌓이지 않는다")
    void normalize_WhenRunAgain_DoesNotDuplicateRunes() {
        // given
        rawMatchRepository.save(new RawMatch(MATCH_ID, rawMatch()));
        service.normalize(MATCH_ID);
        entityManager.flush();

        // when
        service.normalize(MATCH_ID);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(runeRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("저장된 Raw Match가 없으면 정규화하지 않는다")
    void normalize_WhenRawMatchIsMissing_Throws() {
        assertThatThrownBy(() -> service.normalize("KR_MISSING"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Raw Match");
    }

    private String rawMatch() {
        return """
                {
                  "info": {
                    "gameVersion": "16.19.817.5716",
                    "queueId": 420,
                    "participants": [
                      {
                        "participantId": 1, "championId": 126, "teamId": 100, "teamPosition": "TOP", "win": true,
                        "perks": {
                          "statPerks": { "defense": 5001, "flex": 5008, "offense": 5005 },
                          "styles": [
                            { "description": "primaryStyle", "style": 8400,
                              "selections": [ { "perk": 8437 }, { "perk": 8446 }, { "perk": 8473 }, { "perk": 8451 } ] },
                            { "description": "subStyle", "style": 8300,
                              "selections": [ { "perk": 8304 }, { "perk": 8345 } ] }
                          ]
                        }
                      },
                      {
                        "participantId": 6, "championId": 24, "teamId": 200, "teamPosition": "TOP", "win": false,
                        "perks": {
                          "statPerks": { "defense": 5001, "flex": 5008, "offense": 5005 },
                          "styles": [
                            { "description": "primaryStyle", "style": 8000,
                              "selections": [ { "perk": 8010 }, { "perk": 9111 }, { "perk": 9104 }, { "perk": 8299 } ] },
                            { "description": "subStyle", "style": 8400,
                              "selections": [ { "perk": 8444 }, { "perk": 8242 } ] }
                          ]
                        }
                      }
                    ]
                  }
                }
                """;
    }
}
