package dfgg.domain.aram;

import dfgg.domain.language.Language;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 패치별 증강 카탈로그 항목. 이름만 저장하고 이미지는 S3에 따로 둔다. */
@Entity
@Table(name = "aram_mayhem_augments")
public class Augment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String patch;

    @Column(name = "augment_id", nullable = false)
    private int augmentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AugmentRarity rarity;

    protected Augment() {
    }

    public Augment(String patch, int augmentId, Map<String, String> name, AugmentRarity rarity) {
        this.patch = patch;
        this.augmentId = augmentId;
        this.name = Map.copyOf(name);
        this.rarity = rarity;
    }

    public Long id() {
        return id;
    }

    public int augmentId() {
        return augmentId;
    }

    public AugmentRarity rarity() {
        return rarity;
    }

    public String nameIn(Language language) {
        return language.pick(name);
    }

    /** 어느 언어든 이름이 같으면 이 증강이다. 앞뒤 공백과 대소문자만 무시한다. */
    public boolean isNamed(String candidate) {
        String normalizedCandidate = normalize(candidate);
        return name.values().stream()
                .map(Augment::normalize)
                .anyMatch(normalizedCandidate::equals);
    }

    public boolean sharesNameWith(Augment other) {
        return name.values().stream().anyMatch(other::isNamed);
    }

    private static String normalize(String text) {
        return text.strip().toLowerCase(Locale.ROOT);
    }
}
