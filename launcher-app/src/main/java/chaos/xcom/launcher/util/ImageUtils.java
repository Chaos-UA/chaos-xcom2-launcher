package chaos.xcom.launcher.util;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;

public class ImageUtils {
    public static final ImageIcon WOTC_ICON = loadResourceImage("/image/wotc.png");
    public static final FlatSVGIcon REFRESH_ICON = loadResourceSvgImage("/image/refresh.svg");
    public static final FlatSVGIcon SEARCH_ICON = loadResourceSvgImage("/image/search.svg");

    private static FlatSVGIcon loadResourceSvgImage(String path) {
        URL iconURL = ImageUtils.class.getResource(path);
        return new FlatSVGIcon(iconURL);
    }

    private static ImageIcon loadResourceImage(String path) {
        URL iconURL = ImageUtils.class.getResource(path);
        return new ImageIcon(iconURL);
    }

    public static ImageIcon scaleIconForMenu(ImageIcon icon) {
        return scaleIcon(icon, 16, 16);
    }

    /**
     * Scales an existing ImageIcon to a desired width and height.
     * Uses Image.SCALE_SMOOTH for high-quality resizing.
     *
     * @param icon The original ImageIcon to scale.
     * @param targetWidth The desired width for the new icon.
     * @param targetHeight The desired height for the new icon.
     * @return A new, scaled ImageIcon, or null if the input icon is null.
     */
    public static ImageIcon scaleIcon(ImageIcon icon, int targetWidth, int targetHeight) {
        if (icon == null || icon.getImage() == null || targetWidth <= 0 || targetHeight <= 0) {
            return null;
        }

        Image img = icon.getImage();

        // Use MediaTracker to ensure the image is fully loaded before scaling,
        // which prevents potential issues with asynchronous loading (though rare with files/resources).
        try {
            MediaTracker tracker = new MediaTracker(new java.awt.Container());
            tracker.addImage(img, 0);
            tracker.waitForID(0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        // 1. Scale the image using the high-quality algorithm
        Image scaledImage = img.getScaledInstance(
                targetWidth,
                targetHeight,
                Image.SCALE_SMOOTH // Hint for high-quality scaling
        );

        // 2. Create and return a new ImageIcon from the scaled Image
        return new ImageIcon(scaledImage);
    }

    public static BufferedImage toBufferedImage(Image image) {
        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }

        // 2. Get the dimensions
        int w = image.getWidth(null);
        int h = image.getHeight(null);

        // Check for dimensions to avoid errors with uninitialized images
        if (w == -1 || h == -1) {
            // Can be replaced with error handling or a wait mechanism
            System.err.println("Image dimensions not available.");
            return null;
        }

        // 3. Create a blank BufferedImage with the dimensions and ARGB type (supports transparency)
        // TYPE_INT_ARGB is a common type for images with color and an alpha channel.
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        // 4. Draw the original image onto the BufferedImage
        Graphics2D g2d = bi.createGraphics();

        // The Component 'null' argument is fine here; it's only needed for image observation
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose(); // Release system resources used by the Graphics object

        return bi;
    }
}
