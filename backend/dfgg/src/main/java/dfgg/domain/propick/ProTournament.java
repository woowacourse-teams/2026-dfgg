package dfgg.domain.propick;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Leaguepedia 대회 하나. {@code overviewPage}로 같은 대회를 찾는다.
 */
@Entity
@Table(name = "pro_tournaments")
public class ProTournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "overview_page", nullable = false, unique = true)
    private String overviewPage;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String league;

    private String region;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    protected ProTournament() {
    }

    public ProTournament(String overviewPage, String name, String league, String region,
                         LocalDate startDate, LocalDate endDate) {
        this.overviewPage = overviewPage;
        update(name, league, region, startDate, endDate);
    }

    public void update(String name, String league, String region, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.league = league;
        this.region = region;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long getId() {
        return id;
    }

    public String getOverviewPage() {
        return overviewPage;
    }

    public String getName() {
        return name;
    }

    public String getLeague() {
        return league;
    }

    public String getRegion() {
        return region;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
