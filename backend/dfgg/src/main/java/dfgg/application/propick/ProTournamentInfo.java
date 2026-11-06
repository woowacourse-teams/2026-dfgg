package dfgg.application.propick;

import java.time.LocalDate;

/** Leaguepedia {@code Tournaments} 한 행. 지역·날짜는 비어 있을 수 있다. */
public record ProTournamentInfo(
        String overviewPage,
        String name,
        String league,
        String region,
        LocalDate startDate,
        LocalDate endDate
) {
}
