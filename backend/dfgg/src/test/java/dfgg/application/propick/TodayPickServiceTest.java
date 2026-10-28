package dfgg.application.propick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dfgg.common.exception.ChampionNotFoundException;
import dfgg.common.exception.InvalidRecommendationRequestException;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.language.Language;
import dfgg.domain.propick.ProPickRepository;
import dfgg.domain.propick.ProTournamentRepository;
import dfgg.domain.rune.RuneRepository;
import dfgg.domain.spell.SummonerSpellRepository;
import dfgg.presentation.dto.response.TodayPickResponse;
import dfgg.presentation.dto.response.TodayPickResponse.EntryResponse;
import dfgg.presentation.dto.response.TodayPickResponse.PickResponse;
import dfgg.presentation.dto.response.TodayPickResponse.RunesResponse;
import dfgg.presentation.dto.response.TodayPickResponse.TreeResponse;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/today-pick-test-data.sql")
class TodayPickServiceTest {

    private static final String S3 = "https://bucket.s3.ap-northeast-2.amazonaws.com/dfgg/images/16.20/";

    @Autowired
    private ProTournamentRepository tournamentRepository;

    @Autowired
    private ProPickRepository pickRepository;

    @Autowired
    private ChampionRepository championRepository;

    @Autowired
    private SummonerSpellRepository spellRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private RuneRepository runeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private TodayPickService todayPickService;

    @BeforeEach
    void setUp() {
        todayPickService = new TodayPickService(tournamentRepository, pickRepository, championRepository,
                spellRepository, itemRepository, runeRepository, new ImageUrls("bucket", "ap-northeast-2", "16.20"),
                5, () -> new Random(42));
    }

    @Test
    @DisplayName("최근 대회의 이름·리그·시작일과 요청한 포지션을 담는다")
    void recommend_WhenPicksExist_IncludeLatestTournament() {
        // when
        TodayPickResponse response = todayPickService.recommend(ChampionPosition.MID, List.of(), Language.KO_KR);

        // then
        assertThat(response.tournament().name()).isEqualTo("2026 Worlds Main Event");
        assertThat(response.tournament().league()).isEqualTo("World Championship");
        assertThat(response.tournament().startDate()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(response.position()).isEqualTo(ChampionPosition.MID);
    }

    @Test
    @DisplayName("최근 대회의 내 라인 픽만 뽑는다 — 다른 라인과 예전 대회 픽은 나오지 않는다")
    void recommend_WhenOtherPositionOrOlderTournamentExists_DrawOnlyLatestTournamentPosition() {
        // when
        TodayPickResponse response = todayPickService.recommend(ChampionPosition.MID, List.of(), Language.KO_KR);

        // then
        assertThat(response.picks()).extracting(pick -> pick.player().name())
                .containsExactlyInAnyOrder("Faker", "Chovy");
    }

    @Test
    @DisplayName("밴된 챔피언의 픽은 빠진다")
    void recommend_WhenChampionBanned_ExcludePick() {
        // when
        TodayPickResponse response = todayPickService.recommend(ChampionPosition.MID, List.of(9002, 9002),
                Language.KO_KR);

        // then
        assertThat(response.picks()).extracting(pick -> pick.champion().id()).containsExactly(9001L);
    }

    @Test
    @DisplayName("선수(이름·팀·사진)·경기 날짜·챔피언(이름·사진·포지션)을 담는다")
    void recommend_WhenPickDrawn_IncludePlayerAndChampion() {
        // when
        PickResponse faker = fakerPick(Language.KO_KR);

        // then
        assertThat(faker.player().team()).isEqualTo("T1");
        assertThat(faker.player().imageUrl()).isEqualTo("https://static.wikia.nocookie.net/faker.png");
        assertThat(faker.playedAt()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(faker.champion().id()).isEqualTo(9001L);
        assertThat(faker.champion().name()).isEqualTo("테스트챔피언");
        assertThat(faker.champion().imageUrl()).isEqualTo(S3 + "champions/ProTestChampion.png");
        assertThat(faker.champion().position()).isEqualTo(ChampionPosition.MID);
    }

    @Test
    @DisplayName("선수 팀은 지금 소속이 아니라 그 경기 당시 팀이다 — 지금 소속이 비었거나 바뀌어도 그 경기의 팀을 낸다")
    void recommend_WhenCurrentTeamDiffersOrMissing_UseTeamAtGame() {
        // given
        jdbcTemplate.update("UPDATE pro_players SET team = NULL WHERE leaguepedia_page = 'Faker'");
        jdbcTemplate.update("UPDATE pro_players SET team = 'Dplus KIA' WHERE leaguepedia_page = 'Chovy'");

        // when & then
        assertThat(pickOf("Faker", Language.KO_KR).player().team()).isEqualTo("T1");
        assertThat(pickOf("Chovy", Language.KO_KR).player().team()).isEqualTo("Gen.G");
    }

    @Test
    @DisplayName("소환사 주문과 최종 아이템을 칸 순서대로 이름·사진과 함께 담는다")
    void recommend_WhenPickDrawn_IncludeSpellsAndItemsInSlotOrder() {
        // when
        PickResponse faker = fakerPick(Language.KO_KR);

        // then
        assertThat(faker.spells()).extracting(EntryResponse::id, EntryResponse::name, EntryResponse::imageUrl)
                .containsExactly(
                        tuple(9001L, "점멸", S3 + "summoner-spells/ProTestFlash.png"),
                        tuple(9002L, "점화", S3 + "summoner-spells/ProTestIgnite.png"));
        assertThat(faker.items()).extracting(EntryResponse::id, EntryResponse::name, EntryResponse::imageUrl)
                .containsExactly(
                        tuple(9001L, "아이템1", S3 + "items/9001.png"),
                        tuple(9002L, "아이템2", S3 + "items/9002.png"));
    }

    @Test
    @DisplayName("룬은 주 트리(0번 칸 룬의 트리)·핵심·주 일반·보조 트리(4번 칸 룬의 트리)·보조·파편으로 나눠 담는다")
    void recommend_WhenPickDrawn_GroupRunesBySlot() {
        // when
        RunesResponse runes = fakerPick(Language.KO_KR).runes();

        // then
        assertThat(runes.primaryTree()).isEqualTo(new TreeResponse(9000L, "트리"));
        assertThat(runes.keystone()).isEqualTo(new EntryResponse(9001L, "룬1", S3 + "runes/9001.png"));
        assertThat(runes.primary()).extracting(EntryResponse::id).containsExactly(9002L);
        assertThat(runes.secondaryTree()).isEqualTo(new TreeResponse(9100L, "보조트리"));
        assertThat(runes.secondary()).extracting(EntryResponse::id).containsExactly(9101L);
        assertThat(runes.shards()).extracting(EntryResponse::id, EntryResponse::name)
                .containsExactly(tuple(5008L, "적응형 능력치"), tuple(5011L, "체력"));
    }

    @Test
    @DisplayName("룬 칸이 없는 픽은 트리·핵심이 비고 목록은 빈 목록이다")
    void recommend_WhenPickHasNoRunes_ReturnEmptyRunes() {
        // when
        PickResponse chovy = pickOf("Chovy", Language.KO_KR);

        // then
        assertThat(chovy.runes().primaryTree()).isNull();
        assertThat(chovy.runes().keystone()).isNull();
        assertThat(chovy.runes().shards()).isEmpty();
        assertThat(chovy.spells()).isEmpty();
    }

    @Test
    @DisplayName("이름은 요청 언어로 담는다")
    void recommend_WhenEnglishRequested_UseEnglishNames() {
        // when
        PickResponse faker = fakerPick(Language.EN_US);

        // then
        assertThat(faker.champion().name()).isEqualTo("Test Champion");
        assertThat(faker.spells().getFirst().name()).isEqualTo("Test Flash");
        assertThat(faker.runes().primaryTree().name()).isEqualTo("Tree");
    }

    @Test
    @DisplayName("대회 픽 데이터가 없으면 대회는 비고 픽은 빈 목록이다")
    void recommend_WhenNoPick_ReturnNullTournamentAndEmptyPicks() {
        // given
        jdbcTemplate.update("DELETE FROM pro_picks");

        // when
        TodayPickResponse response = todayPickService.recommend(ChampionPosition.MID, List.of(), Language.KO_KR);

        // then
        assertThat(response.tournament()).isNull();
        assertThat(response.position()).isEqualTo(ChampionPosition.MID);
        assertThat(response.picks()).isEmpty();
    }

    @Test
    @DisplayName("밴이 11개 이상이면 거부한다 — 양 팀이 같은 챔피언을 밴한 중복도 개수에 든다")
    void recommend_WhenElevenBans_Throw() {
        // given
        List<Integer> bans = Collections.nCopies(11, 9001);

        // when & then
        assertThatThrownBy(() -> todayPickService.recommend(ChampionPosition.MID, bans, Language.KO_KR))
                .isInstanceOf(InvalidRecommendationRequestException.class)
                .hasMessageContaining("11");
    }

    @Test
    @DisplayName("등록되지 않은 챔피언을 밴하면 그 id를 담아 거부한다")
    void recommend_WhenUnknownChampionBanned_Throw() {
        assertThatThrownBy(() -> todayPickService.recommend(ChampionPosition.MID, List.of(9001, 99999),
                Language.KO_KR))
                .isInstanceOf(ChampionNotFoundException.class)
                .hasMessageContaining("99999");
    }

    @Test
    @DisplayName("후보 픽이 많아도 쿼리 7번으로 끝난다 — 최근 대회·픽(선수·대회·주문·아이템·룬) 각 1번과 카탈로그 5번")
    void recommend_WhenManyCandidates_UseSevenQueries() {
        // given
        jdbcTemplate.update("""
                INSERT INTO pro_picks (tournament_id, player_id, source_game_id, played_at, team, side, position,
                                       champion_id, win, spell_ids, item_ids, rune_ids)
                SELECT 1, 2, 'extra' || n, '2026-10-26 08:00:00+00', 'Gen.G', 'RED', 'MID', 9002, true,
                       '{9001,9002}', '{9001,NULL,NULL,NULL,NULL,NULL,9002}',
                       '{9001,9002,NULL,NULL,9101,NULL,5008,NULL,5011}'
                FROM generate_series(1, 30) n
                """);
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        // when
        TodayPickResponse response = todayPickService.recommend(ChampionPosition.MID, List.of(), Language.KO_KR);

        // then
        assertThat(response.picks()).isNotEmpty();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(7);
    }

    private PickResponse fakerPick(Language language) {
        return pickOf("Faker", language);
    }

    private PickResponse pickOf(String playerName, Language language) {
        return todayPickService.recommend(ChampionPosition.MID, List.of(), language).picks().stream()
                .filter(pick -> pick.player().name().equals(playerName))
                .findFirst()
                .orElseThrow();
    }
}
