package dfgg.application.champion;

import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.image.ImageUrls;
import dfgg.presentation.dto.ChampionSummaryDto;
import dfgg.presentation.dto.response.ChampionsResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 클라이언트에 줄 챔피언 목록을 만든다. ddragon {@code champion.json}을 대신한다.
 * <p>
 * 한글 이름 가나다순으로 준다. 검색 목록을 받은 순서 그대로 그릴 수 있게 하려는 것이다.
 */
@Service
@Transactional(readOnly = true)
public class ChampionQueryService {

    private final ChampionRepository championRepository;
    private final ImageUrls imageUrls;

    public ChampionQueryService(ChampionRepository championRepository, ImageUrls imageUrls) {
        this.championRepository = championRepository;
        this.imageUrls = imageUrls;
    }

    public ChampionsResponse findAll() {
        List<ChampionSummaryDto> champions = championRepository.findAll().stream()
                .map(champion -> ChampionSummaryDto.of(champion, imageUrls.championOf(champion.getRiotKey())))
                .sorted(Comparator.comparing(ChampionSummaryDto::name, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new ChampionsResponse(champions);
    }
}
