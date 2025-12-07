package chaos.xcom.launcher.swing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Rectangle {
    private int x;
    private int y;
    private int width;
    private int height;

    public static Rectangle from(java.awt.Rectangle r) {
        return new Rectangle(r.x, r.y, r.width, r.height);
    }

    public java.awt.Rectangle toAwtRectangle() {
        return new java.awt.Rectangle(x, y, width, height);
    }
}