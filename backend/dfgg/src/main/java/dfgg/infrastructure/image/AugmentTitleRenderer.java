package dfgg.infrastructure.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * 증강 이름을 앱이 게임 카드 제목과 비교할 견본 그림으로 그린다.
 * <p>
 * 그림은 이름과 규격만으로 정해지므로 이름 단위로 캐시한다. 같은 패치를 다시 동기화해도 무효화할 것이 없다.
 */
@Component
public class AugmentTitleRenderer {

    private static final String FONT_RESOURCE = "fonts/SpoqaHanSansNeo-Regular.ttf";
    private static final int FONT_SIZE = 64;
    private static final int HEIGHT = 84;
    private static final int SIDE_PADDING = 10;

    /**
     * 위아래 범위를 정하는 글자. 칼바람 증강 이름에서는 괄호가 가장 높고 g·y가 가장 깊다.
     * 이 범위(64px에서 약 72px)를 84px 가운데에 두고 모든 그림이 같은 기준선을 쓴다.
     */
    private static final String VERTICAL_EXTENT_SAMPLE = "가힣()gjpqy";

    /** 그리기와 크기 재기가 같은 규칙을 써야 잉크가 정확히 여백 안쪽에서 시작한다. */
    private static final FontRenderContext RENDER_CONTEXT = new FontRenderContext(null, true, false);

    private final Font font;
    private final int baseline;
    private final Map<String, Optional<AugmentTitleImage>> images = new ConcurrentHashMap<>();

    public AugmentTitleRenderer() {
        this.font = loadFont().deriveFont((float) FONT_SIZE);
        this.baseline = centeredBaseline(font);
    }

    public AugmentTitleStyle style() {
        return new AugmentTitleStyle(font.getFontName(Locale.ENGLISH), FONT_SIZE, SIDE_PADDING);
    }

    /** 폰트가 그리지 못하는 글자가 있으면 그리지 않는다. 빈 네모로 그린 견본은 판별을 틀리게 만든다. */
    public Optional<AugmentTitleImage> render(String name) {
        return images.computeIfAbsent(name, this::draw);
    }

    private Optional<AugmentTitleImage> draw(String name) {
        if (font.canDisplayUpTo(name) != -1) {
            return Optional.empty();
        }
        GlyphVector glyphs = font.createGlyphVector(RENDER_CONTEXT, name);
        Rectangle ink = glyphs.getPixelBounds(RENDER_CONTEXT, 0, baseline);
        int width = ink.width + SIDE_PADDING * 2;

        BufferedImage image = new BufferedImage(width, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        graphics.setColor(Color.WHITE);
        graphics.drawGlyphVector(glyphs, SIDE_PADDING - ink.x, baseline);
        graphics.dispose();
        return Optional.of(new AugmentTitleImage(width, HEIGHT, encode(image)));
    }

    private static int centeredBaseline(Font font) {
        Rectangle2D extent = font.createGlyphVector(RENDER_CONTEXT, VERTICAL_EXTENT_SAMPLE).getVisualBounds();
        return (int) Math.round((HEIGHT - extent.getHeight()) / 2 - extent.getMinY());
    }

    private static Font loadFont() {
        try (InputStream stream = AugmentTitleRenderer.class.getClassLoader().getResourceAsStream(FONT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("견본 폰트가 없습니다: " + FONT_RESOURCE);
            }
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (IOException | FontFormatException exception) {
            throw new IllegalStateException("견본 폰트를 읽지 못했습니다: " + FONT_RESOURCE, exception);
        }
    }

    private static byte[] encode(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
