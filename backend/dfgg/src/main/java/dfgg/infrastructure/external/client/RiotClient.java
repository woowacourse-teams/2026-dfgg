package dfgg.infrastructure.external.client;

import dfgg.infrastructure.external.config.RiotApiProperties;
import dfgg.infrastructure.external.dto.LeagueEntryResponse;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

@Component
public class RiotClient {

    private static final String RIOT_TOKEN_HEADER = "X-Riot-Token";
    private static final ParameterizedTypeReference<List<LeagueEntryResponse>> LEAGUE_ENTRY_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public RiotClient(RestClient.Builder builder, RiotApiProperties properties) {
        this.restClient = builder
                .baseUrl(properties.platformBaseUrl().toString())
                .defaultHeader(RIOT_TOKEN_HEADER, properties.key())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<LeagueEntryResponse> getLeagueEntries(
            String queue,
            String tier,
            String division,
            int page
    ) {
        Assert.hasText(queue, "queue must not be blank");
        Assert.hasText(tier, "tier must not be blank");
        Assert.hasText(division, "division must not be blank");
        Assert.isTrue(page > 0, "page must be greater than zero");

        List<LeagueEntryResponse> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/lol/league/v4/entries/{queue}/{tier}/{division}")
                        .queryParam("page", page)
                        .build(queue, tier, division))
                .retrieve()
                .body(LEAGUE_ENTRY_LIST_TYPE);

        if (response == null) {
            throw new IllegalStateException("[Error] Riot League entries response is empty");
        }
        return List.copyOf(response);
    }
}
