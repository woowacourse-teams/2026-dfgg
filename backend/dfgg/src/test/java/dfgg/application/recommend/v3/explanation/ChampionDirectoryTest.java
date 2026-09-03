package dfgg.application.recommend.v3.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챔피언 ID를 사람이 읽을 이름과 태그로 바꾼다.
 * <p>
 * 추천 이유에 "다리우스"라고 쓰려면 ID를 한글명으로 바꿔야 하고, "물리 피해"를 말하려면 태그가 필요하다.
 * 둘 다 DB에 있지만 그냥 읽으면 두 가지가 걸린다
 * — 태그가 지연 로딩이라 트랜잭션 밖에서 터지고, 운영 데이터에는 중복 태그 행이 있다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/champion-directory-test-data.sql")
class ChampionDirectoryTest {

    private static final long DARIUS = 122L;
    private static final long JINX = 222L;
    private static final long VIKTOR = 112L;
    private static final long WITHOUT_TAGS = 777L;
    private static final long UNKNOWN = 99999L;

    @Autowired
    private ChampionRepository championRepository;

    private ChampionDirectory directory;

    @BeforeEach
    void setUp() {
        directory = new ChampionDirectory(championRepository);
    }

    @Test
    @DisplayName("ID를 한글 이름으로 바꾼다")
    void resolve_TranslatesIdToKoreanName() {
        Map<Long, ChampionProfile> profiles = directory.resolve(List.of(DARIUS, JINX));

        assertThat(profiles.get(DARIUS).name()).isEqualTo("다리우스");
        assertThat(profiles.get(JINX).name()).isEqualTo("징크스");
    }

    @Test
    @DisplayName("중복된 태그 행을 한 번씩만 낸다 — 태그는 의미상 집합이다")
    void resolve_DeduplicatesTags() {
        // 픽스처의 다리우스는 FIGHTER, TANK, FIGHTER, TANK 네 행이다.
        Map<Long, ChampionProfile> profiles = directory.resolve(List.of(DARIUS));

        assertThat(profiles.get(DARIUS).tags())
                .containsExactlyInAnyOrder(ChampionTag.FIGHTER, ChampionTag.TANK);
    }

    @Test
    @DisplayName("태그가 하나도 없는 챔피언은 빈 태그로 낸다 — 없는 태그를 지어내지 않는다")
    void resolve_WhenChampionHasNoTags_YieldsEmptyTags() {
        Map<Long, ChampionProfile> profiles = directory.resolve(List.of(WITHOUT_TAGS));

        assertThat(profiles.get(WITHOUT_TAGS).tags()).isEmpty();
        assertThat(profiles.get(WITHOUT_TAGS).name()).isEqualTo("태그없는챔피언");
    }

    @Test
    @DisplayName("모르는 ID는 결과에서 빠진다 — 이름을 지어내거나 터지지 않는다")
    void resolve_WhenIdIsUnknown_OmitsItInsteadOfFailing() {
        Map<Long, ChampionProfile> profiles = directory.resolve(List.of(DARIUS, UNKNOWN));

        assertThat(profiles).containsKey(DARIUS).doesNotContainKey(UNKNOWN);
    }

    @Test
    @DisplayName("빈 요청에는 빈 결과를 낸다")
    void resolve_WhenNoIdsRequested_ReturnsEmpty() {
        assertThat(directory.resolve(List.of())).isEmpty();
    }

    @Test
    @DisplayName("여러 ID를 한 번에 해석한다 — 챔피언마다 조회하면 요청당 9번이 된다")
    void resolve_ResolvesManyIdsAtOnce() {
        Map<Long, ChampionProfile> profiles = directory.resolve(List.of(DARIUS, JINX, VIKTOR));

        assertThat(profiles).hasSize(3);
    }

    @Test
    @DisplayName("트랜잭션 밖에서도 태그를 읽을 수 있다 — 지연 로딩이면 여기서 터진다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void resolve_OutsideTransaction_StillLoadsTags() {
        Map<Long, ChampionProfile> profiles = directory.resolve(List.of(DARIUS));

        assertThat(profiles.get(DARIUS).tags()).isNotEmpty();
    }

    @Test
    @DisplayName("같은 ID를 두 번 넣어도 한 번만 나온다")
    void resolve_WhenIdRepeats_YieldsOneEntry() {
        assertThat(directory.resolve(List.of(DARIUS, DARIUS))).hasSize(1);
    }
}
