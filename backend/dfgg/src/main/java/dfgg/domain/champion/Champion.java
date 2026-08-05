package dfgg.domain.champion;

import java.util.List;

public class Champion {
    private Long championId;
    private String name;
    private List<ChampionTag> championTags;

    public Champion(Long championId, String name, List<ChampionTag> championTags) {
        this.championId = championId;
        this.name = name;
        this.championTags = championTags;
    }

    public Long getChampionId() {
        return championId;
    }

    public String getName() {
        return name;
    }

    public List<ChampionTag> getChampionTags() {
        return championTags;
    }
}
