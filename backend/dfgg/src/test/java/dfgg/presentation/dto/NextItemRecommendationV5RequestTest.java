package dfgg.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.recommend.v3.MyState;
import dfgg.application.recommend.v3.ReportedGameState;
import dfgg.presentation.dto.request.NextItemRecommendationV4Request;
import dfgg.presentation.dto.request.NextItemRecommendationV5Request;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 새 추천 API의 요청. v4 요청의 각 선수 항목에 상태(레벨, 킬·데스·어시스트, 들고 있는 아이템)를 더 받는다.
 *
 * <p>새 모델은 상황 없는 입력을 거의 배우지 못했다. 그래서 상태 값은 모두 필수이고, 빠지면 400이다.
 */
class NextItemRecommendationV5RequestTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    private final JsonMapper mapper = JsonMapper.builder().build();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    private Set<String> violations(NextItemRecommendationV5Request request) {
        return VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private NextItemRecommendationV5Request request(ObjectNode json) {
        return mapper.treeToValue(json, NextItemRecommendationV5Request.class);
    }

    private ObjectNode me(ObjectNode body) {
        return (ObjectNode) body.get("myChampion");
    }

    private ObjectNode player(ObjectNode body, String team, int index) {
        return (ObjectNode) body.get(team).get(index);
    }

    /** 선수마다 값이 모두 다르다. 상태를 옮길 때 짝이 밀리면 드러난다. */
    private ObjectNode body() {
        return (ObjectNode) mapper.readTree("""
                {
                  "myChampion": {"name":"Garen", "position":"TOP", "level":11, "kills":4, "deaths":2, "assists":3},
                  "purchasedItemIds": [3071],
                  "allies": [
                    {"name":"Ahri", "position":"MID", "level":8, "kills":0, "assists":7, "itemIds":[3089, 3340]},
                    {"name":"LeeSin", "position":"JUNGLE", "level":7, "kills":1, "assists":9, "itemIds":[]},
                    {"name":"Jinx", "position":"BOTTOM", "level":9, "kills":2, "assists":5, "itemIds":[3031]},
                    {"name":"Thresh", "position":"SUPPORT", "level":10, "kills":3, "assists":1, "itemIds":[3190]}
                  ],
                  "enemies": [
                    {"name":"Ornn", "position":"TOP", "level":12, "kills":5, "assists":0, "itemIds":[3075]},
                    {"name":"Vi", "position":"JUNGLE", "level":13, "kills":6, "assists":2, "itemIds":[]},
                    {"name":"Annie", "position":"MID", "level":6, "kills":7, "assists":4, "itemIds":[3089, 3020]},
                    {"name":"Ashe", "position":"BOTTOM", "level":5, "kills":8, "assists":6, "itemIds":[]},
                    {"name":"Braum", "position":"SUPPORT", "level":4, "kills":9, "assists":8, "itemIds":[3109]}
                  ],
                  "tier": "EMERALD",
                  "inventoryItems": [{"itemId":1036, "quantity":2}],
                  "availableGold": 1000,
                  "gameTimeMs": 600000
                }
                """);
    }

    @Test
    @DisplayName("10명의 상태가 모두 있는 요청은 통과한다")
    void validate_WhenAllStatesPresent_NoViolations() {
        // when & then
        assertThat(violations(request(body()))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"myChampion", "purchasedItemIds", "allies", "enemies", "inventoryItems", "availableGold",
            "gameTimeMs"})
    @DisplayName("v4에서 필수인 필드는 여기서도 필수다")
    void validate_WhenV4RequiredFieldMissingOrNull_Rejects(String field) {
        // given
        ObjectNode missing = body();
        missing.remove(field);
        ObjectNode explicitNull = body();
        explicitNull.putNull(field);

        // when & then
        assertThat(violations(request(missing))).anyMatch(path -> path.startsWith(field));
        assertThat(violations(request(explicitNull))).anyMatch(path -> path.startsWith(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {"level", "kills", "deaths", "assists"})
    @DisplayName("내 상태 값은 하나라도 빠지면 거부한다")
    void validate_WhenMyStateFieldMissing_Rejects(String field) {
        // given
        ObjectNode body = body();
        me(body).remove(field);

        // when & then
        assertThat(violations(request(body))).contains("myChampion." + field);
    }

    @ParameterizedTest
    @CsvSource({"allies,level", "allies,kills", "allies,assists", "allies,itemIds",
            "enemies,level", "enemies,kills", "enemies,assists", "enemies,itemIds"})
    @DisplayName("아군·상대의 상태 값은 하나라도 빠지면 거부한다 — 아이템이 없으면 빈 배열을 보낸다")
    void validate_WhenPlayerStateFieldMissing_Rejects(String team, String field) {
        // given
        ObjectNode body = body();
        player(body, team, 2).remove(field);

        // when & then
        assertThat(violations(request(body))).contains(team + "[2]." + field);
    }

    @ParameterizedTest
    @CsvSource({"0,false", "1,true", "18,true", "30,true"})
    @DisplayName("레벨은 1 이상이고 상한은 두지 않는다 — 게임이 레벨 상한을 바꿔도 요청이 막히지 않는다")
    void validate_WhenLevelGiven_AcceptsOneOrMoreWithoutUpperBound(int level, boolean valid) {
        // given
        ObjectNode body = body();
        me(body).put("level", level);
        player(body, "enemies", 0).put("level", level);

        // when & then
        assertThat(violations(request(body)).isEmpty()).isEqualTo(valid);
    }

    @ParameterizedTest
    @CsvSource({"myChampion,kills", "myChampion,deaths", "myChampion,assists"})
    @DisplayName("내 킬·데스·어시스트는 0 이상이어야 한다")
    void validate_WhenMyScoreNegative_Rejects(String path, String field) {
        // given
        ObjectNode body = body();
        me(body).put(field, -1);

        // when & then
        assertThat(violations(request(body))).contains(path + "." + field);
    }

    @ParameterizedTest
    @CsvSource({"allies,kills", "allies,assists", "enemies,kills", "enemies,assists"})
    @DisplayName("아군·상대의 킬·어시스트는 0 이상이어야 한다")
    void validate_WhenPlayerScoreNegative_Rejects(String team, String field) {
        // given
        ObjectNode body = body();
        player(body, team, 1).put(field, -1);

        // when & then
        assertThat(violations(request(body))).contains(team + "[1]." + field);
    }

    @ParameterizedTest
    @CsvSource({"0,true", "7,true", "8,false"})
    @DisplayName("한 선수의 아이템은 7개까지다 — 게임의 아이템 칸이 7개다")
    void validate_WhenItemCountGiven_AcceptsUpToSeven(int count, boolean valid) {
        // given
        ObjectNode body = body();
        ArrayNode itemIds = player(body, "enemies", 0).putArray("itemIds");
        for (int index = 0; index < count; index++) {
            itemIds.add(3000 + index);
        }

        // when & then
        assertThat(violations(request(body)).isEmpty()).isEqualTo(valid);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 2147483648L})
    @DisplayName("아이템 번호는 양수이고 지원 범위 안이어야 한다")
    void validate_WhenItemIdOutOfRange_Rejects(long itemId) {
        // given
        ObjectNode body = body();
        player(body, "allies", 0).putArray("itemIds").add(itemId);

        // when & then
        assertThat(violations(request(body))).anyMatch(path -> path.startsWith("allies[0].itemIds"));
    }

    @Test
    @DisplayName("아이템 목록에 null이 섞이면 거부한다")
    void validate_WhenItemIdsContainNull_Rejects() {
        // given
        ObjectNode body = body();
        player(body, "allies", 0).putArray("itemIds").addNull();

        // when & then
        assertThat(violations(request(body))).anyMatch(path -> path.startsWith("allies[0].itemIds"));
    }

    @Test
    @DisplayName("선수의 이름·포지션과 팀 인원 검증은 v4와 같다")
    void validate_WhenChampionOrTeamSizeInvalid_RejectsLikeV4() {
        // given
        ObjectNode body = body();
        player(body, "allies", 0).put("name", "").put("position", "INVALID");
        ((ArrayNode) body.get("enemies")).remove(0);

        // when & then
        assertThat(violations(request(body))).contains("allies[0].name", "allies[0].position", "enemies");
    }

    @ParameterizedTest
    @CsvSource({"TOP,6,true", "TOP,7,false", "BOTTOM,7,true", "BOTTOM,8,false"})
    @DisplayName("구매한 Core 개수 제한은 v4와 같다")
    void validate_WhenPurchasedCountGiven_KeepsPositionSpecificLimit(String position, int count, boolean valid) {
        // given
        ObjectNode body = body();
        me(body).put("position", position);
        ArrayNode purchased = body.putArray("purchasedItemIds");
        for (int index = 0; index < count; index++) {
            purchased.add(3000 + index);
        }

        // when & then
        assertThat(violations(request(body)).isEmpty()).isEqualTo(valid);
    }

    @Test
    @DisplayName("보유 아이템 번호가 겹치면 v4처럼 거부한다")
    void validate_WhenInventoryItemIdsDuplicated_RejectsLikeV4() {
        // given
        ObjectNode body = body();
        body.withArray("inventoryItems").addObject().put("itemId", 1036).put("quantity", 1);

        // when & then
        assertThat(violations(request(body))).contains("inventoryItemIdsUnique");
    }

    @Test
    @DisplayName("상태를 뺀 나머지는 같은 값의 v4 요청과 같다")
    void toV4Request_WhenConverted_EqualsV4RequestWithoutStates() {
        // given
        ObjectNode v4Body = body();
        me(v4Body).remove(List.of("level", "kills", "deaths", "assists"));
        for (String team : List.of("allies", "enemies")) {
            v4Body.get(team).forEach(entry ->
                    ((ObjectNode) entry).remove(List.of("level", "kills", "assists", "itemIds")));
        }
        NextItemRecommendationV4Request expected = mapper.treeToValue(v4Body, NextItemRecommendationV4Request.class);

        // when
        NextItemRecommendationV4Request converted = request(body()).toV4Request();

        // then
        assertThat(converted).isEqualTo(expected);
    }

    @Test
    @DisplayName("검증을 통과한 요청의 v4 모양은 v4 요청의 검증도 통과한다 — 두 요청의 규칙이 어긋나면 여기서 드러난다")
    void toV4Request_WhenRequestIsValid_PassesV4Validation() {
        // given
        NextItemRecommendationV5Request request = request(body());

        // when
        NextItemRecommendationV4Request converted = request.toV4Request();

        // then
        assertThat(violations(request)).isEmpty();
        assertThat(VALIDATOR.validate(converted)).isEmpty();
    }

    @Test
    @DisplayName("상태 값은 요청의 선수 순서 그대로 나온다")
    void toGameState_WhenConverted_KeepsPlayerOrder() {
        // when
        ReportedGameState state = request(body()).toGameState();

        // then
        assertThat(state.me()).isEqualTo(new MyState(11, 4, 2, 3));
        assertThat(state.allies()).containsExactly(
                new ReportedGameState.Player(8, 0, 7, List.of(3089L, 3340L)),
                new ReportedGameState.Player(7, 1, 9, List.of()),
                new ReportedGameState.Player(9, 2, 5, List.of(3031L)),
                new ReportedGameState.Player(10, 3, 1, List.of(3190L)));
        assertThat(state.enemies()).containsExactly(
                new ReportedGameState.Player(12, 5, 0, List.of(3075L)),
                new ReportedGameState.Player(13, 6, 2, List.of()),
                new ReportedGameState.Player(6, 7, 4, List.of(3089L, 3020L)),
                new ReportedGameState.Player(5, 8, 6, List.of()),
                new ReportedGameState.Player(4, 9, 8, List.of(3109L)));
    }

    @Test
    @DisplayName("받지 않는 필드는 무시한다 — 앱이 보내는 riotId와, 쓰지 않는 아군·상대의 데스")
    void validate_WhenUnknownFieldsSent_IgnoresThem() {
        // given
        ObjectNode body = body();
        body.put("riotId", "player#KR1");
        player(body, "enemies", 0).put("deaths", 3);

        // when & then
        assertThat(violations(request(body))).isEmpty();
    }

    @Test
    @DisplayName("변환·검증용 메서드는 JSON에 나오지 않는다")
    void serialize_WhenWritten_HelperMethodsAreNotPartOfJson() {
        // when
        var json = mapper.valueToTree(request(body()));

        // then
        assertThat(json.has("purchasedItemCountWithinLimit")).isFalse();
        assertThat(json.has("inventoryItemIdsUnique")).isFalse();
        assertThat(json.has("v4Request")).isFalse();
        assertThat(json.has("gameState")).isFalse();
    }
}
