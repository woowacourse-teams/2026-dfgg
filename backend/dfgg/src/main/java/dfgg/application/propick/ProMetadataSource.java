package dfgg.application.propick;

import dfgg.domain.propick.ProPlayer;
import dfgg.infrastructure.external.client.CargoQuery;
import dfgg.infrastructure.external.client.LeaguepediaClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 픽에 나온 대회·선수의 정보를 Leaguepedia에서 받는다. 목록에 없는 대회·선수는 페이지 이름으로 채운다.
 * 선수 사진은 프로필 사진 중 대회 시작일이 가장 늦은 것이다 (스펙 §4.3 결정 3).
 */
@Component
public class ProMetadataSource {

    // IN 목록이 길면 요청 URL이 길어진다.
    private static final int PAGES_PER_QUERY = 50;
    private static final String DISAMBIGUATION = "\\s*\\(.*\\)$";

    private final LeaguepediaClient leaguepediaClient;

    public ProMetadataSource(LeaguepediaClient leaguepediaClient) {
        this.leaguepediaClient = leaguepediaClient;
    }

    public List<ProTournamentInfo> tournaments(Collection<String> overviewPages) {
        Map<String, Map<String, String>> rows = queryByPages(List.copyOf(overviewPages), Function.identity(),
                pages -> new CargoQuery(
                        "Tournaments=T",
                        "T.OverviewPage, T.Name, T.League, T.Region, T.DateStart, T.Date",
                        "T.OverviewPage IN (" + inList(pages) + ")",
                        "T.OverviewPage"));
        return overviewPages.stream()
                .map(page -> tournamentOf(page, rows.get(page)))
                .toList();
    }

    private ProTournamentInfo tournamentOf(String overviewPage, Map<String, String> row) {
        if (row == null) {
            return new ProTournamentInfo(overviewPage, overviewPage, overviewPage.split("/", 2)[0], null, null, null);
        }
        return new ProTournamentInfo(overviewPage, row.get("Name"), row.get("League"), blankToNull(row.get("Region")),
                dateOf(row.get("DateStart")), dateOf(row.get("Date")));
    }

    public List<ProPlayerInfo> players(Collection<String> playerPages) {
        List<String> pages = playerPages.stream().map(ProPlayer::canonicalPage).distinct().toList();
        // 위키 비교는 대소문자를 가리지 않아 kyeahoo처럼 소문자 페이지가 돌아올 수 있다. 같은 규칙으로 맞춰 찾는다.
        Map<String, Map<String, String>> profiles = queryByPages(pages, ProPlayer::canonicalPage,
                batch -> new CargoQuery(
                        "Players=P",
                        "P.OverviewPage, P.ID, P.Team",
                        "P.OverviewPage IN (" + inList(batch) + ")",
                        "P.OverviewPage"));
        Map<String, String> imageFiles = latestProfileImages(pages);
        Map<String, String> imageUrls = leaguepediaClient.imageUrls(List.copyOf(imageFiles.values()));
        return pages.stream()
                .map(page -> playerOf(page, profiles.get(page), imageUrlOf(page, imageFiles, imageUrls)))
                .toList();
    }

    private String imageUrlOf(String page, Map<String, String> imageFiles, Map<String, String> imageUrls) {
        String file = imageFiles.get(page);
        if (file == null) {
            return null;
        }
        return imageUrls.get(file);
    }

    private ProPlayerInfo playerOf(String page, Map<String, String> profile, String imageUrl) {
        if (profile == null) {
            return new ProPlayerInfo(page, page.replaceAll(DISAMBIGUATION, ""), null, imageUrl);
        }
        return new ProPlayerInfo(page, profile.get("ID"), blankToNull(profile.get("Team")), imageUrl);
    }

    // 최근 대회 사진이 먼저 오도록 정렬해 받고, 선수마다 첫 행을 쓴다.
    private Map<String, String> latestProfileImages(List<String> pages) {
        Map<String, String> files = new LinkedHashMap<>();
        for (List<String> batch : batches(pages)) {
            leaguepediaClient.cargoQuery(new CargoQuery(
                            "PlayerImages=PI, Tournaments=T",
                            "PI.Link, PI.FileName, T.DateStart",
                            "PI.Link IN (" + inList(batch) + ") AND PI.IsProfileImage = '1'",
                            "T.DateStart DESC, PI.SortDate DESC",
                            "PI.Tournament=T.OverviewPage"))
                    .forEach(row -> files.putIfAbsent(ProPlayer.canonicalPage(row.get("Link")), row.get("FileName")));
        }
        return files;
    }

    private Map<String, Map<String, String>> queryByPages(List<String> pages, Function<String, String> keyOf,
                                                         Function<List<String>, CargoQuery> queryOf) {
        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        for (List<String> batch : batches(pages)) {
            leaguepediaClient.cargoQuery(queryOf.apply(batch))
                    .forEach(row -> rows.putIfAbsent(keyOf.apply(row.get("OverviewPage")), row));
        }
        return rows;
    }

    private List<List<String>> batches(List<String> pages) {
        List<List<String>> batches = new ArrayList<>();
        for (int start = 0; start < pages.size(); start += PAGES_PER_QUERY) {
            batches.add(pages.subList(start, Math.min(start + PAGES_PER_QUERY, pages.size())));
        }
        return batches;
    }

    private String inList(List<String> values) {
        return values.stream()
                .map(value -> "'" + value.replace("'", "''") + "'")
                .collect(Collectors.joining(", "));
    }

    private LocalDate dateOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
