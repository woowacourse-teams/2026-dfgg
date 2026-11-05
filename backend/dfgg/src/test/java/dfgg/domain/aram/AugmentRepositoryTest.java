package dfgg.domain.aram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.domain.language.Language;
import dfgg.domain.match.PatchVersion;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class AugmentRepositoryTest {

    private static final Map<String, String> NAME = Map.of("ko-KR", "축소 엔진", "en-US", "Shrink Engine");

    @Autowired
    private AugmentRepository augmentRepository;

    @Autowired
    private ChampionAugmentRepository championAugmentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("증강 이름을 언어별로 저장하고 요청한 언어로 읽는다")
    void save_RestoresNameByLanguage() {
        // given
        Augment saved = augmentRepository.save(new Augment("16.21", 1373, NAME, AugmentRarity.GOLD));
        entityManager.flush();
        entityManager.clear();

        // when
        Augment augment = augmentRepository.findById(saved.id()).orElseThrow();

        // then
        assertThat(augment.rarity()).isEqualTo(AugmentRarity.GOLD);
        assertThat(augment.nameIn(Language.KO_KR)).isEqualTo("축소 엔진");
        assertThat(augment.nameIn(Language.EN_US)).isEqualTo("Shrink Engine");
    }

    @Test
    @DisplayName("저장된 패치 중 가장 높은 패치는 숫자 순서로 고른다")
    void findDistinctPatches_LatestComparesNumerically() {
        // given
        augmentRepository.save(new Augment("16.9", 1373, NAME, AugmentRarity.GOLD));
        augmentRepository.save(new Augment("16.21", 1373, NAME, AugmentRarity.GOLD));
        entityManager.flush();

        // when
        List<String> patches = augmentRepository.findDistinctPatches();

        // then
        assertThat(PatchVersion.latest(patches)).contains(PatchVersion.of("16.21"));
    }

    @Test
    @DisplayName("같은 패치에 같은 증강을 두 번 저장할 수 없다")
    void save_RejectsDuplicateAugmentInPatch() {
        // given
        augmentRepository.save(new Augment("16.21", 1373, NAME, AugmentRarity.GOLD));
        entityManager.flush();

        // when & then
        assertThatThrownBy(() -> augmentRepository.saveAndFlush(
                new Augment("16.21", 1373, NAME, AugmentRarity.GOLD)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("챔피언별 픽률을 저장하고 읽는다")
    void saveChampionAugment_RestoresPickRate() {
        // given
        augmentRepository.save(new Augment("16.21", 1373, NAME, AugmentRarity.GOLD));
        ChampionAugment saved = championAugmentRepository.save(new ChampionAugment("16.21", 22, 1373, 9438, 16.79));
        entityManager.flush();
        entityManager.clear();

        // when
        ChampionAugment championAugment = championAugmentRepository.findById(saved.id()).orElseThrow();

        // then
        assertThat(championAugment.pickRate()).isEqualTo(16.79);
    }

    @Test
    @DisplayName("저장된 증강이 없는 픽률은 저장할 수 없다")
    void saveChampionAugment_RejectsUnknownAugment() {
        // when & then
        assertThatThrownBy(() -> championAugmentRepository.saveAndFlush(
                new ChampionAugment("16.21", 22, 1373, 9438, 16.79)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
