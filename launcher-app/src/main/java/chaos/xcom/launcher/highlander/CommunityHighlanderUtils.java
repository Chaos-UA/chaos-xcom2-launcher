package chaos.xcom.launcher.highlander;

import chaos.xcom.launcher.highlander.dto.HighlanderModConfig;
import chaos.xcom.launcher.highlander.dto.HighlanderModConfig.RunOrderDeclaration;
import chaos.xcom.launcher.highlander.dto.HighlanderModsConfig;
import chaos.xcom.launcher.highlander.dto.HighlanderRunPriorityGroup;
import chaos.xcom.launcher.mod.dto.ModLoadOrder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class CommunityHighlanderUtils {
    public static final String COMMUNITY_HIGHLANDER_MOD_ID = "X2WOTCCommunityHighlander";
    private static final Pattern SECTION_PATTERN = Pattern.compile("\\[(\\S+)(?:\\s+(\\S+))?\\]");
    private static final Pattern ENTRY_PATTERN = Pattern.compile("(\\+?)(\\S+)=(.*)");

    /**
     * Parses highlander entities from XComGame.ini file.
     */
    public static HighlanderModsConfig parseHighlanderXComGameIni(String mod, File file) {
        HighlanderModsConfig modsConfig = new HighlanderModsConfig();
        HighlanderModConfig currentMod = null;
        boolean isCurrentRunOrder = false;
        boolean isCurrentDependency = false;
        if (!file.isFile()) {
            return modsConfig;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith(";")) continue;

                    Matcher sectionMatcher = SECTION_PATTERN.matcher(line);
                    if (sectionMatcher.matches()) {
                        String targetMod = StringUtils.trimToEmpty(sectionMatcher.group(1));
                        String sectionTag = StringUtils.trimToEmpty(sectionMatcher.group(2));

                        // Only process CHModDependency and CHDLCRunOrder
                        if (sectionTag.equalsIgnoreCase("CHModDependency")) {
                            isCurrentDependency = true;
                            isCurrentRunOrder = false;
                            currentMod = modsConfig.getModConfigs().computeIfAbsent(targetMod, k -> new HighlanderModConfig(targetMod));
                        } else if (sectionTag.equalsIgnoreCase("CHDLCRunOrder")) {
                            isCurrentRunOrder = true;
                            isCurrentDependency = false;
                            currentMod = modsConfig.getModConfigs().computeIfAbsent(targetMod, k -> new HighlanderModConfig(targetMod));
                        } else if (line.startsWith("[") && line.endsWith("]")) {
                            isCurrentDependency = false;
                            isCurrentRunOrder = false;
                            currentMod = null;
                        }
                        continue;
                    }

                    if (currentMod != null) {
                        Matcher entryMatcher = ENTRY_PATTERN.matcher(line);
                        if (entryMatcher.matches()) {
                            String key = entryMatcher.group(2);
                            String value = entryMatcher.group(3).replaceAll("^\"|\"$", ""); // remove quotes
                            if (isCurrentRunOrder) {
                                switch (key) {
                                    case "RunAfter" -> {
                                        RunOrderDeclaration runOrderDeclaration = new RunOrderDeclaration();
                                        runOrderDeclaration.setModLoadOrder(ModLoadOrder.AFTER);
                                        runOrderDeclaration.setTargetMod(value);
                                        currentMod.getRunOrderDeclarations().add(runOrderDeclaration);
                                    }
                                    case "RunBefore" -> {
                                        RunOrderDeclaration runOrderDeclaration = new RunOrderDeclaration();
                                        runOrderDeclaration.setModLoadOrder(ModLoadOrder.BEFORE);
                                        runOrderDeclaration.setTargetMod(value);
                                        currentMod.getRunOrderDeclarations().add(runOrderDeclaration);
                                    }
                                    case "RunPriorityGroup" -> {
                                        try {
                                            currentMod.setRunPriorityGroup(HighlanderRunPriorityGroup.parse(value));
                                        } catch (IllegalArgumentException e) {
                                            log.error("Failed to parse {} {} priority group {}", file, currentMod.getMod(), key, e);
                                        }
                                    }
                                }
                            } else if (isCurrentDependency) {
                                switch (key) {
                                    case "RequiredMods" -> currentMod.getRequiredMods().add(value);
                                    case "IncompatibleMods" -> currentMod.getIncompatibleMods().add(value);
                                    case "IgnoreRequiredMods" -> currentMod.getIgnoreRequiredMods().add(value);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to process {} line: {}", file, line, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse {}", file, e);
        }

        if (modsConfig.getDependenciesCount() > 0 || modsConfig.getRunOrderDependenciesCount() > 0) {
            modsConfig.getModConfigs().computeIfAbsent(mod, v -> new HighlanderModConfig(mod))
                    .getRequiredMods().add(COMMUNITY_HIGHLANDER_MOD_ID);
        }

        HighlanderModConfig rootModConfig = modsConfig.getModConfigs().get(mod);
        if (rootModConfig != null && rootModConfig.getRunPriorityGroup() == null) {
            // use standard as default
            rootModConfig.setRunPriorityGroup(HighlanderRunPriorityGroup.RUN_STANDARD);
        }

        return modsConfig;
    }
}
