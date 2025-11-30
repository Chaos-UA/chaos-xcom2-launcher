package chaos.xcom.launcher.gui.component;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

@Slf4j
public class XImage extends JPanel {
    private BufferedImage image;

    public XImage() {}

    public void setImage(File imageFile) {
        try {
            image = null;
            if (imageFile != null && imageFile.exists()) {
                image = ImageIO.read(imageFile);
            }
        } catch (Exception e) {
            log.error("Failed to open image file {}", imageFile, e);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

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
            int y = (panelHeight - newHeight) / 2;

            g.drawImage(image, x, y, newWidth, newHeight, this);
        }
    }
}
