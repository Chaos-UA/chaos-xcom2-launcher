package chaos.xcom.launcher.swing;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class SwingComponentStates {
    private Map<String, Rectangle> windowBounds = new HashMap<>();
    private Map<String, Integer> splitPanePositions = new HashMap<>();
}