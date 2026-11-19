package dfgg.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 실제 칼바람 증강 이름 중 괄호(가장 높음), g(가장 깊음), 가장 긴 이름, 문장부호가 든 이름을 쓴다. */
class AugmentTitleRendererTest {

    private static final int HEIGHT = 84;
    private static final int SIDE_PADDING = 10;
    private static final int MIN_VERTICAL_PADDING = 5;

    private final AugmentTitleRenderer renderer = new AugmentTitleRenderer();

    @ParameterizedTest
    @ValueSource(strings = {"빵과 버터", "마법사 (물리)", "Archmage", "능력치 더하기 능력치 더하기 능력치!",
            "Don't Stop Cleavin'", "업그레이드: 죽음불꽃 손아귀", "BEES!!!"})
    @DisplayName("모든 그림의 높이는 84px다")
    void render_HeightIsAlways84(String name) throws IOException {
        // when
        BufferedImage image = decode(renderer.render(name).orElseThrow());

        // then
        assertThat(image.getHeight()).isEqualTo(HEIGHT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"빵과 버터", "마법사 (물리)", "Archmage", "능력치 더하기 능력치 더하기 능력치!",
            "Don't Stop Cleavin'", "업그레이드: 죽음불꽃 손아귀", "BEES!!!"})
    @DisplayName("글자는 왼쪽 끝이 x=10에서 시작하고 오른쪽에도 10px를 남긴다")
    void render_LeavesSidePaddingOf10(String name) throws IOException {
        // when
        BufferedImage image = decode(renderer.render(name).orElseThrow());

        // then
        assertThat(firstInkColumn(image)).isEqualTo(SIDE_PADDING);
        assertThat(lastInkColumn(image)).isEqualTo(image.getWidth() - 1 - SIDE_PADDING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"빵과 버터", "마법사 (물리)", "Archmage", "능력치 더하기 능력치 더하기 능력치!",
            "Don't Stop Cleavin'", "업그레이드: 죽음불꽃 손아귀", "BEES!!!"})
    @DisplayName("괄호 위쪽과 g 아래쪽도 잘리지 않고 위아래에 5px 이상 남는다")
    void render_KeepsInkAwayFromTopAndBottom(String name) throws IOException {
        // when
        BufferedImage image = decode(renderer.render(name).orElseThrow());

        // then
        assertThat(firstInkRow(image)).isGreaterThanOrEqualTo(MIN_VERTICAL_PADDING);
        assertThat(lastInkRow(image)).isLessThanOrEqualTo(HEIGHT - 1 - MIN_VERTICAL_PADDING);
    }

    @Test
    @DisplayName("이름이 달라도 기준선이 같아 같은 글자는 같은 높이에 그려진다")
    void render_UsesSameBaselineForEveryName() throws IOException {
        // when
        BufferedImage alone = decode(renderer.render("버터").orElseThrow());
        BufferedImage withParenthesis = decode(renderer.render("버터 (물리)").orElseThrow());

        // then
        assertThat(firstInkRow(withParenthesis)).isLessThan(firstInkRow(alone));
        assertThat(inkRowsOfColumns(withParenthesis, 0, 40)).isEqualTo(inkRowsOfColumns(alone, 0, 40));
    }

    @Test
    @DisplayName("그림은 검은 배경에 흰 글자인 8비트 흑백 PNG다")
    void render_ProducesEightBitGrayscalePng() throws IOException {
        // when
        byte[] png = renderer.render("빵과 버터").orElseThrow().png();

        // then
        BufferedImage image = decode(png);
        assertThat(image.getType()).isEqualTo(BufferedImage.TYPE_BYTE_GRAY);
        assertThat(png[24]).as("IHDR 비트 깊이").isEqualTo((byte) 8);
        assertThat(png[25]).as("IHDR 색 형식(0 = 흑백)").isEqualTo((byte) 0);
        assertThat(image.getRaster().getSample(0, 0, 0)).isZero();
        assertThat(maxSample(image)).isEqualTo(255);
    }

    @Test
    @DisplayName("응답에 싣는 폭과 높이는 그림의 실제 크기와 같다")
    void render_ReportsActualSize() throws IOException {
        // when
        AugmentTitleImage title = renderer.render("마법사 (물리)").orElseThrow();

        // then
        BufferedImage image = decode(title);
        assertThat(title.width()).isEqualTo(image.getWidth());
        assertThat(title.height()).isEqualTo(image.getHeight());
    }

    @Test
    @DisplayName("같은 이름은 다시 그리지 않고 처음 그린 그림을 준다")
    void render_WhenSameName_ReturnsCachedImage() {
        // when
        AugmentTitleImage first = renderer.render("빵과 버터").orElseThrow();
        AugmentTitleImage second = renderer.render("빵과 버터").orElseThrow();

        // then
        assertThat(second).isSameAs(first);
    }

    @Test
    @DisplayName("다른 렌더러가 그려도 같은 이름이면 같은 PNG 바이트다")
    void render_IsDeterministic() {
        // when
        byte[] first = renderer.render("빵과 버터").orElseThrow().png();
        byte[] second = new AugmentTitleRenderer().render("빵과 버터").orElseThrow().png();

        // then
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("폰트가 그리지 못하는 글자가 있으면 빈 네모로 그리지 않고 그리지 않는다")
    void render_WhenFontCannotDisplay_ReturnsEmpty() {
        // when & then
        assertThat(renderer.render("증강 😀")).isEmpty();
    }

    @Test
    @DisplayName("그림 규격은 Spoqa Han Sans Neo Regular, 64px, 좌우 여백 10px다")
    void style_DescribesFontSizeAndPadding() {
        // when
        AugmentTitleStyle style = renderer.style();

        // then
        assertThat(style.font()).isEqualTo("Spoqa Han Sans Neo Regular");
        assertThat(style.fontSize()).isEqualTo(64);
        assertThat(style.padding()).isEqualTo(10);
    }

    private static BufferedImage decode(AugmentTitleImage title) throws IOException {
        return decode(title.png());
    }

    private static BufferedImage decode(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static int firstInkColumn(BufferedImage image) {
        for (int x = 0; x < image.getWidth(); x++) {
            if (hasInkInColumn(image, x)) {
                return x;
            }
        }
        return -1;
    }

    private static int lastInkColumn(BufferedImage image) {
        for (int x = image.getWidth() - 1; x >= 0; x--) {
            if (hasInkInColumn(image, x)) {
                return x;
            }
        }
        return -1;
    }

    private static int firstInkRow(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            if (hasInkInRow(image, y)) {
                return y;
            }
        }
        return -1;
    }

    private static int lastInkRow(BufferedImage image) {
        for (int y = image.getHeight() - 1; y >= 0; y--) {
            if (hasInkInRow(image, y)) {
                return y;
            }
        }
        return -1;
    }

    private static boolean hasInkInColumn(BufferedImage image, int x) {
        for (int y = 0; y < image.getHeight(); y++) {
            if (image.getRaster().getSample(x, y, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasInkInRow(BufferedImage image, int y) {
        for (int x = 0; x < image.getWidth(); x++) {
            if (image.getRaster().getSample(x, y, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    /** 왼쪽 글자들(같은 "버터")이 그려진 행 패턴. 기준선이 같으면 두 그림에서 같다. */
    private static String inkRowsOfColumns(BufferedImage image, int fromX, int toX) {
        StringBuilder rows = new StringBuilder();
        for (int y = 0; y < image.getHeight(); y++) {
            boolean ink = false;
            for (int x = fromX; x < toX; x++) {
                ink |= image.getRaster().getSample(x, y, 0) > 0;
            }
            rows.append(ink ? '#' : '.');
        }
        return rows.toString();
    }

    private static int maxSample(BufferedImage image) {
        int max = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                max = Math.max(max, image.getRaster().getSample(x, y, 0));
            }
        }
        return max;
    }
}
