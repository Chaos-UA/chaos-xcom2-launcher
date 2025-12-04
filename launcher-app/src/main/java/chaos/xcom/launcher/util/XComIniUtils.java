package chaos.xcom.launcher.util;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class XComIniUtils {

    public static void overwriteSection(File file, String section, List<String> values) {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            List<String> newLines = new ArrayList<>();
            boolean inTargetSection = false;

            for (String line : lines) {
                String trimmed = line.trim();

                // Start of the target section
                if (trimmed.equalsIgnoreCase(section)) {
                    inTargetSection = true;
                    continue; // skip this line
                }

                // End of target section when a new section starts
                if (inTargetSection && trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    inTargetSection = false;
                }

                // Keep lines that are not part of the target section
                if (!inTargetSection) {
                    newLines.add(line);
                }
            }

            // Add the new section at the end
            if (!StringUtils.isBlank(newLines.getLast())) {
                newLines.add(""); // blank line before section
            }
            newLines.add(section);
            newLines.addAll(values);

            // Write updated content back to the file
            Files.write(file.toPath(), newLines);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process: " + file, e);
        }
    }

}
