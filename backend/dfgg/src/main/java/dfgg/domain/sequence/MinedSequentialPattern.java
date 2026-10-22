package dfgg.domain.sequence;

import dfgg.domain.champion.ChampionPosition;
import dfgg.infrastructure.persistence.LongListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "mined_sequential_patterns")
public class MinedSequentialPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pattern_id")
    private Long id;

    @Column(name = "champion_id", nullable = false)
    private Long championId;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 16)
    private ChampionPosition position;

    @Column(name = "tier", length = 32)
    private String tier;

    @Column(name = "patch", nullable = false, length = 16)
    private String patch;

    @Column(name = "pattern_key", nullable = false, length = 512)
    private String patternKey;

    @Convert(converter = LongListConverter.class)
    @Column(name = "items", nullable = false, columnDefinition = "TEXT")
    private List<Long> items;

    @Column(name = "support_count", nullable = false)
    private Integer supportCount;

    @Column(name = "scope_total_count", nullable = false)
    private Integer scopeTotalCount;

    @Column(name = "win_count", nullable = false)
    private Integer winCount;

    @Column(name = "algorithm_version", nullable = false, length = 32)
    private String algorithmVersion;

    protected MinedSequentialPattern() {
    }

    public MinedSequentialPattern(
            Long championId,
            ChampionPosition position,
            String tier,
            String patch,
            List<Long> items,
            Integer supportCount,
            Integer scopeTotalCount,
            Integer winCount,
            String algorithmVersion
    ) {
        this.championId = championId;
        this.position = position;
        this.tier = tier;
        this.patch = patch;
        this.items = new ArrayList<>(items);
        this.patternKey = createPatternKey(items);
        this.supportCount = supportCount;
        this.scopeTotalCount = scopeTotalCount;
        this.winCount = winCount;
        this.algorithmVersion = algorithmVersion;
    }

    public static String createPatternKey(List<Long> items) {
        return items.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("-"));
    }

    public Long getId() {
        return id;
    }

    public Long getChampionId() {
        return championId;
    }

    public ChampionPosition getPosition() {
        return position;
    }

    public String getTier() {
        return tier;
    }

    public String getPatch() {
        return patch;
    }

    public String getPatternKey() {
        return patternKey;
    }

    public List<Long> getItems() {
        return List.copyOf(items);
    }

    public Integer getSupportCount() {
        return supportCount;
    }

    public Integer getScopeTotalCount() {
        return scopeTotalCount;
    }

    public Integer getWinCount() {
        return winCount;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }
}
