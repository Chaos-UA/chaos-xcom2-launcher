package chaos.xcom.launcher.service;

import lombok.Data;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@Data
public class SwingComponentStates {
    private Map<String, Rectangle> windowBounds = new HashMap<>();
    private Map<String, Integer> splitPanePositions = new HashMap<>();
}