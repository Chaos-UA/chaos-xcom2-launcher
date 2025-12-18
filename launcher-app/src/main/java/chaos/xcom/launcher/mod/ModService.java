package chaos.xcom.launcher.mod;

import chaos.db.gen.tables.records.ModRecord;
import chaos.db.gen.tables.records.UserModRuleRecord;
import chaos.xcom.launcher.common.JsonConverter;
import chaos.xcom.launcher.db.property.DbProperties;
import chaos.xcom.launcher.event.SkinChangeEvent;
import chaos.xcom.launcher.gui.EditModDependencyDialog.EditModDependencyDialog;
import chaos.xcom.launcher.gui.LookAndFeelService;
import chaos.xcom.launcher.gui.MainForm.LauncherService;
import chaos.xcom.launcher.gui.MainForm.MainForm;
import chaos.xcom.launcher.gui.MainForm.MainFormMenuBar;
import chaos.xcom.launcher.highlander.CommunityHighlanderUtils;
import chaos.xcom.launcher.highlander.dto.HighlanderModConfig;
import chaos.xcom.launcher.highlander.dto.HighlanderModConfig.RunOrderDeclaration;
import chaos.xcom.launcher.highlander.dto.HighlanderRunPriorityGroup;
import chaos.xcom.launcher.mod.dto.*;
import chaos.xcom.launcher.mod.dto.Mod.ModLoadOrderDeclaration;
import chaos.xcom.launcher.mod.rule.UserModRuleRepository;
import chaos.xcom.launcher.mod.rule.UserRuleDeclaration;
import chaos.xcom.launcher.steam.SteamMod;
import chaos.xcom.launcher.steam.SteamMod.SteamRequiredMod;
import chaos.xcom.launcher.steam.SteamService;
import chaos.xcom.launcher.steam.SteamSyncProgress;
import chaos.xcom.launcher.swing.SwingService;
import chaos.xcom.launcher.util.FileUtils;
import chaos.xcom.launcher.util.SortUtils;
import chaos.xcom.launcher.util.SortUtils.SortItem;
import chaos.xcom.launcher.util.SortUtils.SortResult;
import chaos.xcom.launcher.util.XComModFinderUtils;
import chaos.xcom.launcher.util.XComUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Singleton;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

@Startup
@Singleton
@RequiredArgsConstructor
@Slf4j
public class ModService {

    private final DbProperties dbProps;
    private final LauncherService launcherService;
    private final MainFormMenuBar mainFormMenuBar;
    private final SwingService swingService;
    /**
     * Required to be injected before this class init to already init skin.
     */
    private final LookAndFeelService lookAndFeelService;
    private final JsonConverter jsonConverter;
    private MainForm mainForm;
    private final ModRepository modRepository;
    private final SteamService steamService;
    private final UserModRuleRepository userModRuleRepository;

    /**
     * String is mod ID, and values are mods under this ID, multiple values in case of duplicates.
     */
   // private Map<String, List<Mod>> modsMap = new HashMap<>();

    private LinkedHashMap<String, Mod> allMods = new LinkedHashMap<>();
    private HashMap<String, Mod> steamModIdToModMap = new HashMap<>();
    private HashMap<String, List<ReplacedByMod>> replacedByModIdToModMap = new HashMap<>();
    private Map<String, List<UserRuleDeclaration>> userRulesByMod = new HashMap<>();
    private Map<String, List<UserRuleDeclaration>> userRulesByTargetMod = new HashMap<>();

    public static ModService get() {
        return CDI.current().select(ModService.class).get();
    }

    @PostConstruct
    void init() {
        recreateForm();
        reloadModsFromDirs();
    }

    public void onSkinChange(@ObservesAsync SkinChangeEvent event) {
        SwingUtilities.invokeLater(() -> recreateForm());
    }

    private synchronized void recreateForm() {
        List<Mod> mods = new ArrayList<>();
        if (mainForm != null) {
            mods = mainForm.getMods();
            mainForm.dispose();
            mainForm = null;
        }
        this.mainForm = new MainForm(launcherService, mainFormMenuBar, swingService);
        this.mainForm.init();
        try {
            mainForm.setMods(mods);
        } catch (Exception e) {
            log.error("Failed to recreate form", e);
        }
    }

    public void setIgnoreDependency(Mod mod, ModDeclaredDependency dependency, boolean ignore) {
        String ignoreDependencyKey = mod.toIgnoredDependencyKey(dependency);
        if (ignore) {
            mod.getIgnoredDependenciesKeys().add(ignoreDependencyKey);
        } else {
            mod.getIgnoredDependenciesKeys().remove(ignoreDependencyKey);
        }
        log.info("Mod {} ignored={} declared dependency {}", mod.getId(), ignore, ignoreDependencyKey);
        saveModToDb(mod);
        recalculateModDependencies();
    }

    public void setSteamModId(Mod mod, String newSteamModId) {
        newSteamModId = StringUtils.trimToNull(newSteamModId);
        if (newSteamModId == null) {
            newSteamModId = mod.getSteamModIdByDirName();
        }
        mod.setSteamDbModId(newSteamModId);
        mod.setSteamMod(new SteamMod());
        mod.getSteamMod().setSteamModId(newSteamModId);
        log.info("Mod {} changed manually steam ID to {}", mod.getId(), mod.getSteamDbModId());
        saveModToDb(mod);
        reloadModsFromDirs();
    }

    public void selectMod(Mod mod) {
        mainForm.selectModIfExist(mod);
        log.info("Selected mod {}", mod.getId());
    }

    public void setModsActive(Collection<Mod> mods, boolean active) {
        for (Mod mod : mods) {
            mod.setActive(active);
            mod.setNewMod(false);
        }
        saveModsToDb(mods);
        log.info("{} mods is now {}", mods.size(), active ? "active" : "inactive");
        recalculateModDependencies();
    }

    public void setModActive(String modId, boolean active) {
        Mod mod = allMods.get(modId);
        if (mod == null) {
            log.warn("Mod with ID {} not found", modId);
        } else {
            if (mod.isActive() == active) {
                log.info("Mod with ID {} is already {}", modId, active ? "active" : "inactive");
                //return;
            }
            mod.setActive(active);
            mod.setNewMod(false);
            log.info("Mod with ID {} is now {}", modId, active ? "active" : "inactive");
            saveModToDb(mod);
        }
        recalculateModDependencies();
    }

    public void openUserModRulesEditorDialog(Mod mod) {
        log.info("Mod {} editing mod rules", mod.getId());
        new EditModDependencyDialog(mod, allMods, userRulesByMod, userRulesByTargetMod);
    }

    @PreDestroy
    public void saveAll() {
        saveAllModsToDb();
    }

    private void saveModToDb(Mod mod) {
        ModRecord modDbRecord = toDbModRecord(mod);
        modRepository.save(modDbRecord);
        log.info("Saved mod {} to DB", mod.getId());
    }

    private void saveModsToDb(Collection<Mod> mods) {
        List<ModRecord> modDbRecord = mods.stream().map(v -> toDbModRecord(v)).toList();
        modRepository.save(modDbRecord);
        log.info("Saved {} mods to DB", mods.size());
    }

    private List<Mod> getAllModsIncludingIgnoredDuplicates() {
        List<Mod> result = new ArrayList<>(allMods.values().size());
        for (Mod mod : allMods.values()) {
            result.add(mod);
            result.addAll(mod.getDuplicateMods());
        }
        return result;
    }

    public void calculateAllModsSizeAndSave() {
        for (Mod mod : getAllModsIncludingIgnoredDuplicates()) {
            mod.setSize(FileUtils.calculateDirectorySize(mod.getDirectory()).orElse(null));
        }
        saveAllModsToDb();
        log.info("All {} mods size have been calculated and saved to DB", allMods.size());
        mainForm.onMainTableDataChange();
    }

    /**
     * @param userRules to save. All not included records will be removed from DB.
     */
    public void applyUserModRules(Collection<UserRuleDeclaration> userRules) {
        // remove duplicates and invalid values
        List<UserRuleDeclaration> validRules = new ArrayList<>(userRules.size());
        Set<String> unique = new HashSet<>();
        for (UserRuleDeclaration userRule : userRules) {
            if (userRule.getType() == null || userRule.getTargetModId() == null || userRule.getModId() == null
                    || Objects.equals(userRule.getModId(), userRule.getTargetModId())) {
                continue;
            }
            if (unique.add(userRule.getModId() + userRule.getTargetModId() + userRule.getTargetModId())) {
                // add unique only
                validRules.add(userRule);
            }
        }

        List<Long> userRuleIds = validRules.stream().map(UserRuleDeclaration::getId).filter(Objects::nonNull).toList();
        List<Long> recordIdsToDelete = userModRuleRepository.findNotProvidedUserIds(userRuleIds);
        userModRuleRepository.save(validRules.stream().map(this::toUserModRuleRecord).toList());
        userModRuleRepository.deleteByIds(recordIdsToDelete);
        log.info("Modified user mod rules, saved {}, deleted {}", validRules.size(), recordIdsToDelete.size());
        reloadModsFromDirs();
    }

    private UserModRuleRecord toUserModRuleRecord(UserRuleDeclaration userRule) {
        UserModRuleRecord record = new UserModRuleRecord();
        record.setId(userRule.getId());
        record.setMod1Id(userRule.getModId());
        record.setType(userRule.getType().name());
        record.setMod2Id(userRule.getTargetModId());
        return record;
    }

    public void saveAllModsToDb() {
        List<ModRecord> modRecords = allMods.values().stream().map(this::toDbModRecord).toList();
        modRepository.save(modRecords);
        log.info("Saved {} mods to DB", allMods.size());
    }

    private ModRecord toDbModRecord(Mod mod) {
        ModRecord modDbRecord = new ModRecord();
        modDbRecord.setId(mod.getId());
        modDbRecord.setTitle(mod.getTitle());
        modDbRecord.setIsActive(mod.isActive());
        modDbRecord.setIsNew(mod.isNewMod());
        modDbRecord.setDirectory(mod.getDirectory().getAbsolutePath());
        modDbRecord.setSizeBytes(mod.getSize());
        SteamMod steamMod = mod.getSteamMod();
        modDbRecord.setSteamModId(steamMod.getSteamModId());

        // save ignored dependencies if exist
        Set<String> ignoredDependenciesKeys = mod.getIgnoredDependenciesKeys();
        Set<String> dependenciesKeys = mod.getDeclaredDependencies().stream()
                .map(mod::toIgnoredDependencyKey).collect(Collectors.toSet());
        ignoredDependenciesKeys.removeIf(v -> !dependenciesKeys.contains(v));
        modDbRecord.setIgnoredDependenciesKeys(jsonConverter.toJson(ignoredDependenciesKeys));
        return modDbRecord;
    }

    public Optional<Mod> findModById(String modId) {
        if (modId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(allMods.get(modId));
    }

    public Optional<Mod> findModBySteamModId(String steamModId) {
        return Optional.ofNullable(this.steamModIdToModMap.get(steamModId));
    }

    public void syncAllSteamMods() {
        steamService.SyncSteamModsInAsync(prepareSteamModsForSync(allMods.values()));
    }

    public void syncMissingSteamMods() {
        steamService.SyncSteamModsInAsync(prepareSteamModsForSync(allMods.values().stream()
                .filter(v -> v.getSteamMod().getUpdatedAt() == null)
                .toList()));
    }

    public void syncSteamMods(Collection<Mod> mods) {
        steamService.SyncSteamModsInAsync(prepareSteamModsForSync(mods));
    }

    /**
     * @return mods sorted by not synced yet then from most outdated to most recently synced.
     */
    static List<String> prepareSteamModsForSync(Collection<Mod> mods) {
        return mods.stream()
                .filter(v -> v != null && v.getSteamMod().getSteamModId() != null)
                .sorted(Comparator.comparing(
                        mod -> mod.getSteamMod().getUpdatedAt(),
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ))
                .map(v -> v.getSteamMod().getSteamModId())
                .collect(Collectors.toList());
    }

    public void onSteamSyncProgress(@ObservesAsync SteamSyncProgress steamSyncProgress) {
        SwingUtilities.invokeLater(() -> mainForm.onSteamSyncProgress(steamSyncProgress));
        if (steamSyncProgress.isComplete()) {
            recalculateModDependencies();
        }
    }

    public void onSteamModParsed(@ObservesAsync SteamMod steamMod) {
        Mod mod = steamModIdToModMap.get(steamMod.getSteamModId());
        if (mod == null) {
            return;
        }
        SteamMod currentSteamMod = mod.getSteamMod();
        if (!currentSteamMod.getRequiredSteamMods().equals(steamMod.getRequiredSteamMods())) {
            log.info("Detected changes in steam mod {} for mod {}. Recalculating",
                    steamMod.getSteamModName(), mod.getId());
            recalculateModDependencies();
        } else {
            log.info("No dependency changes detected in steam mod {} for mod {}", steamMod.getSteamModName(), mod.getId());
            currentSteamMod.setUpdatedAt(steamMod.getUpdatedAt());
            currentSteamMod.setSteamModName(steamMod.getSteamModName());
            currentSteamMod.setDescription(steamMod.getDescription());
            currentSteamMod.setRequiredSteamMods(steamMod.getRequiredSteamMods());
        }
        SwingUtilities.invokeLater(mainForm::onMainTableDataChange);
    }

    public void removeDeletedMods(List<Mod> deletedMods) {
        deletedMods = deletedMods.stream().filter(v -> !v.isExist()).toList();
        log.info("Removing deleted mods: {}", deletedMods.stream().map(Mod::getId).toList());
        modRepository.deleteByIds(deletedMods.stream().map(Mod::getId).toList());
        reloadModsFromDirs();
    }

    /**
     * @return map with key as modId, and values as mods which has ignoreRequired mod for key modId.
     */
    private LinkedHashMap<String, List<ReplacedByMod>> extractIgnoreRequiredMods(Collection<Mod> mods) {
        LinkedHashMap<String, List<ReplacedByMod>> result = new LinkedHashMap<>();
        for (Mod mod : mods) {
            for (HighlanderModConfig config : mod.getHighlanderModsConfig().getModConfigs().values()) {
                for (String ignoreRequiredMod : config.getIgnoreRequiredMods()) {
                    ReplacedByMod replacedByMod = new ReplacedByMod(ignoreRequiredMod, config.getMod(), mod.getId());
                    result.computeIfAbsent(ignoreRequiredMod, k -> new ArrayList<>()).add(replacedByMod);
                }
            }
        }
        return result;
    }

    public Collection<Mod> findModsByIds(List<String> mods) {
        return mods.stream().map(v -> allMods.get(v)).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public List<UserRuleDeclaration> getUserModRulesByMod(String modId) {
        return userRulesByMod.getOrDefault(modId, List.of());
    }

    public boolean hasDeclaredUserDependency(Mod mod) {
        return userRulesByMod.containsKey(mod.getId());
    }

    public int getDeclaredUserDependenciesSize(Mod mod) {
        return userRulesByMod.getOrDefault(mod.getId(), List.of()).size();
    }


    @Data
    private static class ReplacedByMod {
        /**
         * IgnoreRequiredMod
         */
        private final String mod;
        /**
         * Mod alternative
         */
        private final String replacedByMod;
        private final String declaredInMod;
    }

    synchronized void recalculateModDependencies() {
        // clear all dependencies state
        for (Mod mod : allMods.values()) {
            mod.clearStateForLoadOrderCalculation();
        }
        // load user rules first so replacements declared by user are included into replacedBy map
        resetAndFillUserDeclarationRules();
        resetIgnoreRequiredReplacements();
        fillSteamMods(allMods);
        resetModsDeclaredDependencies(allMods);

        LinkedHashMap<String, Mod> loadOrderSortedMods = sortModsForLoadOrder(allMods.values());

        this.allMods = loadOrderSortedMods;
        calculateModsStatuses();

        // expand duplicate mods to show in table
        ArrayList<Mod> tableMods = new ArrayList<>(loadOrderSortedMods.size());
        for (Mod mod : allMods.values()) {
            tableMods.add(mod);
            for (Mod duplicateMod : mod.getDuplicateMods()) {
                duplicateMod.setActive(false);
                duplicateMod.setLoadOrder(mod.getLoadOrder());
                duplicateMod.getStatuses().add(ModStatus.DUPLICATE);
                duplicateMod.getStatuses().add(ModStatus.INACTIVE);
                duplicateMod.setSteamMod(mod.getSteamMod());
                tableMods.add(duplicateMod);
            }
        }

        if (dbProps.syncMissingSteamModsOnReload.get()) {
            this.syncMissingSteamMods();
        }
        SwingUtilities.invokeLater(() -> mainForm.setMods(tableMods));
    }

    private void resetAndFillUserDeclarationRules() {
        this.userRulesByMod = new HashMap<>();
        this.userRulesByTargetMod = new HashMap<>();
        List<UserModRuleRecord> records = userModRuleRepository.findAll();
        for (UserModRuleRecord record : records) {
            try {
                UserRuleDeclaration userModRule = new UserRuleDeclaration();
                userModRule.setId(record.getId());
                userModRule.setModId(record.getMod1Id());
                userModRule.setType(UserRuleDeclaration.RuleType.valueOf(record.getType()));
                userModRule.setTargetModId(record.getMod2Id());
                userRulesByMod.computeIfAbsent(userModRule.getModId(), k -> new ArrayList<>()).add(userModRule);
                userRulesByTargetMod.computeIfAbsent(userModRule.getTargetModId(), k -> new ArrayList<>()).add(userModRule);
            } catch (Exception e) {
                log.error("Failed to read record: {}", record);
            }
        }
        log.info("Loaded {} user mod rules", records.size());
    }

    private void resetIgnoreRequiredReplacements() {
        // start with highlander-defined replacements
        replacedByModIdToModMap = extractIgnoreRequiredMods(allMods.values());
        // include user-declared REPLACED rules so they behave similarly to highlander ignoreRequired
        for (List<UserRuleDeclaration> rules : userRulesByMod.values()) {
            for (UserRuleDeclaration rule : rules) {
                if (rule.getType() == UserRuleDeclaration.RuleType.REPLACED) {
                    ReplacedByMod replacedByMod = new ReplacedByMod(rule.getTargetModId(), rule.getModId(), rule.getModId());
                    replacedByModIdToModMap.computeIfAbsent(rule.getTargetModId(), k -> new ArrayList<>()).add(replacedByMod);
                }
            }
        }
    }

    private void resetModsDeclaredDependencies(LinkedHashMap<String, Mod> allMods) {
        for (Mod mod : allMods.values()) {
            mod.setDeclaredDependencies(new ArrayList<>());

            SteamMod steamMod = mod.getSteamMod();
            for (SteamRequiredMod requiredSteamMod : steamMod.getRequiredSteamMods()) {
                Mod resolvedSteamMod = steamModIdToModMap.get(requiredSteamMod.getSteamModId());
                ModDeclaredDependency declaredDependency = new ModDeclaredDependency();
                declaredDependency.setMod(mod.getId());
                if (resolvedSteamMod == null) {
                    declaredDependency.setHasError(true);
                } else {
                    declaredDependency.setTargetMod(resolvedSteamMod.getId());
                }
                declaredDependency.setDependencyType(DependencyType.REQUIRED);
                declaredDependency.setSteamRequiredMod(requiredSteamMod);
                declaredDependency.getSources().add(DeclarationSource.STEAM);
                mod.addDeclaredDependency(declaredDependency);
            }


            for (HighlanderModConfig highlanderModConfig : mod.getHighlanderModsConfig().getModConfigs().values()) {
                for (String requiredModId : highlanderModConfig.getRequiredMods()) {
                    ModDeclaredDependency declaredDependency = new ModDeclaredDependency();
                    declaredDependency.setMod(highlanderModConfig.getMod());
                    declaredDependency.setTargetMod(requiredModId);
                    declaredDependency.setDependencyType(DependencyType.REQUIRED);
                    declaredDependency.getSources().add(DeclarationSource.HIGHLANDER);
                    mod.addDeclaredDependency(declaredDependency);
                }
                for (String incompatibleModId : highlanderModConfig.getIncompatibleMods()) {
                    ModDeclaredDependency declaredDependency = new ModDeclaredDependency();
                    declaredDependency.setMod(highlanderModConfig.getMod());
                    declaredDependency.setTargetMod(incompatibleModId);
                    declaredDependency.setDependencyType(DependencyType.INCOMPATIBLE);
                    declaredDependency.getSources().add(DeclarationSource.HIGHLANDER);
                    mod.addDeclaredDependency(declaredDependency);
                }
                for (String ignoredRequiredModId : highlanderModConfig.getIgnoreRequiredMods()) {
                    ModDeclaredDependency declaredDependency = new ModDeclaredDependency();
                    declaredDependency.setMod(highlanderModConfig.getMod());
                    declaredDependency.setTargetMod(ignoredRequiredModId);
                    declaredDependency.setDependencyType(DependencyType.REPLACED);
                    declaredDependency.getSources().add(DeclarationSource.HIGHLANDER);
                    mod.addDeclaredDependency(declaredDependency);
                }
            }

            for (UserRuleDeclaration ruleDeclaration : userRulesByMod.getOrDefault(mod.getId(), List.of())) {
                if (ruleDeclaration.getType() == UserRuleDeclaration.RuleType.REQUIRED) {
                    ModDeclaredDependency declaredDependency = new ModDeclaredDependency();
                    declaredDependency.setMod(ruleDeclaration.getModId());
                    declaredDependency.setTargetMod(ruleDeclaration.getTargetModId());
                    declaredDependency.setDependencyType(DependencyType.REQUIRED);
                    declaredDependency.getSources().add(DeclarationSource.USER);
                    mod.addDeclaredDependency(declaredDependency);
                } else if (ruleDeclaration.getType() == UserRuleDeclaration.RuleType.REPLACED) {
                    // user declared replacement of a required mod (ignore required)
                    ModDeclaredDependency declaredDependency = new ModDeclaredDependency();
                    declaredDependency.setMod(ruleDeclaration.getModId());
                    declaredDependency.setTargetMod(ruleDeclaration.getTargetModId());
                    declaredDependency.setDependencyType(DependencyType.REPLACED);
                    declaredDependency.getSources().add(DeclarationSource.USER);
                    mod.addDeclaredDependency(declaredDependency);
                } else if (ruleDeclaration.getType() == UserRuleDeclaration.RuleType.INCOMPATIBLE) {
                    // user declared incompatible mod
                    ModDeclaredDependency declaredDependency = new ModDeclaredDependency();
                    declaredDependency.setMod(ruleDeclaration.getModId());
                    declaredDependency.setTargetMod(ruleDeclaration.getTargetModId());
                    declaredDependency.setDependencyType(DependencyType.INCOMPATIBLE);
                    declaredDependency.getSources().add(DeclarationSource.USER);
                    mod.addDeclaredDependency(declaredDependency);
                }
            }
        }
    }

    void calculateModsStatuses() {
        Map<String, Integer> conflictSteamIds = new HashMap<>();
        for (Mod mod : allMods.values()) {
            if (mod.getSteamMod().getSteamModId() != null) {
                int counter = conflictSteamIds.getOrDefault(mod.getSteamMod().getSteamModId(), 0);
                conflictSteamIds.put(mod.getSteamMod().getSteamModId(), counter + 1);
            }
        }

        for (Mod mod : allMods.values()) {
            recalculateModStatus(mod, conflictSteamIds);
        }
    }

    void recalculateModStatus(Mod mod, Map<String, Integer> conflictSteamIds) {
        Set<ModStatus> modStatuses = mod.getStatuses();

        if (!mod.isActive()) {
            modStatuses.add(ModStatus.INACTIVE);
        }
        if (!mod.isExist()) {
            modStatuses.add(ModStatus.DELETED);
        }
        if (!mod.getCycleMods().isEmpty()) {
            modStatuses.add(ModStatus.CYCLIC_DEPENDENCY);
        }

        if (mod.isNewMod()) {
            modStatuses.add(ModStatus.NEW);
        }

        for (ModDependency modDependency : mod.getDependencies()) {
            if (!Objects.equals(modDependency.getMod(), mod.getId()) || !modDependency.isActive()) {
                continue;
            }

            Mod targetMod = allMods.get(modDependency.getTargetMod());
            if ((modDependency.getDependencyType() == DependencyType.REQUIRED || modDependency.getDependencyType() == DependencyType.REQUIRED_REPLACEMENT)
                    && (targetMod == null || !isModOrReplacementModActive(targetMod.getId()))) {
                modStatuses.add(ModStatus.REQUIRE_DEPENDENCY);
            } else if (modDependency.getDependencyType() == DependencyType.INCOMPATIBLE
                    && targetMod != null && targetMod.isActive()) {
                modStatuses.add(ModStatus.INCOMPATIBLE_DEPENDENCY);
            }
        }

        if (!hasAllNotIgnoredSteamRequiredModsInfos(mod)) {
            modStatuses.add(ModStatus.MISSING_REQUIRED_STEAM_MOD);
        }

        if (conflictSteamIds.getOrDefault(mod.getSteamMod().getSteamModId(), 0) > 1) {
            modStatuses.add(ModStatus.STEAM_ID_DUPLICATE);
        }

        if (modStatuses.isEmpty()) {
            modStatuses.add(ModStatus.OK);
        }
    }

    private boolean hasAllNotIgnoredSteamRequiredModsInfos(Mod mod) {
        SteamMod steamMod = mod.getSteamMod();
        for (var requiredSteamMod : steamMod.getRequiredSteamMods()) {
            Mod requiredMod = steamModIdToModMap.get(requiredSteamMod.getSteamModId());
            if (requiredMod == null) {
                if (!mod.getIgnoredDependenciesKeys().contains(mod.toIgnoredDependencyKey(
                        mod.getId(), DependencyType.REQUIRED, null, requiredSteamMod.getSteamModId()))) {
                    return false;
                }
            }
        }
        return true;
    }

    LinkedHashMap<String, Mod> sortModsForLoadOrder(Collection<Mod> inputMods) {
        List<Mod> mods = new ArrayList<>(inputMods);
        mods.sort(Comparator.comparing(mod -> mod.getId().toLowerCase()));

        //Set<String> ignoreRequiredModIds =;// mods.stream().map(v -> ).collect(Collectors.toSet());

        // first use only declared dependencies directly in mod without meta mods enhanced dependencies
        TreeMap<HighlanderRunPriorityGroup, List<Mod>> groupsMap = new TreeMap<>();
        for (Mod mod : mods) {
            HighlanderModConfig config = mod.getHighlanderModsConfig().getModConfigs().get(mod.getId());
            HighlanderRunPriorityGroup runPriorityGroup = config == null
                    ? HighlanderRunPriorityGroup.RUN_STANDARD
                    : config.getRunPriorityGroup();
            groupsMap.computeIfAbsent(runPriorityGroup, k -> new ArrayList<>()).add(mod);
        }

        for (Map.Entry<HighlanderRunPriorityGroup, List<Mod>> entry : groupsMap.entrySet()) {
            List<Mod> rootSortedMods = sortModsByDeclaredHighlanderConfigOnly(toLinkedHashMap(entry.getValue()));
            entry.setValue(rootSortedMods);
        }

        mods = groupsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
//        for (int i = 0; i < mods.size(); i++) {
//            Mod mod = mods.get(i);
//            for (ModDeclaredDependency declaredDependency : mod.getDeclaredDependencies()) {
//                if (declaredDependency.getTargetMod() == null || declaredDependency.getDependencyType() != DependencyType.REQUIRED) {
//                    continue;
//                }
//                appendLoadAfterRequiredMods(mod, declaredDependency.getTargetMod(), mod.getId(),
//                        requiredModIgnoredByAlternativeModsMap);
//            }
//
//            for (int j = i; j < mods.size(); j++) {
//                Mod laterMod = mods.get(j);
//                boolean isCurrentMod = i == j;
//                HighlanderModConfig modConfig = laterMod.getHighlanderModsConfig().getModConfigs().get(mod.getId());
//                if (modConfig != null) { // later mod may override previous mod
//                    if (modConfig.getRunPriorityGroup() != null) {
//                        ModLoadOrderGroupDeclaration loadOrderDeclaration = new ModLoadOrderGroupDeclaration();
//                        loadOrderDeclaration.setDeclaredInMod(laterMod.getId());
//                        loadOrderDeclaration.setModLoadOrderGroup(modConfig.getRunPriorityGroup());
//                        loadOrderDeclaration.setTargetMod(mod.getId());
//                        mod.addLoadOrderGroupDeclaration(loadOrderDeclaration);
//                    }
//
//                    if (!isCurrentMod) {
//                        for (String requiredMod : modConfig.getRequiredMods()) {
//                            ModLoadOrderDeclaration loadOrderDeclaration = new ModLoadOrderDeclaration();
//                            loadOrderDeclaration.setMod(mod.getId());
//                            loadOrderDeclaration.setDeclaredInMod(laterMod.getId());
//                            loadOrderDeclaration.setModLoadOrder(ModLoadOrder.LOAD_AFTER_REQUIRED);
//                            loadOrderDeclaration.setTargetMod(requiredMod);
//                            mod.addLoadOrderDeclaration(loadOrderDeclaration);
//
//                            LinkedHashSet<String> requiredModIgnoredByMods = requiredModIgnoredByAlternativeModsMap.getOrDefault(requiredMod, new LinkedHashSet<>());
//                            for (String requiredModIgnoredByMod : requiredModIgnoredByMods) {
//                                if (loadOrderDeclaration.getOverriddenByMod() == null) {
//                                    loadOrderDeclaration.setOverriddenByMod(requiredModIgnoredByMod);
//                                }
//                                ModLoadOrderDeclaration loadOrder2 = new ModLoadOrderDeclaration();
//                                loadOrder2.setMod(mod.getId());
//                                loadOrder2.setDeclaredInMod(laterMod.getId());
//                                loadOrder2.setModLoadOrder(ModLoadOrder.LOAD_AFTER_REQUIRED);
//                                loadOrder2.setTargetMod(requiredModIgnoredByMod);
//                                mod.addLoadOrderDeclaration(loadOrder2);
//                            }
//                        }
//                    }
//                    for (RunOrderDeclaration runOrder : modConfig.getRunOrderDeclarations()) {
//                        ModLoadOrderDeclaration loadOrderDeclaration = new ModLoadOrderDeclaration();
//                        loadOrderDeclaration.setMod(mod.getId());
//                        loadOrderDeclaration.setDeclaredInMod(laterMod.getId());
//                        loadOrderDeclaration.setModLoadOrder(runOrder.getModLoadOrder());
//                        loadOrderDeclaration.setTargetMod(runOrder.getTargetMod());
//                        mod.addLoadOrderDeclaration(loadOrderDeclaration);
//                    }
//                }
//            }
//        }
        // todo prepareFinalDependenciesAndLoadOrders remove above?
        groupsMap = new TreeMap<>();
        for (Mod mod : mods) {
            HighlanderModConfig config = mod.getHighlanderModsConfig().getModConfigs().get(mod.getId());
            HighlanderRunPriorityGroup runPriorityGroup = config == null
                    ? HighlanderRunPriorityGroup.RUN_STANDARD
                    : config.getRunPriorityGroup();
            groupsMap.computeIfAbsent(runPriorityGroup, k -> new ArrayList<>()).add(mod);
        }

        // sort by using isolated RunPriorityGroups
        // todo delete
        LinkedHashMap<String, Mod> modsMap = toLinkedHashMap(mods);
        prepareFinalDependenciesAndLoadOrders(modsMap);
        for (Map.Entry<HighlanderRunPriorityGroup, List<Mod>> entry : groupsMap.entrySet()) {
            List<Mod> preSortedMods = sortModsAfterPreSort(toLinkedHashMap(entry.getValue()), false);
            entry.setValue(preSortedMods);
        }

        mods = groupsMap.values().stream().flatMap(Collection::stream).toList();
        // now sort by using only before/after/required/ignoreRequired and ignore groups on already sorted by groups
        modsMap = toLinkedHashMap(mods);
        prepareFinalDependenciesAndLoadOrders(modsMap);

        mods = sortModsAfterPreSort(toLinkedHashMap(mods), true);

        List<Mod> inactiveMods = new ArrayList<>();
        LinkedHashMap<String, Mod> result = new LinkedHashMap<>();
        int activeModCounter = 1;
        for (Mod mod : mods) {
            if (mod.isActive()) {
                mod.setLoadOrder(activeModCounter++);
                result.put(mod.getId(), mod);
            } else {
                inactiveMods.add(mod);
                mod.setLoadOrder(Mod.MOD_ORDER_DISABLED);
            }
        }
        inactiveMods.sort(Comparator.comparing(mod -> mod.getId().toLowerCase()));
        for (Mod inactiveMod : inactiveMods) {
            result.put(inactiveMod.getId(), inactiveMod);
        }
        return result;
    }

    List<Mod> resolveRequiredSteamMods(SteamMod steamMod, LinkedHashMap<String, Mod> mods) {
        ArrayList<Mod> result = new ArrayList<>(steamMod.getRequiredSteamMods().size());
        for (SteamRequiredMod steamRequiredMod : steamMod.getRequiredSteamMods()) {
            Mod requiredMod = steamModIdToModMap.get(steamRequiredMod.getSteamModId());
            if (requiredMod != null) {
                result.add(requiredMod);
            }
        }
        return result;
    }

    void prepareFinalDependenciesAndLoadOrders(LinkedHashMap<String, Mod> mods) {
        // 1 reset
        for (Mod mod : mods.values()) {
            mod.getDependencies().clear();
            mod.getLoadOrders().clear();
        }

        // 2 prepare
        for (Mod mod : mods.values()) {
            prepareFinalDependenciesAndLoadOrders(mod, mods);
        }

        // 3 fill incoming dependencies from other mods
        fillModsFieldsAfterLoadOrderSort(mods);
    }

    void fillModsFieldsAfterLoadOrderSort(LinkedHashMap<String, Mod> modsMap) {

        // fill incoming load order rules
        for (Mod mod : modsMap.values()) {
            for (ModLoadOrderDeclaration orderDeclaration : mod.getLoadOrders()) {
                if (!orderDeclaration.getTargetMod().equals(mod.getId())) {
                    Mod targetMod = modsMap.get(orderDeclaration.getTargetMod());
                    if (targetMod != null) {
                        targetMod.addIncomingLoadOrderDeclaration(orderDeclaration);
                    }
                }
                // todo check
//                if (!orderDeclaration.getMod().equals(mod.getId())) { // never happen?
//                    Mod targetMod = modsMap.get(orderDeclaration.getMod());
//                    if (targetMod != null) {
//                        if (!targetMod.getLoadOrders().contains(orderDeclaration)) {
//                            targetMod.getLoadOrders().add(orderDeclaration);
//                        }
//                    }
//                }
            }
        }

        // fill incoming dependencies
        for (Mod mod : modsMap.values()) {
            for (ModDependency modDependency : mod.getDependencies()) {
                if (!modDependency.getTargetMod().equals(mod.getId())) {
                    Mod targetMod = modsMap.get(modDependency.getTargetMod());
                    if (targetMod != null) {
                        targetMod.addDependency(modDependency);
                    }
                }
//                if (!modDependency.getMod().equals(mod.getId())) { // never happen?
//                    Mod targetMod = modsMap.get(modDependency.getMod());
//                    if (targetMod != null) {
//                        if (!targetMod.getDependencies().contains(modDependency)) {
//                            targetMod.addDependency(modDependency);
//                        }
//                    }
//                }
            }
        }
    }

    void prepareFinalDependenciesAndLoadOrders(Mod mod,
                                               LinkedHashMap<String, Mod> mods) {

        // 1 fill steam dependencies as lowest priority
        for (Mod requiredSteamMod : resolveRequiredSteamMods(mod.getSteamMod(), mods)) {
            fillRequiredModDependencyAndLoadOrder(mod, requiredSteamMod.getId(), mod.getId(), DeclarationSource.STEAM);
        }

        // 2 Fill highlander dependencies and run orders
        for (Mod anotherMod : mods.values()) { // fill by current order after highlander pre sorting
            boolean isCurrentMod = mod == anotherMod;
            HighlanderModConfig modConfig = anotherMod.getHighlanderModsConfig().getModConfigs().get(mod.getId());
            if (modConfig == null) {
                continue;
            }
            if (modConfig.getRunPriorityGroup() != null) {
                ModHighlanderGroupLoadOrder groupLoadOrder = new ModHighlanderGroupLoadOrder();
                groupLoadOrder.setMod(mod.getId());
                groupLoadOrder.setPriorityGroup(modConfig.getRunPriorityGroup());
                groupLoadOrder.setDeclaredInMod(anotherMod.getId());
                groupLoadOrder.setActive(mod.isActive() && anotherMod.isActive()); // both must be active for group
                mod.addHighlanderGroupLoadOrder(groupLoadOrder);
            }

            for (String requiredMod : modConfig.getRequiredMods()) {
                fillRequiredModDependencyAndLoadOrder(mod, requiredMod, anotherMod.getId(), DeclarationSource.HIGHLANDER);
            }

            for (String replacedMod : modConfig.getIgnoreRequiredMods()) {
                ModLoadOrderDeclaration loadOrder = new ModLoadOrderDeclaration();
                loadOrder.setMod(mod.getId());
                loadOrder.setModLoadOrder(ModLoadOrder.AFTER_REQUIRED_REPLACEMENT);
                loadOrder.setTargetMod(replacedMod);
                loadOrder.setDeclaredInMod(anotherMod.getId());
                loadOrder.getSources().add(DeclarationSource.HIGHLANDER);
                loadOrder.setActive(mod.isActive() && isModActive(replacedMod));
                loadOrder.setHasError(false);
                mod.addLoadOrder(loadOrder);
            }

            for (String incompatibleMod : modConfig.getIncompatibleMods()) {
                String ignoredByMod = findModIgnoringDeclaredDependency(incompatibleMod, DependencyType.INCOMPATIBLE);
                boolean ignored = ignoredByMod != null;
                ModDependency modDependency = new ModDependency();
                modDependency.setMod(mod.getId());
                modDependency.setDeclaredInMod(anotherMod.getId());
                modDependency.setDependencyType(DependencyType.INCOMPATIBLE);
                modDependency.setTargetMod(incompatibleMod);
                modDependency.getSources().add(DeclarationSource.HIGHLANDER);
                modDependency.setIgnored(ignored);
                modDependency.setActive(mod.isActive() && isModActive(incompatibleMod) && !ignored);
                modDependency.setHasError(modDependency.isActive() && isModActive(incompatibleMod) && !ignored);
                modDependency.setOverriddenByMod(ignoredByMod);

                mod.addDependency(modDependency);
            }

            for (RunOrderDeclaration runOrderDeclaration : modConfig.getRunOrderDeclarations()) {
                ModLoadOrderDeclaration loadOrder = new ModLoadOrderDeclaration();
                loadOrder.setMod(mod.getId());
                loadOrder.setModLoadOrder(runOrderDeclaration.getModLoadOrder());
                loadOrder.setTargetMod(runOrderDeclaration.getTargetMod());
                loadOrder.setDeclaredInMod(anotherMod.getId());
                loadOrder.getSources().add(DeclarationSource.HIGHLANDER);
                loadOrder.setActive(mod.isActive() && anotherMod.isActive());
                mod.addLoadOrder(loadOrder);

                replacedByModIdToModMap.getOrDefault(runOrderDeclaration.getTargetMod(), List.of())
                        .forEach(replacedByMod -> {
                            ModLoadOrderDeclaration loadOrder2 = new ModLoadOrderDeclaration();
                            loadOrder2.setMod(mod.getId());
                            loadOrder2.setModLoadOrder(runOrderDeclaration.getModLoadOrder());
                            loadOrder2.setTargetMod(replacedByMod.getReplacedByMod());
                            loadOrder2.setDeclaredInMod(replacedByMod.getDeclaredInMod());
                            loadOrder2.getSources().add(DeclarationSource.HIGHLANDER);
                            loadOrder2.setActive(mod.isActive() && isModActive(replacedByMod.getReplacedByMod()));
                            mod.addLoadOrder(loadOrder2);
                        });
            }
        }

        // 3 Fill User defined dependencies and run orders as final priority
        List<UserRuleDeclaration> userModRules = userRulesByMod.getOrDefault(mod.getId(), List.of());
        for (UserRuleDeclaration userModRule : userModRules) {
            if (userModRule.getType() == UserRuleDeclaration.RuleType.REQUIRED) {
                fillRequiredModDependencyAndLoadOrder(mod, userModRule.getTargetModId(), mod.getId(), DeclarationSource.USER);
            } else if (userModRule.getType() == UserRuleDeclaration.RuleType.REPLACED) {
                // user declared that this mod replaces a required mod -> create load order entry similar to highlander ignoreRequired
                ModLoadOrderDeclaration loadOrder = new ModLoadOrderDeclaration();
                loadOrder.setMod(mod.getId());
                loadOrder.setModLoadOrder(ModLoadOrder.AFTER_REQUIRED_REPLACEMENT);
                loadOrder.setTargetMod(userModRule.getTargetModId());
                loadOrder.setDeclaredInMod(mod.getId());
                loadOrder.getSources().add(DeclarationSource.USER);
                loadOrder.setActive(mod.isActive() && isModActive(userModRule.getTargetModId()));
                loadOrder.setHasError(false);
                mod.addLoadOrder(loadOrder);
            } else if (userModRule.getType() == UserRuleDeclaration.RuleType.INCOMPATIBLE) {
                String ignoredByMod = findModIgnoringDeclaredDependency(userModRule.getTargetModId(), DependencyType.INCOMPATIBLE);
                ModDependency modDependency = new ModDependency();
                modDependency.setMod(mod.getId());
                modDependency.setDeclaredInMod(mod.getId());
                modDependency.setDependencyType(DependencyType.INCOMPATIBLE);
                modDependency.setTargetMod(userModRule.getTargetModId());
                modDependency.getSources().add(DeclarationSource.USER);
                modDependency.setIgnored(ignoredByMod != null);
                modDependency.setActive(mod.isActive() && isModActive(userModRule.getTargetModId()) && ignoredByMod == null);
                modDependency.setHasError(modDependency.isActive() && isModActive(userModRule.getTargetModId()) && ignoredByMod == null);
                if (ignoredByMod != null) {
                    modDependency.setOverriddenByMod(ignoredByMod);
                }
                mod.addDependency(modDependency);
            } else {
                ModLoadOrder loadOrderType = userModRule.getType().toLoadOrder();
                if (loadOrderType != null) {
                    ModLoadOrderDeclaration loadOrder = new ModLoadOrderDeclaration();
                    loadOrder.setMod(mod.getId());
                    loadOrder.setModLoadOrder(loadOrderType);
                    loadOrder.setTargetMod(userModRule.getTargetModId());
                    loadOrder.setDeclaredInMod(mod.getId());
                    loadOrder.getSources().add(DeclarationSource.USER);
                    loadOrder.setActive(mod.isActive() && isModActive(userModRule.getTargetModId()));
                    loadOrder.setHasError(false);
                    mod.addLoadOrder(loadOrder);
                }
            }
        }
    }

    private void fillRequiredModDependencyAndLoadOrder(Mod mod,
                                                       String requiredMod,
                                                       String requiredModDeclaredInMod,
                                                       DeclarationSource source) {
        // check if any mod has this required mod's declaration marked as ignored
        String ignoredByModId = findModIgnoringRequiredMod(requiredMod);
        boolean declarationIgnored = ignoredByModId != null;

        ModDependency modDependency = new ModDependency();
        modDependency.setMod(mod.getId());
        modDependency.setDeclaredInMod(requiredModDeclaredInMod);
        modDependency.setDependencyType(DependencyType.REQUIRED);
        modDependency.setTargetMod(requiredMod);
        modDependency.getSources().add(source);
        modDependency.setIgnored(declarationIgnored);
        modDependency.setActive(mod.isActive() && !declarationIgnored);
        modDependency.setHasError(!isModOrReplacementModActive(requiredMod) && !declarationIgnored);
        if (ignoredByModId != null) {
            modDependency.setOverriddenByMod(ignoredByModId);
        }
        mod.addDependency(modDependency);

        ModLoadOrderDeclaration loadOrder = new ModLoadOrderDeclaration();
        loadOrder.setMod(mod.getId());
        loadOrder.setModLoadOrder(ModLoadOrder.AFTER_REQUIRED);
        loadOrder.setTargetMod(requiredMod);
        loadOrder.setDeclaredInMod(requiredModDeclaredInMod);
        loadOrder.getSources().add(source);
        loadOrder.setActive(mod.isActive() && !declarationIgnored);
        loadOrder.setHasError(!isModOrReplacementModActive(requiredMod) && !declarationIgnored);
        if (ignoredByModId != null) {
            loadOrder.setOverriddenByMod(ignoredByModId);
        }
        mod.addLoadOrder(loadOrder);

        List<ReplacedByMod> requiredModIgnoredByMods = replacedByModIdToModMap.getOrDefault(requiredMod, List.of());
        for (ReplacedByMod requiredModReplacedByMod : requiredModIgnoredByMods) {
            boolean replacedByModActive = isModActive(requiredModReplacedByMod.getReplacedByMod());

            ModDependency modDependency2 = new ModDependency();
            modDependency2.setMod(mod.getId());
            modDependency2.setDeclaredInMod(requiredModReplacedByMod.getDeclaredInMod());
            modDependency2.setDependencyType(DependencyType.REQUIRED_REPLACEMENT);
            modDependency2.setTargetMod(requiredModReplacedByMod.getReplacedByMod());
            modDependency2.getSources().add(DeclarationSource.HIGHLANDER);
            modDependency2.setActive(mod.isActive() && replacedByModActive);
            modDependency2.setHasError(!isModOrReplacementModActive(requiredMod));
            mod.addDependency(modDependency2);

            if (modDependency.getOverriddenByMod() == null && modDependency2.isActive() && !isModActive(requiredMod)) {
                modDependency.setOverriddenByMod(modDependency2.getTargetMod());
                loadOrder.setOverriddenByMod(modDependency2.getTargetMod());
            }

            ModLoadOrderDeclaration loadOrder2 = new ModLoadOrderDeclaration();
            loadOrder2.setMod(mod.getId());
            loadOrder2.setDeclaredInMod(requiredModReplacedByMod.getDeclaredInMod());
            loadOrder2.setModLoadOrder(ModLoadOrder.AFTER_REQUIRED_REPLACEMENT);
            loadOrder2.setTargetMod(requiredModReplacedByMod.getReplacedByMod());
            loadOrder2.setActive(mod.isActive() && replacedByModActive);
            loadOrder2.setHasError(modDependency2.isActive() && !isModOrReplacementModActive(requiredMod));
            loadOrder2.getSources().add(DeclarationSource.HIGHLANDER);
            mod.addLoadOrder(loadOrder2);
        }
    }

    /**
     * Sort by before/after/required/ignoreRequired without priority group at first step.
     * @param mods
     * @return
     */
    List<Mod> sortModsByDeclaredHighlanderConfigOnly(LinkedHashMap<String, Mod> mods) {
        LinkedHashMap<String, SortItem<String>> sortItemMap = new LinkedHashMap<>(mods.size());
        for (Mod mod : mods.values()) {
            SortItem<String> sortItem = new SortItem<>();
            sortItem.setValue(mod.getId());
            HighlanderModConfig highlanderConfig = mod.getHighlanderModsConfig().getModConfigs().get(mod.getId());

            if (highlanderConfig != null) {
                // todo
                for (RunOrderDeclaration runOrderDeclaration : highlanderConfig.getRunOrderDeclarations()) {
                    if (runOrderDeclaration.getTargetMod() != null) {
                        if (runOrderDeclaration.getModLoadOrder().isLoadBefore()) {
                            sortItem.getBeforeValues().add(runOrderDeclaration.getTargetMod());
                        } else if (runOrderDeclaration.getModLoadOrder().isLoadAfter()) {
                            sortItem.getAfterValues().add(runOrderDeclaration.getTargetMod());
                        }
                    }
                }
            }
            List<ModDeclaredDependency> declaredDependencies = mod.getDeclaredDependencies();
            for (ModDeclaredDependency dependency : declaredDependencies) {
                String requiredModId = dependency.getTargetMod();
                if (requiredModId == null || dependency.getDependencyType() != DependencyType.REQUIRED) {
                    continue;
                }
                if (!sortItem.getBeforeValues().contains(requiredModId)
                        && !sortItem.getAfterValues().contains(requiredModId)) {
                    // assume required mod should be loaded before
                    sortItem.getBeforeValues().add(requiredModId);
                    List<ReplacedByMod> requiredModIgnoredByMods = replacedByModIdToModMap.getOrDefault(requiredModId, List.of());
                    for (ReplacedByMod requiredModIgnoredByMod : requiredModIgnoredByMods) {
                        String replacedBy = requiredModIgnoredByMod.getReplacedByMod();
                        if (!sortItem.getBeforeValues().contains(replacedBy)
                                && !sortItem.getAfterValues().contains(replacedBy)) {
                            sortItem.getBeforeValues().add(replacedBy);
                        }
                    }
                }
            }
            sortItemMap.put(mod.getId(), sortItem);
        }
        SortResult<String> sortResult = SortUtils.sort(sortItemMap);
        if (!sortResult.getCycles().isEmpty()) {
            for (List<String> cycleModsGroup : sortResult.getCycles()) {
                log.warn("Found mods cycle group: {}", cycleModsGroup);
            }
        }

        return sortResult.getSorted().stream().map(mods::get).collect(Collectors.toList());
    }

    List<Mod> sortModsAfterPreSort(LinkedHashMap<String, Mod> mods, boolean setCycleModsToMods) {
        LinkedHashMap<String, SortItem<String>> sortItemMap = new LinkedHashMap<>(mods.size());
        List<Mod> inactiveMods = new ArrayList<>(mods.size());
        List<Mod> activeMods = new ArrayList<>(mods.size());
        for (Mod mod : mods.values()) {
            if (!mod.isActive()) {
                inactiveMods.add(mod);
                continue;
            }
            activeMods.add(mod);
            SortItem<String> sortItem = new SortItem<>();
            sortItem.setValue(mod.getId());
            for (ModLoadOrderDeclaration loadOrderDeclaration : mod.getLoadOrders()) {
                if (loadOrderDeclaration.isActive() && loadOrderDeclaration.getMod().equals(mod.getId())) {
                    if (Objects.equals(loadOrderDeclaration.getMod(), loadOrderDeclaration.getTargetMod())) {
                        loadOrderDeclaration.setHasError(true);
                        // mod.getCycleMods().add(List.of(mod)); lets just ignore as it doesn't make sense
                        log.warn("Mod {} self cycle reference detected", mod.getId());
                        continue;
                    }
                    if (loadOrderDeclaration.getModLoadOrder().isLoadBefore()) {
                        sortItem.getBeforeValues().add(loadOrderDeclaration.getTargetMod());
                    } else if (loadOrderDeclaration.getModLoadOrder().isLoadAfter()) {
                        sortItem.getAfterValues().add(loadOrderDeclaration.getTargetMod());
                    }
                }
            }

            sortItemMap.put(mod.getId(), sortItem);
        }
        SortResult<String> sortResult = SortUtils.sort(sortItemMap);
        if (!sortResult.getCycles().isEmpty()) {
            for (List<String> cycleModsGroup : sortResult.getCycles()) {
                log.warn("Found mods cycle group: {}", cycleModsGroup);
                if (setCycleModsToMods) {
                    Mod.CycleGroup cycleGroup = new Mod.CycleGroup();
                    for (String cycleMod : cycleModsGroup) {
                        SortItem<String> modSortItemInput = sortItemMap.get(cycleMod);
                        SortItem<String> modSortItemRelevantToCycle = new SortItem<>();
                        modSortItemRelevantToCycle.setValue(cycleMod);
                        for (String beforeMod : modSortItemInput.getBeforeValues()) {
                            if (cycleModsGroup.contains(beforeMod)) {
                                modSortItemRelevantToCycle.getBeforeValues().add(beforeMod);
                            }
                        }
                        for (String afterMod : modSortItemInput.getAfterValues()) {
                            if (cycleModsGroup.contains(afterMod)) {
                                modSortItemRelevantToCycle.getAfterValues().add(afterMod);
                            }
                        }
                        cycleGroup.getMods().add(modSortItemRelevantToCycle);

                        Mod mod = mods.get(cycleMod);
                        mod.getCycleMods().add(cycleGroup);
                    }
                }
            }
        }

        List<Mod> result = new ArrayList<>(mods.size());
        sortResult.getSorted().stream().map(mods::get).forEach(result::add);
        result.addAll(inactiveMods);

        HashSet<String> sortedMods = new HashSet<>(sortResult.getSorted());
        for (Mod mod : activeMods) {
            if (!sortedMods.contains(mod.getId())) {
                log.error("Failed to sort mod: {}", mod.getId());
                result.add(mod);
                mod.getStatuses().add(ModStatus.LOAD_ORDER_ERROR);
            }
        }

        return result;
    }

    void setModsMapAfterParsingFromDirs(List<Mod> mods) {
        LinkedHashMap<String, Mod> map = new LinkedHashMap<>();
        for (Mod mod : mods) {
            Mod primaryMod = map.computeIfAbsent(mod.getId(), (k) -> mod);
            if (mod != primaryMod) { // duplicate detected
                primaryMod.getDuplicateMods().add(mod);
                log.info("Detected duplicate mod with ID {} in dir1 {} and dir2 {}",
                        mod.getId(), primaryMod.getDirectory(), mod.getDirectory());
            }
        }

        List<ModRecord> modDbRecords = modRepository.findAll();
        for (ModRecord modDbRecord : modDbRecords) {
            Mod mod = map.get(modDbRecord.getId());
            if (mod == null) {
                mod = new Mod();
                mod.setId(modDbRecord.getId());
                mod.setTitle(modDbRecord.getTitle());
                mod.setDirectory(new File(modDbRecord.getDirectory()));
                map.put(modDbRecord.getId(), mod);
            } else {
                if (new File(modDbRecord.getDirectory()).equals(mod.getDirectory())) {
                    mod.setSize(modDbRecord.getSizeBytes());
                }
            }
            try {
                mod.setIgnoredDependenciesKeys(jsonConverter.parse(
                        modDbRecord.getIgnoredDependenciesKeys(), new TypeReference<>() {}));
            } catch (Exception e) {
                log.error("Failed to parse ignored dependencies from DB for Mod {}", mod.getId());
            }
            mod.setSteamDbModId(modDbRecord.getSteamModId());
            if (mod.getSteamDbModId() != null) {
                mod.getSteamMod().setSteamModId(modDbRecord.getSteamModId());
            }
            mod.setActive(modDbRecord.getIsActive());
            mod.setNewMod(modDbRecord.getIsNew());
        }

        allMods = map;
        recalculateModDependencies();
    }

    void fillSteamMods(Map<String, Mod> mods) {
        long start = System.currentTimeMillis();
        Map<String, SteamMod> steamMods = steamService.findAllSteamMods();
        HashMap<String, Mod> steamModIdToModMap = new HashMap<>(steamMods.size());
        // build map from steamModId to mod
        for (Mod mod : mods.values()) {
            if (mod.getSteamMod().getSteamModId() != null) {
                SteamMod steamMod = steamMods.get(mod.getSteamMod().getSteamModId());
                if (steamMod != null) {
                    mod.setSteamMod(steamMod);
                }
                steamModIdToModMap.putIfAbsent(mod.getSteamMod().getSteamModId(), mod);
            }
        }
        log.info("Filled steam mods info to {} mods in {}ms", steamModIdToModMap.size(), System.currentTimeMillis() - start);
        this.steamModIdToModMap = steamModIdToModMap;
    }

    public void reloadModsFromDirs() {
        List<Mod> parsedMods = parseModsFromDirs();
        setModsMapAfterParsingFromDirs(parsedMods);
    }

    List<Mod> parseModsFromDirs() {
        List<Mod> parsedMods = new ArrayList<>();
        for (String modsDir : dbProps.modDirsForSearch.get()) {
            boolean isSteamWorkshopModDir = XComUtils.isXcomWorkshopFolder(new File(modsDir));
            List<File> modsFiles = reloadModsFromDir(new File(modsDir));
            parsedMods.addAll(parseMods(isSteamWorkshopModDir, modsFiles));
        }
        return parsedMods;
    }

    public List<File> reloadModsFromDir(File modDir) {
        long before = System.currentTimeMillis();
        log.info("Searching for mods in directory {}", modDir.getAbsolutePath());
        List<File> modsFiles = XComModFinderUtils.findModFiles(modDir, dbProps.modDirMaxSubDirsForSearch.get());
        log.info("Found {} mods in directory {} in {}ms",
                modsFiles.size(), modDir.getAbsolutePath(), System.currentTimeMillis() - before);
        return modsFiles;
    }

    public List<Mod> parseMods(boolean isSteamWorkshopMod, Collection<File> modDirs) {
        return modDirs.stream().map(v -> parseMod(isSteamWorkshopMod, v))
                .filter(Objects::nonNull).toList();
    }

    public Mod parseMod(boolean isSteamWorkshopMod, File modFile) {
        try {
            String xcomModFileContent = Files.readString(modFile.toPath());
            Properties props = new Properties();
            props.load(new ByteArrayInputStream(xcomModFileContent.getBytes(StandardCharsets.UTF_8)));
            Mod mod = new Mod();
            mod.setId(modFile.getName().substring(0, modFile.getName().length() - XComModFinderUtils.MOD_FILE_EXTENSION.length()));
            mod.setXcomModFileContent(xcomModFileContent);
            mod.setPublishedFileId(parseModProp("publishedFileId", props));
            mod.setTitle(parseModProp("Title", props));
            //mod.setDescription(parseModProp("Description", props));
            mod.setRequiresXPACK("true".equalsIgnoreCase(parseModProp("RequiresXPACK", props)));
            //mod.setTags(parseModProp("Tags", props));
            //mod.setSize(FileUtils.calculateDirectorySize(modFile.getParentFile().toPath()));
            mod.setDirectory(modFile.getParentFile().getAbsoluteFile());
            if (isSteamWorkshopMod) {
                try {
                    mod.getSteamMod().setSteamModId(modFile.getParentFile().getName());
                    mod.setSteamModIdByDirName(mod.getSteamMod().getSteamModId());
                } catch (Exception e) {
                    log.error("Failed to parse steam mod ID from dir name: {}", modFile.getParentFile().getName(), e);
                }
            }
            File xcomGameIniFile = new File(modFile.getParentFile().getAbsolutePath() + "/Config/XComGame.ini");
            mod.setHighlanderModsConfig(CommunityHighlanderUtils.parseHighlanderXComGameIni(mod.getId(), xcomGameIniFile));
            return mod;
        } catch (Exception e) {
            log.error("Error while reading mod file {}", modFile.getAbsolutePath(), e);
            return null;
        }
    }

    private String parseModProp(String key, Properties props) {
        String value = props.getProperty(key);
        if (value != null) {
            return value.strip();
        }
        return value;
    }

    private static HashMap<String, Mod> toHashMap(List<Mod> mods) {
        HashMap<String, Mod> modMap = new HashMap<>(mods.size());
        for (Mod mod : mods) {
            modMap.put(mod.getId(), mod);
        }
        return modMap;
    }

    private static LinkedHashMap<String, Mod> toLinkedHashMap(List<Mod> mods) {
        LinkedHashMap<String, Mod> modMap = new LinkedHashMap<>(mods.size());
        for (Mod mod : mods) {
            modMap.put(mod.getId(), mod);
        }
        return modMap;
    }


    public Collection<Mod> getActiveMods() {
        return allMods.values().stream().filter(Mod::isActive).toList();
    }

    public boolean isModActive(String modId) {
        Mod mod = allMods.get(modId);
        return mod != null && mod.isActive();
    }

    public boolean isModOrReplacementModActive(String modId) {
        Mod mod = allMods.get(modId);
        if (mod != null && mod.isActive()) {
            return true;
        }
        List<ReplacedByMod> replacedByMods = replacedByModIdToModMap.get(modId);
        if (replacedByMods == null) {
            return false;
        }
        return replacedByMods.stream().anyMatch(v -> isModActive(v.getReplacedByMod()));
    }

    public boolean isModExist(String targetModId) {
        Mod mod = allMods.get(targetModId);
        return mod != null && mod.isExist();
    }

    /**
     * TODO optimize
     */
    private String findModIgnoringRequiredMod(String requiredMod) {
        if (requiredMod == null) {
            return null;
        }
        // search all mods for a REQUIRED declaration of requiredMod that is marked ignored
        for (Mod mod : allMods.values()) {
            for (ModDeclaredDependency dd : mod.getDeclaredDependencies()) {
                if (Objects.equals(dd.getTargetMod(), requiredMod)
                        && dd.getDependencyType() == DependencyType.REQUIRED
                        && dd.isIgnored()) {
                    return mod.getId();
                }
            }
        }
        return null;
    }

    private String findModIgnoringDeclaredDependency(String targetModId, DependencyType dependencyType) {
        if (targetModId == null) {
            return null;
        }
        // search all mods for a INCOMPATIBLE declaration of targetModId that is marked ignored
        for (Mod mod : allMods.values()) {
            for (ModDeclaredDependency dd : mod.getDeclaredDependencies()) {
                if (Objects.equals(dd.getTargetMod(), targetModId)
                        && dd.getDependencyType() == dependencyType
                        && dd.isIgnored()) {
                    return mod.getId();
                }
            }
        }
        return null;
    }
}
