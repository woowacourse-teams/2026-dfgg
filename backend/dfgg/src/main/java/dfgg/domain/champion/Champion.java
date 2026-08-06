package dfgg.domain.champion;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "champions")
public class Champion {

    @Id
    @Column(name = "champion_id")
    private Long championId;

    @Column(name = "riot_key", nullable = false, unique = true)
    private String riotKey;

    @Column(nullable = false)
    private String name;

    @ElementCollection
    @CollectionTable(
            name = "champion_tags",
            joinColumns = @JoinColumn(name = "champion_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "tag")
    private List<ChampionTag> championTags = new ArrayList<>();

    protected Champion() {
    }

    public Champion(Long championId, String riotKey, String name, List<ChampionTag> championTags) {
        this.championId = championId;
        this.riotKey = riotKey;
        this.name = name;
        this.championTags = new ArrayList<>(championTags);
    }

    public Long getChampionId() {
        return championId;
    }

    public String getRiotKey() {
        return riotKey;
    }

    public String getName() {
        return name;
    }

    public List<ChampionTag> getChampionTags() {
        return List.copyOf(championTags);
    }
}
