package dfgg.infrastructure.external.client;

import dfgg.infrastructure.external.config.RiotApiProperties;
import dfgg.infrastructure.external.dto.LeagueEntryResponse;
import dfgg.infrastructure.external.dto.MatchResponse;
import dfgg.infrastructure.external.dto.MatchTimelineResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

@Component
public class RiotClient {

    private static final String RIOT_TOKEN_HEADER = "X-Riot-Token";
    private static final int RANKED_SOLO_QUEUE_ID = 420;
    private static final int DEFAULT_MATCH_START = 0;
    private static final int DEFAULT_MATCH_COUNT = 20;
    private static final ParameterizedTypeReference<List<LeagueEntryResponse>> LEAGUE_ENTRY_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<String>> STRING_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient platformRestClient;
    private final RestClient regionalRestClient;
    private final RiotRateLimitExecutor rateLimitExecutor;

    @Autowired
    public RiotClient(RestClient.Builder builder, RiotApiProperties properties) {
        this(builder, properties, new RiotRateLimitExecutor(properties.keys()));
    }

    RiotClient(
            RestClient.Builder builder,
            RiotApiProperties properties,
            RiotRateLimitExecutor rateLimitExecutor
    ) {
        this.platformRestClient = createRestClient(builder.clone(), properties.platformBaseUrl().toString());
        this.regionalRestClient = createRestClient(builder.clone(), properties.regionalBaseUrl().toString());
        this.rateLimitExecutor = rateLimitExecutor;
    }

    private RestClient createRestClient(RestClient.Builder builder, String baseUrl) {
        return builder
                .baseUrl(baseUrl)
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

        List<LeagueEntryResponse> response = rateLimitExecutor.execute(() ->
                platformRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/lol/league/v4/entries/{queue}/{tier}/{division}")
                                .queryParam("page", page)
                                .build(queue, tier, division))
                        .header(RIOT_TOKEN_HEADER, rateLimitExecutor.currentApiKey())
                        .retrieve()
                        .body(LEAGUE_ENTRY_LIST_TYPE)
        );

        if (response == null) {
            throw new IllegalStateException("[Error] Riot League entries response is empty");
        }
        return List.copyOf(response);
    }

    public List<String> getMatchIds(String puuid) {
        return getMatchIds(puuid, DEFAULT_MATCH_START, DEFAULT_MATCH_COUNT);
    }

    public List<String> getMatchIds(String puuid, int start, int count) {
        Assert.hasText(puuid, "puuid must not be blank");
        Assert.isTrue(start >= 0, "start must not be negative");
        Assert.isTrue(count >= 0 && count <= 100, "count must be between 0 and 100");

        List<String> response = rateLimitExecutor.execute(() ->
                regionalRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/lol/match/v5/matches/by-puuid/{puuid}/ids")
                                .queryParam("queue", RANKED_SOLO_QUEUE_ID)
                                .queryParam("start", start)
                                .queryParam("count", count)
                                .build(puuid))
                        .header(RIOT_TOKEN_HEADER, rateLimitExecutor.currentApiKey())
                        .retrieve()
                        .body(STRING_LIST_TYPE)
        );

        if (response == null) {
            throw new IllegalStateException("[Error] Riot Match IDs response is empty");
        }
        return List.copyOf(response);
    }

    public MatchResponse getMatch(String matchId) {
        Assert.hasText(matchId, "matchId must not be blank");

        MatchResponse response = rateLimitExecutor.execute(() ->
                regionalRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/lol/match/v5/matches/{matchId}")
                                .build(matchId))
                        .header(RIOT_TOKEN_HEADER, rateLimitExecutor.currentApiKey())
                        .retrieve()
                        .body(MatchResponse.class)
        );

        if (response == null) {
            throw new IllegalStateException("[Error] Riot Match response is empty");
        }
        return response;
    }

    public String getRawMatch(String matchId) {
        Assert.hasText(matchId, "matchId must not be blank");

        String response = rateLimitExecutor.execute(() ->
                regionalRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/lol/match/v5/matches/{matchId}")
                                .build(matchId))
                        .header(RIOT_TOKEN_HEADER, rateLimitExecutor.currentApiKey())
                        .retrieve()
                        .body(String.class)
        );

        if (response == null) {
            throw new IllegalStateException("[Error] Riot raw Match response is empty");
        }
        return response;
    }

    public String getRawMatchTimeline(String matchId) {
        Assert.hasText(matchId, "matchId must not be blank");

        String response = rateLimitExecutor.execute(() ->
                regionalRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/lol/match/v5/matches/{matchId}/timeline")
                                .build(matchId))
                        .header(RIOT_TOKEN_HEADER, rateLimitExecutor.currentApiKey())
                        .retrieve()
                        .body(String.class)
        );

        if (response == null) {
            throw new IllegalStateException("[Error] Riot raw Match timeline response is empty");
        }
        return response;
    }

    public MatchTimelineResponse getMatchTimeline(String matchId) {
        Assert.hasText(matchId, "matchId must not be blank");

        MatchTimelineResponse response = rateLimitExecutor.execute(() ->
                regionalRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/lol/match/v5/matches/{matchId}/timeline")
                                .build(matchId))
                        .header(RIOT_TOKEN_HEADER, rateLimitExecutor.currentApiKey())
                        .retrieve()
                        .body(MatchTimelineResponse.class)
        );

        if (response == null) {
            throw new IllegalStateException("[Error] Riot Match timeline response is empty");
        }
        return response;
    }
}
