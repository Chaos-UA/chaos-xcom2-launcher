package chaos.xcom.launcher.gui.component;

import chaos.xcom.launcher.util.ImageUtils;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

@Slf4j
public class XImage extends JPanel {

    private volatile File currentImageFile;
    private volatile BufferedImage fallbackImage;
    private volatile BufferedImage currentImage;

    public XImage(Image fallbackImage) {
        this.fallbackImage = ImageUtils.toBufferedImage(fallbackImage);
    }

    public void setImage(Image image) {
        this.currentImageFile = null;
        this.currentImage = image == null ? null : ImageUtils.toBufferedImage(image);
        repaint();
    }

    public void setImage(File imageFile) {
        this.currentImageFile = imageFile;
        this.currentImage = null;
        repaint();
        if (imageFile != null && imageFile.exists()) {
            Thread.ofVirtual().start(() -> { // load in async to not block ui
                try {
                    BufferedImage loadedImage = ImageIO.read(imageFile);
                    if (imageFile.equals(currentImageFile)) {
                        this.currentImage = loadedImage;
                        repaint();
                    }
                } catch (Exception e) {
                    log.error("Failed to open image file {}", imageFile, e);
                }
            });
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage image = currentImage;
        if (image == null) {
            image = fallbackImage;
        }
        if (image != null) {
            int panelWidth = getWidth();
            int panelHeight = getHeight();

            // calculate scaled size while keeping aspect ratio
            float ratio = Math.min((float) panelWidth / image.getWidth(),
                    (float) panelHeight / image.getHeight());
            int newWidth = Math.round(image.getWidth() * ratio);
            int newHeight = Math.round(image.getHeight() * ratio);

            // center the image
            int x = (panelWidth - newWidth) / 2;
            //int y = (panelHeight - newHeight) / 2;
            // align image to the top instead of centering vertically
            int y = 0;

            g.drawImage(image, x, y, newWidth, newHeight, this);
        }
    }
}
