package dfgg.application.recommend.v3.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionRepository;
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

/**
 * 챔피언 ID를 사람이 읽을 이름으로 바꾼다. 추천 이유에 "다리우스"라고 쓰려면 한글명이 필요하다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/champion-directory-test-data.sql")
class ChampionDirectoryTest {

    private static final long DARIUS = 122L;
    private static final long JINX = 222L;
    private static final long VIKTOR = 112L;
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
    @DisplayName("같은 ID를 두 번 넣어도 한 번만 나온다")
    void resolve_WhenIdRepeats_YieldsOneEntry() {
        assertThat(directory.resolve(List.of(DARIUS, DARIUS))).hasSize(1);
    }
}
