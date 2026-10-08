package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.match.PurchaseCostEstimator;
import dfgg.infrastructure.external.dto.ItemData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PurchaseCostEstimatorTest {

    private final PurchaseCostEstimator estimator = new PurchaseCostEstimator();

    @Test
    void 단품과_반복_재료_조합의_비용_가설을_계산한다() {
        Map<String, ItemData> catalog = Map.of(
                "1036", item(350, List.of()),
                "3071", item(1200, List.of("1036", "1036")));

        assertThat(estimator.estimate(1036, List.of(), catalog).orElseThrow()).isEqualTo(350);
        var combined = estimator.estimate(3071, List.of(1036, 1036), catalog);
        assertThat(combined.orElseThrow()).isEqualTo(500);
    }

    @Test
    void 레시피보다_많은_재료나_상위_하위_재료의_중복_차감을_거부한다() {
        Map<String, ItemData> catalog = Map.of(
                "1036", item(350, List.of()),
                "3133", item(800, List.of("1036")),
                "3071", item(1200, List.of("3133")));

        assertThat(estimator.estimate(3071, List.of(3133), catalog).orElseThrow()).isEqualTo(400);
        assertThat(estimator.estimate(3071, List.of(3133, 1036), catalog).isEmpty()).isTrue();
        assertThat(estimator.estimate(3071, List.of(1036, 1036), catalog).isEmpty()).isTrue();
    }

    @Test
    void 카탈로그에서_동등성이_확인된_2421은_존야의_재료로_차감한다() {
        Map<String, ItemData> catalog = Map.of(
                "1058", item(1200, List.of()),
                "2420", item(1600, List.of("1052", "1029", "1052"), List.of("3157")),
                "2421", item(1600, List.of("1052", "1029", "1052"), List.of("3157")),
                "3157", item(3250, List.of("1058", "2420")));

        var result = estimator.estimate(3157, List.of(1058, 2421), catalog);

        assertThat(result.orElseThrow()).isEqualTo(450);
    }

    @Test
    void 특정_ID를_몰라도_같은_조합과_가격의_변형_재료를_차감한다() {
        Map<String, ItemData> catalog = Map.of(
                "7000", item(300, List.of()),
                "7001", item(200, List.of()),
                "9000", item(800, List.of("7000", "7001", "7000"), List.of("9100")),
                "9001", item(800, List.of("7001", "7000", "7000"), List.of("9100")),
                "9100", item(1300, List.of("9000")));

        var result = estimator.estimate(9100, List.of(9001), catalog);

        assertThat(result.orElseThrow()).isEqualTo(500);
    }

    @Test
    void 변형_재료의_조합_관계나_가격이_다르면_차감하지_않는다() {
        Map<String, ItemData> missingUpgrade = Map.of(
                "2420", item(1600, List.of("1052", "1029", "1052"), List.of("3157")),
                "2421", item(1600, List.of("1052", "1029", "1052"), List.of()),
                "3157", item(3250, List.of("2420")));
        Map<String, ItemData> differentPrice = Map.of(
                "2420", item(1600, List.of("1052", "1029", "1052"), List.of("3157")),
                "2421", item(1500, List.of("1052", "1029", "1052"), List.of("3157")),
                "3157", item(3250, List.of("2420")));
        Map<String, ItemData> differentRecipe = Map.of(
                "2420", item(1600, List.of("1052", "1029", "1052"), List.of("3157")),
                "2421", item(1600, List.of("1052", "1029"), List.of("3157")),
                "3157", item(3250, List.of("2420")));

        assertThat(estimator.estimate(3157, List.of(2421), missingUpgrade).isEmpty()).isTrue();
        assertThat(estimator.estimate(3157, List.of(2421), differentPrice).isEmpty()).isTrue();
        assertThat(estimator.estimate(3157, List.of(2421), differentRecipe).isEmpty()).isTrue();
    }

    @Test
    void 같은_조합_위치에_대체_재료_후보가_여러_개면_미확정으로_둔다() {
        Map<String, ItemData> catalog = Map.of(
                "9000", item(800, List.of("7000"), List.of("9100")),
                "9001", item(800, List.of("7000"), List.of("9100")),
                "9002", item(800, List.of("7000"), List.of("9100")),
                "9100", item(2500, List.of("9000")));

        var result = estimator.estimate(9100, List.of(9001, 9002), catalog);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void 할인과_확인되지_않은_변형_재료는_정가로_확정하지_않는다() {
        Map<String, ItemData> catalog = Map.of(
                "2055", item(75, List.of()),
                "1001", item(300, List.of(), List.of("3020")),
                "2422", item(300, List.of(), List.of("3020"), false),
                "3020", item(1200, List.of("1001")));

        var ward = estimator.estimate(2055, List.of(), catalog);
        assertThat(ward.isEmpty()).isTrue();
        assertThat(estimator.estimate(3020, List.of(2422), catalog).isEmpty()).isTrue();
    }

    @Test
    void 가격이_없거나_조합_금액이_0이하면_금액을_반환하지_않는다() {
        Map<String, ItemData> catalog = Map.of(
                "1036", item(350, List.of()),
                "3071", item(300, List.of("1036")),
                "9999", item(null, List.of()));

        assertThat(estimator.estimate(9999, List.of(), catalog).isEmpty()).isTrue();
        assertThat(estimator.estimate(3071, List.of(1036), catalog).isEmpty()).isTrue();
    }

    private static ItemData item(Integer total, List<String> from) {
        return item(total, from, List.of());
    }

    private static ItemData item(Integer total, List<String> from, List<String> into) {
        return item(total, from, into, true);
    }

    private static ItemData item(Integer total, List<String> from, List<String> into, boolean purchasable) {
        return new ItemData("test", from, into, List.of(), Map.of("11", true), false, null,
                new ItemData.Gold(100, total, purchasable), null, true, false);
    }
}
