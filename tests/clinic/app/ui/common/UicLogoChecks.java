package clinic.app.ui.common;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

/** Resource loading, transparency, and HiDPI variant checks for the shared UIC seal. */
public final class UicLogoChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        BufferedImage source;
        try (InputStream input = UicLogoChecks.class.getResourceAsStream("/clinic/app/ui/auth/uic-logo.png")) {
            check(input != null, "packaged UIC logo resource is available");
            source = ImageIO.read(input);
        }
        check(source != null, "logo resource decodes as an image");
        check(source.getColorModel().hasAlpha() && source.getRGB(0, 0) >>> 24 == 0,
            "source logo has a transparent canvas");

        checkIcon(144, source);
        checkIcon(44, source);

        JLabel label = UicLogo.label(144);
        check("University of the Immaculate Conception seal".equals(
                label.getAccessibleContext().getAccessibleName()),
            "logo keeps its accessible name");
        check(label.getAccessibleContext().getAccessibleDescription().contains("transparent"),
            "logo description accurately identifies its transparent canvas");

        System.out.println("PASS: " + checks + " UIC logo checks.");
    }

    private static void checkIcon(int logicalSize, BufferedImage source) {
        ImageIcon icon = UicLogo.icon(logicalSize);
        check(icon != null, "logo resource loads at " + logicalSize + "px");
        check(icon.getIconWidth() == logicalSize && icon.getIconHeight() == logicalSize,
            "icon keeps its " + logicalSize + "px logical dimensions");
        check(icon.getImage() instanceof MultiResolutionImage,
            "icon provides resolution-aware rendering at " + logicalSize + "px");

        MultiResolutionImage image = (MultiResolutionImage) icon.getImage();
        List<Image> variants = image.getResolutionVariants();
        int maxScale = Math.max(1, Math.min(3,
            Math.min(source.getWidth(), source.getHeight()) / logicalSize));
        check(variants.size() == maxScale, "icon provides all source-backed display scales");
        for (int scale = 1; scale <= maxScale; scale++) {
            Image variant = variants.get(scale - 1);
            int expectedSize = logicalSize * scale;
            check(variant.getWidth(null) == expectedSize && variant.getHeight(null) == expectedSize,
                "variant has " + expectedSize + "px raster dimensions");
            check(variant instanceof BufferedImage buffered && buffered.getRGB(0, 0) >>> 24 == 0,
                "variant preserves transparent corners at " + expectedSize + "px");
        }
        if (maxScale >= 2) {
            Image highDpi = image.getResolutionVariant(logicalSize * 2, logicalSize * 2);
            check(highDpi.getWidth(null) == logicalSize * 2 && highDpi.getHeight(null) == logicalSize * 2,
                "Swing resolution lookup selects the 2x raster variant");
        }
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        checks++;
    }
}
