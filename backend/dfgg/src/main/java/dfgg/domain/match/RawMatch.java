package dfgg.domain.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

@Entity
@Table(name = "raw_matches")
public class RawMatch {

    @Id
    @Column(name = "match_id", nullable = false, length = 32)
    private String matchId;

    @Column(name = "raw_data", nullable = false, columnDefinition = "text")
    private String rawData;

    protected RawMatch() {
    }

    public RawMatch(String matchId, String rawData) {
        Assert.hasText(matchId, "matchId must not be blank");
        Assert.hasText(rawData, "rawData must not be blank");

        this.matchId = matchId;
        this.rawData = rawData;
    }

    public String getMatchId() {
        return matchId;
    }

    public String getRawData() {
        return rawData;
    }
}
