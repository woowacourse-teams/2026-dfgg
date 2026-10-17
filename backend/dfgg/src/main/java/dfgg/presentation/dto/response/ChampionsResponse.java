package dfgg.presentation.dto.response;

import dfgg.presentation.dto.ChampionSummaryDto;
import java.util.List;

public record ChampionsResponse(
        List<ChampionSummaryDto> champions
) {
}
