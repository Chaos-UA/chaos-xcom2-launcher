package chaos.xcom.launcher.highlander;

import chaos.xcom.launcher.highlander.dto.HighlanderModConfig;
import chaos.xcom.launcher.highlander.dto.HighlanderModConfig.RunOrderDeclaration;
import chaos.xcom.launcher.highlander.dto.HighlanderModsConfig;
import chaos.xcom.launcher.highlander.dto.HighlanderRunPriorityGroup;
import chaos.xcom.launcher.mod.dto.ModLoadOrder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class CommunityHighlanderUtils {
    public static final String CONFIG_DIR_NAME = "Config";
    public static final String XCOM_GAME_INI_FILE_NAME = "XComGame.ini";
    public static final int MAX_RECURSION_DIRS_DEPTH = 5;
    public static final String COMMUNITY_HIGHLANDER_MOD_ID = "X2WOTCCommunityHighlander";
    public static final String COMMUNITY_HIGHLANDER_DLC2_MOD_ID = "DLC2CommunityHighlander";
    private static final Pattern SECTION_PATTERN = Pattern.compile("\\[(\\S+)(?:\\s+(\\S+))?\\]");
    private static final Pattern ENTRY_PATTERN = Pattern.compile("(\\+?)(\\S+)=(.*)");

    static List<File> findAllXcomGameIniFiles(File modConfigDir) {
        try {
            return Files.walk(modConfigDir.toPath(), MAX_RECURSION_DIRS_DEPTH)
                    .filter(Files::isRegularFile)
                    .filter(path -> XCOM_GAME_INI_FILE_NAME.equals(path.getFileName().toString()))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to find XComGame.ini files in {}", modConfigDir, e);
            return List.of();
        }
    }

    public static HighlanderModsConfig parseHighlanderAllXComGameIniFromModDir(String mod, File modDir) {
        //File configDir = new File(modDir + "/Config");
        List<File> xcomGameIniFiles = findAllXcomGameIniFiles(modDir);
        HighlanderModsConfig combinedConfig = new HighlanderModsConfig();
        for (File xcomGameIni : xcomGameIniFiles) {
            HighlanderModsConfig config = parseHighlanderXComGameIni(mod, xcomGameIni);
            config.getModConfigs().forEach((modId, anotherModConfig) -> {
                HighlanderModConfig conf = combinedConfig.getModConfigs().computeIfAbsent(modId, k -> new HighlanderModConfig(modId));
                conf.getRequiredMods().addAll(anotherModConfig.getRequiredMods());
                conf.getIncompatibleMods().addAll(anotherModConfig.getIncompatibleMods());
                conf.getIgnoreRequiredMods().addAll(anotherModConfig.getIgnoreRequiredMods());
                conf.getRunOrderDeclarations().addAll(anotherModConfig.getRunOrderDeclarations());
                if (anotherModConfig.getRunPriorityGroup() != null && conf.getRunOrderDeclarations() == null) {
                    conf.setRunPriorityGroup(anotherModConfig.getRunPriorityGroup());
                }
            });
        }
        return combinedConfig;
    }

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
                                            currentMod.setRunPriorityGroup(HighlanderRunPriorityGroup.parseValueFromHighlanderXcomGameIni(value));
                                        } catch (IllegalArgumentException e) {
                                            log.error("Failed to parse {} {} priority group {}: {}", file, currentMod.getMod(), key, e.toString());
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

        return modsConfig;
    }
}
