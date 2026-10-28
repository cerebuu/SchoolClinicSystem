package clinic.app.ui.common;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

/** Loads the official UIC seal and scales it proportionally for Swing views. */
public final class UicLogo {
    private static final String RESOURCE = "/clinic/app/ui/auth/uic-logo.png";
    private static final int MAX_SCALE = 3;
    private static final BufferedImage SOURCE = load();
    private static final Map<Integer, ImageIcon> ICONS = new HashMap<>();

    private UicLogo() {}

    public static JLabel label(int boxSize) {
        JLabel label = new JLabel(icon(boxSize));
        label.setPreferredSize(new java.awt.Dimension(boxSize, boxSize));
        label.setMinimumSize(new java.awt.Dimension(boxSize, boxSize));
        label.setMaximumSize(new java.awt.Dimension(boxSize, boxSize));
        label.getAccessibleContext().setAccessibleName("University of the Immaculate Conception seal");
        label.getAccessibleContext().setAccessibleDescription("Official UIC seal with a transparent background.");
        if (SOURCE == null) label.setText("UIC");
        return label;
    }

    public static synchronized ImageIcon icon(int boxSize) {
        if (SOURCE == null || boxSize <= 0) return null;
        return ICONS.computeIfAbsent(boxSize, UicLogo::scale);
    }

    private static ImageIcon scale(int boxSize) {
        int maxScale = Math.max(1, Math.min(MAX_SCALE,
            Math.min(SOURCE.getWidth(), SOURCE.getHeight()) / boxSize));
        Image[] variants = new Image[maxScale];
        for (int scale = 1; scale <= maxScale; scale++) {
            variants[scale - 1] = render(boxSize, scale);
        }
        return new ImageIcon(new BaseMultiResolutionImage(variants));
    }

    private static BufferedImage render(int boxSize, int scale) {
        int pixelBoxSize = Math.multiplyExact(boxSize, scale);
        double factor = Math.min(boxSize / (double) SOURCE.getWidth(), boxSize / (double) SOURCE.getHeight());
        int width = Math.max(1, (int) Math.round(SOURCE.getWidth() * factor * scale));
        int height = Math.max(1, (int) Math.round(SOURCE.getHeight() * factor * scale));
        BufferedImage scaled = new BufferedImage(pixelBoxSize, pixelBoxSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.drawImage(SOURCE, (pixelBoxSize - width) / 2, (pixelBoxSize - height) / 2, width, height, null);
        graphics.dispose();
        return scaled;
    }

    private static BufferedImage load() {
        try (InputStream resource = UicLogo.class.getResourceAsStream(RESOURCE)) {
            if (resource != null) return ImageIO.read(resource);
        } catch (IOException ignored) {
            // Fall through to the source-tree path for direct javac launches.
        }
        Path sourceTree = Path.of("src/clinic/app/ui/auth/uic-logo.png");
        try {
            return Files.isRegularFile(sourceTree) ? ImageIO.read(sourceTree.toFile()) : null;
        } catch (IOException ignored) {
            return null;
        }
    }
}
