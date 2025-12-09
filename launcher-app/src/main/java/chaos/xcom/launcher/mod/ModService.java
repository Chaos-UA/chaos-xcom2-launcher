package chaos.xcom.launcher.mod;

import chaos.db.gen.tables.records.ModRecord;
import chaos.xcom.launcher.db.property.DbProperties;
import chaos.xcom.launcher.gui.MainForm.MainForm;
import chaos.xcom.launcher.highlander.CommunityHighlanderUtils;
import chaos.xcom.launcher.highlander.dto.HighlanderModConfig;
import chaos.xcom.launcher.highlander.dto.HighlanderModConfig.RunOrderDeclaration;
import chaos.xcom.launcher.highlander.dto.HighlanderRunPriorityGroup;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.dto.Mod.ModDependency;
import chaos.xcom.launcher.mod.dto.Mod.ModLoadOrderDeclaration;
import chaos.xcom.launcher.mod.dto.Mod.ModLoadOrderGroupDeclaration;
import chaos.xcom.launcher.mod.dto.ModLoadOrder;
import chaos.xcom.launcher.steam.SteamMod;
import chaos.xcom.launcher.steam.SteamService;
import chaos.xcom.launcher.steam.SteamSyncProgress;
import chaos.xcom.launcher.util.FileUtils;
import chaos.xcom.launcher.util.SortUtils;
import chaos.xcom.launcher.util.SortUtils.SortItem;
import chaos.xcom.launcher.util.SortUtils.SortResult;
import chaos.xcom.launcher.util.XComModFinderUtils;
import chaos.xcom.launcher.util.XComUtils;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@RequiredArgsConstructor
@Slf4j
public class ModService {

    private final DbProperties dbProps;
    private final MainForm mainForm;
    private final ModRepository modRepository;
    private final SteamService steamService;

    /**
     * String is mod ID, and values are mods under this ID, multiple values in case of duplicates.
     */
   // private Map<String, List<Mod>> modsMap = new HashMap<>();

    private LinkedHashMap<String, Mod> allMods = new LinkedHashMap<>();
    private HashMap<String, Mod> steamModIdToModMap = new HashMap<>();

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
            log.info("Mod with ID {} is now {}", modId, active ? "active" : "inactive");
            saveModToDb(mod);
        }
        recalculateModDependencies();
    }

    private void saveModToDb(Mod mod) {
        ModRecord modDbRecord = toDbModRecord(mod);
        modRepository.save(modDbRecord);
        log.info("Saved mod {} to DB", mod.getId());
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
        modDbRecord.setActive(mod.isActive());
        SteamMod steamMod = mod.getSteamMod();
        if (steamMod != null) {
            modDbRecord.setSteamModId(steamMod.getSteamModId());
        }
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
    private LinkedHashMap<String, LinkedHashSet<String>> extractIgnoreRequiredMods(Collection<Mod> mods) {
        LinkedHashMap<String, LinkedHashSet<String>> result = new LinkedHashMap<>();
        for (Mod mod : mods) {
            for (HighlanderModConfig config : mod.getHighlanderModsConfig().getModConfigs().values()) {
                for (String ignoreRequiredMod : config.getIgnoreRequiredMods()) {
                    result.computeIfAbsent(ignoreRequiredMod, k -> new LinkedHashSet<>()).add(config.getMod());
                }
            }
        }
        return result;
    }

    synchronized void recalculateModDependencies() {
        // clear all dependencies state
        for (Mod mod : allMods.values()) {
            mod.clearStateForLoadOrderCalculation();
        }
        fillSteamMods(allMods);
        List<Mod> activeMods = new ArrayList<>();
        List<Mod> inactiveMods = new ArrayList<>();
        for (Mod mod : allMods.values()) {
            if (mod.isActive()) {
                activeMods.add(mod);
            } else {
                inactiveMods.add(mod);
            }
        }

        LinkedHashMap<String, Mod> loadOrderSortedMods = toLinkedHashMap(sortModsForLoadOrder(activeMods));
        inactiveMods.sort(Comparator.comparing(mod -> mod.getId().toLowerCase()));
        for (Mod inactiveMod : inactiveMods) {
            loadOrderSortedMods.put(inactiveMod.getId(), inactiveMod);
        }

        this.allMods = loadOrderSortedMods;
        recalculateModsStatuses();

        // expand duplicate mods to show in table
        ArrayList<Mod> tableMods = new ArrayList<>(loadOrderSortedMods.size());
        for (Mod mod : allMods.values()) {
            tableMods.add(mod);
            for (Mod duplicateMod : mod.getDuplicateMods()) {
                duplicateMod.setActive(false);
                duplicateMod.setLoadOrder(mod.getLoadOrder());
                duplicateMod.getStatuses().add(Mod.Status.DUPLICATE);
                duplicateMod.getStatuses().add(Mod.Status.INACTIVE);
                duplicateMod.setSteamMod(mod.getSteamMod());
                tableMods.add(duplicateMod);
            }
        }

        mainForm.updateMods(tableMods);
        if (dbProps.syncMissingSteamModsOnReload.get()) {
            this.syncMissingSteamMods();
        }
        Thread.ofVirtual().start(System::gc); // suggest GC to free memory after recalculation
    }

    void recalculateModsStatuses() {
        for (Mod mod : allMods.values()) {
            recalculateModStatus(mod);
        }
    }

    void recalculateModStatus(Mod mod) {
        TreeSet<Mod.Status> modStatuses = new TreeSet<>();

        if (!mod.isActive()) {
            modStatuses.add(Mod.Status.INACTIVE);
        }
        if (!mod.isExist()) {
            modStatuses.add(Mod.Status.DELETED);
        }
        if (!mod.getCycleMods().isEmpty()) {
            modStatuses.add(Mod.Status.CYCLIC_DEPENDENCY);
        }

        for (ModDependency modDependency : mod.getDependencies()) {
            if (!Objects.equals(modDependency.getMod(), mod.getId()) || modDependency.getOverriddenByMod() != null) {
                continue;
            }

            Mod targetMod = allMods.get(modDependency.getTargetMod());
            if (modDependency.getDependencyType() == Mod.DependencyType.REQUIRED
                    && (targetMod == null || !targetMod.isActive())) {
                modStatuses.add(Mod.Status.REQUIRE_DEPENDENCY);
                modDependency.setHasError(true);
            } else if (modDependency.getDependencyType() == Mod.DependencyType.INCOMPATIBLE
                    && targetMod != null && targetMod.isActive()) {
                modStatuses.add(Mod.Status.INCOMPATIBLE_DEPENDENCY);
                modDependency.setHasError(true);
            }
        }

        if (!hasAllSteamRequiredModsInfos(mod)) {
            modStatuses.add(Mod.Status.MISSING_REQUIRED_STEAM_MOD);
        }

        if (modStatuses.isEmpty()) {
            modStatuses.add(Mod.Status.OK);
        }
        mod.setStatuses(modStatuses);
    }

    private boolean hasAllSteamRequiredModsInfos(Mod mod) {
        SteamMod steamMod = mod.getSteamMod();
        for (var requiredSteamMod : steamMod.getRequiredSteamMods()) {
            Mod requiredMod = steamModIdToModMap.get(requiredSteamMod.getSteamModId());
            if (requiredMod == null) {
                return false;
            }
        }
        return true;
    }


    List<Mod> sortModsForLoadOrder(List<Mod> mods) {
        mods = new ArrayList<>(mods);
        mods.sort(Comparator.comparing(mod -> mod.getId().toLowerCase()));

        LinkedHashMap<String, LinkedHashSet<String>> requiredModIgnoredByAlternativeModsMap = extractIgnoreRequiredMods(mods);

        //Set<String> ignoreRequiredModIds =;// mods.stream().map(v -> ).collect(Collectors.toSet());

        // first use highlander root mods dependencies without meta mods dependencies
        TreeMap<HighlanderRunPriorityGroup, List<Mod>> groupsMap = new TreeMap<>();
        for (Mod mod : mods) {
            HighlanderModConfig config = mod.getHighlanderModsConfig().getModConfigs().get(mod.getId());
            HighlanderRunPriorityGroup runPriorityGroup = config == null
                    ? HighlanderRunPriorityGroup.RUN_STANDARD
                    : config.getRunPriorityGroup();
            groupsMap.computeIfAbsent(runPriorityGroup, k -> new ArrayList<>()).add(mod);
        }

        for (Map.Entry<HighlanderRunPriorityGroup, List<Mod>> entry : groupsMap.entrySet()) {
            List<Mod> rootSortedMods = sortModsByRootConfigOnly(toLinkedHashMap(entry.getValue()), requiredModIgnoredByAlternativeModsMap);
            entry.setValue(rootSortedMods);
        }

        // now build final load order dependencies tree based on dependencies from first step by using all dependencies
        //LinkedHashMap modsMap = toLinkedHashMap(groupsMap.values().stream().flatMap(Collection::stream));
        mods = groupsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        for (int i = 0; i < mods.size(); i++) {
            Mod mod = mods.get(i);

            // loadOrderDeclaration.set
            // mod.setLoadOrderGroup(ModLoadOrderGroup.NOT_SPECIFIED);
            // = mod.getHighlanderModsConfig().getModConfigs().get(mod.getId());

            for (int j = i; j < mods.size(); j++) {
                Mod laterMod = mods.get(j);
                boolean isCurrentMod = i == j;
                HighlanderModConfig modConfig = laterMod.getHighlanderModsConfig().getModConfigs().get(mod.getId());
                if (modConfig != null) { // later mod may override previous mod
                    if (modConfig.getRunPriorityGroup() != null) {
                        ModLoadOrderGroupDeclaration loadOrderDeclaration = new ModLoadOrderGroupDeclaration();
                        loadOrderDeclaration.setDeclaredInMod(laterMod.getId());
                        loadOrderDeclaration.setModLoadOrderGroup(modConfig.getRunPriorityGroup());
                        loadOrderDeclaration.setTargetMod(mod.getId());
                        mod.addLoadOrderGroupDeclaration(loadOrderDeclaration);
                    }
                    for (String requiredMod : modConfig.getRequiredMods()) {
                        ModLoadOrderDeclaration loadOrderDeclaration = new ModLoadOrderDeclaration();
                        loadOrderDeclaration.setMod(mod.getId());
                        loadOrderDeclaration.setDeclaredInMod(laterMod.getId());
                        loadOrderDeclaration.setModLoadOrder(ModLoadOrder.LOAD_AFTER_REQUIRED);
                        loadOrderDeclaration.setTargetMod(requiredMod);
                        mod.addLoadOrderDeclaration(loadOrderDeclaration);

                        LinkedHashSet<String> requiredModIgnoredByMods = requiredModIgnoredByAlternativeModsMap.getOrDefault(requiredMod, new LinkedHashSet<>());
                        for (String requiredModIgnoredByMod : requiredModIgnoredByMods) {
                            if (loadOrderDeclaration.getOverriddenByMod() == null) {
                                loadOrderDeclaration.setOverriddenByMod(requiredModIgnoredByMod);
                            }
                            ModLoadOrderDeclaration loadOrder2 = new ModLoadOrderDeclaration();
                            loadOrder2.setMod(mod.getId());
                            loadOrder2.setDeclaredInMod(laterMod.getId());
                            loadOrder2.setModLoadOrder(ModLoadOrder.LOAD_AFTER_REQUIRED);
                            loadOrder2.setTargetMod(requiredModIgnoredByMod);
                            mod.addLoadOrderDeclaration(loadOrder2);
                        }
                    }
                    for (RunOrderDeclaration runOrder : modConfig.getRunOrderDeclarations()) {
                        ModLoadOrderDeclaration loadOrderDeclaration = new ModLoadOrderDeclaration();
                        loadOrderDeclaration.setMod(mod.getId());
                        loadOrderDeclaration.setDeclaredInMod(laterMod.getId());
                        loadOrderDeclaration.setModLoadOrder(runOrder.getModLoadOrder());
                        loadOrderDeclaration.setTargetMod(runOrder.getTargetMod());
                        mod.addLoadOrderDeclaration(loadOrderDeclaration);
                    }
                }
            }
        }

        groupsMap = new TreeMap<>();
        for (Mod mod : mods) {
            HighlanderModConfig config = mod.getHighlanderModsConfig().getModConfigs().get(mod.getId());
            HighlanderRunPriorityGroup runPriorityGroup = config == null
                    ? HighlanderRunPriorityGroup.RUN_STANDARD
                    : config.getRunPriorityGroup();
            groupsMap.computeIfAbsent(runPriorityGroup, k -> new ArrayList<>()).add(mod);
        }

        // sort by using isolated RunPriorityGroups
        for (Map.Entry<HighlanderRunPriorityGroup, List<Mod>> entry : groupsMap.entrySet()) {
            List<Mod> rootSortedMods = sortModsAfterRootSort(toLinkedHashMap(entry.getValue()), false);
            entry.setValue(rootSortedMods);
        }

        mods = groupsMap.values().stream().flatMap(Collection::stream).toList();
        // now sort by using only before/after/required/ignoreRequired and ignore groups on already sorted by groups
        mods = sortModsAfterRootSort(toLinkedHashMap(mods), true);


        return fillModsFieldsAfterLoadOrderSort(requiredModIgnoredByAlternativeModsMap, mods);
    }

    List<Mod> fillModsFieldsAfterLoadOrderSort(Map<String, LinkedHashSet<String>> requiredModIgnoredByAlternativeModsMap,
                                               List<Mod> mods) {
        // now set load order field on final mods loaded order
        for (int i = 0; i < mods.size(); i++) {
            mods.get(i).setLoadOrder(i + 1);
        }

        // fill incoming load order rules
        HashMap<String, Mod> modsMap = toHashMap(mods);
        for (Mod mod : mods) {
            for (ModLoadOrderDeclaration orderDeclaration : mod.getLoadOrders()) {
                if (!orderDeclaration.getTargetMod().equals(mod.getId())) {
                    Mod targetMod = modsMap.get(orderDeclaration.getTargetMod());
                    if (targetMod != null) {
                        targetMod.getLoadOrders().add(orderDeclaration);
                    }
                }
                if (!orderDeclaration.getMod().equals(mod.getId())) { // never happen?
                    Mod targetMod = modsMap.get(orderDeclaration.getMod());
                    if (targetMod != null) {
                        if (!targetMod.getLoadOrders().contains(orderDeclaration)) {
                            targetMod.getLoadOrders().add(orderDeclaration);
                        }
                    }
                }
            }
        }

        // fill mod dependencies
        for (Mod declaredInMod : mods) {
            for (HighlanderModConfig modConfig : declaredInMod.getHighlanderModsConfig().getModConfigs().values()) {
                Mod mod = modsMap.get(modConfig.getMod());
                if (mod == null) {
                    continue;
                }

                for (String ignoreRequiredMod : modConfig.getIgnoreRequiredMods()) {
                    ModDependency dependency = new ModDependency();
                    dependency.setMod(mod.getId());
                    dependency.setDeclaredInMod(declaredInMod.getId());
                    dependency.setTargetMod(ignoreRequiredMod);
                    dependency.setDependencyType(Mod.DependencyType.REPLACED);
                    mod.addDependency(dependency);
                }

                for (String requiredMod : modConfig.getRequiredMods()) {
                    ModDependency dependency = new ModDependency();
                    dependency.setMod(mod.getId());
                    dependency.setDeclaredInMod(declaredInMod.getId());
                    dependency.setTargetMod(requiredMod);
                    dependency.setDependencyType(Mod.DependencyType.REQUIRED);
                    mod.addDependency(dependency);

                    Collection<String> modsIgnoredRequiredMod = requiredModIgnoredByAlternativeModsMap.get(requiredMod);
                    if (modsIgnoredRequiredMod != null) {
                        for (String modIgnoredRequiredMod : modsIgnoredRequiredMod) {
                            if (dependency.getOverriddenByMod() == null) {
                                dependency.setOverriddenByMod(modIgnoredRequiredMod);
                            }
                            ModDependency dep2 = new ModDependency();
                            dep2.setMod(mod.getId());
                            dep2.setDeclaredInMod(modIgnoredRequiredMod); // todo not correct, but not really needed and can be fixed later
                            dep2.setTargetMod(modIgnoredRequiredMod);
                            dep2.setDependencyType(Mod.DependencyType.REQUIRED);
                            mod.addDependency(dep2);
                        }
                    }
                }

                for (String incompatibleMod : modConfig.getIncompatibleMods()) {
                    ModDependency dependency = new ModDependency();
                    dependency.setMod(mod.getId());
                    dependency.setDeclaredInMod(declaredInMod.getId());
                    dependency.setTargetMod(incompatibleMod);
                    dependency.setDependencyType(Mod.DependencyType.INCOMPATIBLE);
                    mod.addDependency(dependency);
                }
            }
        }

        // fill incoming dependencies
        for (Mod mod : mods) {
            for (ModDependency modDependency : mod.getDependencies()) {
                if (!modDependency.getTargetMod().equals(mod.getId())) {
                    Mod targetMod = modsMap.get(modDependency.getTargetMod());
                    if (targetMod != null) {
                        targetMod.addDependency(modDependency);
                    }
                }
                if (!modDependency.getMod().equals(mod.getId())) { // never happen?
                    Mod targetMod = modsMap.get(modDependency.getMod());
                    if (targetMod != null) {
                        if (!targetMod.getDependencies().contains(modDependency)) {
                            targetMod.addDependency(modDependency);
                        }
                    }
                }
            }
        }

        return mods;
    }

    /**
     * Sort by before/after/required/ignoreRequired without priority group at first step.
     * @param mods
     * @return
     */
    List<Mod> sortModsByRootConfigOnly(LinkedHashMap<String, Mod> mods,
                                       LinkedHashMap<String, LinkedHashSet<String>> requiredModIgnoredByAlternativeModsMap) {
        LinkedHashMap<String, SortItem<String>> sortItemMap = new LinkedHashMap<>(mods.size());
        for (Mod mod : mods.values()) {
            SortItem<String> sortItem = new SortItem<>();
            sortItem.setValue(mod.getId());
            HighlanderModConfig highlanderConfig = mod.getHighlanderModsConfig().getModConfigs().get(mod.getId());
            if (highlanderConfig != null) {
                for (RunOrderDeclaration runOrderDeclaration : highlanderConfig.getRunOrderDeclarations()) {
                    if (runOrderDeclaration.getTargetMod() != null) {
                        if (runOrderDeclaration.getModLoadOrder().isLoadBefore()) {
                            sortItem.getBeforeValues().add(runOrderDeclaration.getTargetMod());
                        } else if (runOrderDeclaration.getModLoadOrder().isLoadAfter()) {
                            sortItem.getAfterValues().add(runOrderDeclaration.getTargetMod());
                        }
                    }
                }
                for (String requiredModId : highlanderConfig.getRequiredMods()) {
                    if (!sortItem.getBeforeValues().contains(requiredModId)
                            && !sortItem.getAfterValues().contains(requiredModId)) {
                        // assume required mod should be loaded before
                        sortItem.getBeforeValues().add(requiredModId);
                        LinkedHashSet<String> requiredModIgnoredByMods = requiredModIgnoredByAlternativeModsMap.getOrDefault(requiredModId, new LinkedHashSet<>());
                        for (String requiredModIgnoredByMod : requiredModIgnoredByMods) {
                            if (!sortItem.getBeforeValues().contains(requiredModIgnoredByMod)
                                    && !sortItem.getAfterValues().contains(requiredModIgnoredByMod)) {
                                sortItem.getBeforeValues().add(requiredModIgnoredByMod);
                            }
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

    List<Mod> sortModsAfterRootSort(LinkedHashMap<String, Mod> mods, boolean setCycleModsToMods) {
        LinkedHashMap<String, SortItem<String>> sortItemMap = new LinkedHashMap<>(mods.size());
        for (Mod mod : mods.values()) {
            SortItem<String> sortItem = new SortItem<>();
            sortItem.setValue(mod.getId());
            for (ModLoadOrderDeclaration loadOrderDeclaration : mod.getLoadOrders()) {
                if (loadOrderDeclaration.getOverriddenByMod() == null && loadOrderDeclaration.getModLoadOrder() != null) {
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
                    List<Mod> cycleMods = cycleModsGroup.stream().map(mods::get).filter(Objects::nonNull).toList();
                    for (Mod cycleMod : cycleMods) {
                        cycleMod.getCycleMods().add(cycleMods);
                    }
                }
            }
        }

        return sortResult.getSorted().stream().map(mods::get).collect(Collectors.toList());
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
                map.put(modDbRecord.getId(), mod);
            }
            if (modDbRecord.getSteamModId() != null) {
                mod.getSteamMod().setSteamModId(modDbRecord.getSteamModId());
            }
            mod.setActive(modDbRecord.getActive());
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
            mod.setSize(FileUtils.getDirectorySize(modFile.getParentFile().toPath()));
            mod.setDirectory(modFile.getParentFile().getAbsoluteFile());
            if (isSteamWorkshopMod) {
                try {
                    mod.getSteamMod().setSteamModId(modFile.getParentFile().getName());
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

    private static LinkedHashMap<String, Mod> toLinkedHashMap(Stream<Mod> mods) {
        return toLinkedHashMap(mods);
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

    public boolean isModExist(String targetModId) {
        Mod mod = allMods.get(targetModId);
        return mod != null && mod.isExist();
    }

}
