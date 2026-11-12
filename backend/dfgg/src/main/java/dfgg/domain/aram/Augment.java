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
import java.util.Optional;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 패치별 증강 카탈로그 항목. 이름과 요약 설명을 저장하고 이미지는 S3에 따로 둔다. */
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

    /** 이름과 같은 언어별 형식. 수치를 채우지 못한 증강은 설명이 없어 null이다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> description;

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

    /** @param descriptions 언어 키({@code ko-KR})별 설명. 비어 있으면 설명이 없는 증강이다 */
    public void describe(Map<String, String> descriptions) {
        this.description = descriptions.isEmpty() ? null : Map.copyOf(descriptions);
    }

    /** 이름처럼 요청 언어가 없으면 한글로 낸다. 한글도 없으면 비어 있다. */
    public Optional<String> descriptionIn(Language language) {
        return Optional.ofNullable(description).map(language::pick);
    }

    public boolean isDescribed() {
        return description != null;
    }

    /** 견본은 게임 화면 언어와 같아야 맞으므로 다른 언어 이름으로 대신하지 않는다. */
    public Optional<String> exactNameIn(Language language) {
        return language.find(name);
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
