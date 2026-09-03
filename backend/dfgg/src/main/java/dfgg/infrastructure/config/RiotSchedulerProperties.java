package dfgg.infrastructure.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("collection.scheduler")
public class RiotSchedulerProperties {

    private boolean enabled;
    private String cron = "0 */5 * * * *";
    private String zone = "Asia/Seoul";
    private List<String> tiers = new ArrayList<>(List.of("PLATINUM"));
    private List<String> divisions = new ArrayList<>(List.of("I"));
    private int leaguePageCount = 1;
    private int playerPageSize = 100;
    private int playerLimit = 1;
    private int matchCount = 20;
    private boolean recoverMissingTimelines;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public List<String> getTiers() {
        return List.copyOf(tiers);
    }

    public void setTiers(List<String> tiers) {
        this.tiers = new ArrayList<>(tiers);
    }

    public List<String> getDivisions() {
        return List.copyOf(divisions);
    }

    public void setDivisions(List<String> divisions) {
        this.divisions = new ArrayList<>(divisions);
    }

    public int getLeaguePageCount() {
        return leaguePageCount;
    }

    public void setLeaguePageCount(int leaguePageCount) {
        this.leaguePageCount = leaguePageCount;
    }

    public int getPlayerPageSize() {
        return playerPageSize;
    }

    public void setPlayerPageSize(int playerPageSize) {
        this.playerPageSize = playerPageSize;
    }

    public int getPlayerLimit() {
        return playerLimit;
    }

    public void setPlayerLimit(int playerLimit) {
        this.playerLimit = playerLimit;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public void setMatchCount(int matchCount) {
        this.matchCount = matchCount;
    }

    public boolean isRecoverMissingTimelines() {
        return recoverMissingTimelines;
    }

    public void setRecoverMissingTimelines(boolean recoverMissingTimelines) {
        this.recoverMissingTimelines = recoverMissingTimelines;
    }
}
