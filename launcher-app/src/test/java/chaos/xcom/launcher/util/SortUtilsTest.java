package chaos.xcom.launcher.util;

import chaos.xcom.launcher.util.SortUtils.SortItem;
import chaos.xcom.launcher.util.SortUtils.SortResult;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class SortUtilsTest {

    @Test
    void testSimpleLinearOrder() {
        // A -> B -> C
        SortItem<String> a = new SortItem<>();
        a.setValue("A");
        a.getBeforeValues().add("B");

        SortItem<String> b = new SortItem<>();
        b.setValue("B");
        b.getBeforeValues().add("C");

        SortItem<String> c = new SortItem<>();
        c.setValue("C");

        List<SortItem<String>> items = List.of(a, b, c);
        SortResult<String> result = SortUtils.sort(items);

        assertEquals(List.of("A", "B", "C"), result.getSorted());
        assertTrue(result.getCycles().isEmpty());
    }

    @Test
    void testAfterValues() {
        // B -> C -> A
        SortItem<String> a = new SortItem<>();
        a.setValue("A");

        SortItem<String> b = new SortItem<>();
        b.setValue("B");
        b.getAfterValues().add("A"); // B after A

        SortItem<String> c = new SortItem<>();
        c.setValue("C");
        c.getBeforeValues().add("A"); // C before A

        List<SortItem<String>> items = List.of(a, b, c);
        SortResult<String> result = SortUtils.sort(items);

        // Expected order: C -> A -> B
        assertEquals(List.of("C", "A", "B"), result.getSorted());
        assertTrue(result.getCycles().isEmpty());
    }

    @Test
    void testCycleBetweenThree() {
        // A -> B -> C -> A
        SortItem<String> a = new SortItem<>();
        a.setValue("A");
        a.getBeforeValues().add("B");

        SortItem<String> b = new SortItem<>();
        b.setValue("B");
        b.getBeforeValues().add("C");

        SortItem<String> c = new SortItem<>();
        c.setValue("C");
        c.getBeforeValues().add("A"); // cycle

        List<SortItem<String>> items = List.of(a, b, c);
        SortResult<String> result = SortUtils.sort(items);

        // Sorted list should still contain all elements
        assertEquals(Set.of("A", "B", "C"), new HashSet<>(result.getSorted()));

        // One cycle detected
        assertEquals(1, result.getCycles().size());
        List<String> cycle = result.getCycles().get(0);
        assertEquals(3, cycle.size());
        assertEquals(Set.of("A", "B", "C"), new HashSet<>(cycle));
    }

    @Test
    void testDisconnectedGraph() {
        // A -> B , C independent
        SortItem<String> a = new SortItem<>();
        a.setValue("A");
        a.getBeforeValues().add("B");

        SortItem<String> b = new SortItem<>();
        b.setValue("B");

        SortItem<String> c = new SortItem<>();
        c.setValue("C"); // independent

        List<SortItem<String>> items = List.of(a, b, c);
        SortResult<String> result = SortUtils.sort(items);

        assertEquals(3, result.getSorted().size());
        // A must come before B
        assertBefore(result.getSorted(), "A", "B");
        // C can be anywhere
        assertTrue(result.getSorted().contains("C"));
        assertTrue(result.getCycles().isEmpty());
    }

    @Test
    void testMultipleCycles() {
        // Cycle1: A -> B -> A, Cycle2: C -> D -> E -> C
        SortItem<String> a = new SortItem<>();
        a.setValue("A");
        a.getBeforeValues().add("B");

        SortItem<String> b = new SortItem<>();
        b.setValue("B");
        b.getBeforeValues().add("A");

        SortItem<String> c = new SortItem<>();
        c.setValue("C");
        c.getBeforeValues().add("D");

        SortItem<String> d = new SortItem<>();
        d.setValue("D");
        d.getBeforeValues().add("E");

        SortItem<String> e = new SortItem<>();
        e.setValue("E");
        e.getBeforeValues().add("C");

        SortItem<String> f = new SortItem<>();
        f.setValue("F"); // independent

        List<SortItem<String>> items = List.of(a, b, c, d, e, f);
        SortResult<String> result = SortUtils.sort(items);

        assertEquals(6, result.getSorted().size());
        assertTrue(result.getSorted().containsAll(List.of("A", "B", "C", "D", "E", "F")));

        // Two cycles
        assertEquals(2, result.getCycles().size());

        // Each cycle contains correct nodes
        List<String> cycle1 = result.getCycles().get(0);
        assertTrue(cycle1.containsAll(List.of("A", "B")));

        List<String> cycle2 = result.getCycles().get(1);
        assertTrue(cycle2.containsAll(List.of("C", "D", "E")));
    }

    @Test
    void testMultipleCyclesAndAcyclicGroup() {
        // Cycle1: A -> B -> A
        SortItem<String> a = new SortItem<>();
        a.setValue("A");
        a.getBeforeValues().add("B");

        SortItem<String> b = new SortItem<>();
        b.setValue("B");
        b.getBeforeValues().add("A");

        // Cycle2: C -> D -> E -> C
        SortItem<String> c = new SortItem<>();
        c.setValue("C");
        c.getBeforeValues().add("D");

        SortItem<String> d = new SortItem<>();
        d.setValue("D");
        d.getBeforeValues().add("E");

        SortItem<String> e = new SortItem<>();
        e.setValue("E");
        e.getBeforeValues().add("C");

        // Independent F
        SortItem<String> f = new SortItem<>();
        f.setValue("F");

        // New acyclic group: M -> N -> O
        SortItem<String> m = new SortItem<>();
        m.setValue("M");
        m.getBeforeValues().add("N");

        SortItem<String> n = new SortItem<>();
        n.setValue("N");
        n.getBeforeValues().add("O");

        SortItem<String> o = new SortItem<>();
        o.setValue("O");

        List<SortItem<String>> items = List.of(a, b, c, d, e, f, m, n, o);
        SortResult<String> result = SortUtils.sort(items);

        // Total items
        assertEquals(items.size(), result.getSorted().size());
        assertTrue(result.getSorted().containsAll(List.of("A", "B", "C", "D", "E", "F", "M", "N", "O")));

        // Two cycles expected
        assertEquals(2, result.getCycles().size());

        // Cycle1: A, B
        List<String> cycle1 = result.getCycles().get(0);
        assertTrue(cycle1.containsAll(List.of("A", "B")));

        // Cycle2: C, D, E
        List<String> cycle2 = result.getCycles().get(1);
        assertTrue(cycle2.containsAll(List.of("C", "D", "E")));

        // Check acyclic group order M -> N -> O
        int indexM = result.getSorted().indexOf("M");
        int indexN = result.getSorted().indexOf("N");
        int indexO = result.getSorted().indexOf("O");
        assertTrue(indexM < indexN && indexN < indexO);
    }

    @Test
    void testDependOnModFromCycle() {
        // Cycle: 2 -> 3 -> 4 -> 2
        SortItem<String> a = new SortItem<>();
        a.setValue("1");
        a.getBeforeValues().add("2");

        SortItem<String> b = new SortItem<>();
        b.setValue("2");
        b.getBeforeValues().add("3");

        SortItem<String> c = new SortItem<>();
        c.setValue("3");
        c.getBeforeValues().add("4");

        SortItem<String> d = new SortItem<>();
        d.setValue("4");
        d.getBeforeValues().add("2");

        // Depends on cycle
        SortItem<String> e = new SortItem<>();
        e.setValue("5");
        e.getAfterValues().add("3"); // must be AFTER cycle

        SortItem<String> i6 = new SortItem<>();
        i6.setValue("6");
        i6.getBeforeValues().add("3"); // must be BEFORE cycle

        List<SortItem<String>> items = List.of(a, b, c, d, e, i6);
        SortResult<String> result = SortUtils.sort(items);

        List<String> sorted = result.getSorted();

        // 1️⃣ Total items
        assertEquals(items.size(), sorted.size());
        assertTrue(sorted.containsAll(List.of("1", "2", "3", "4", "5", "6")));

        // 2️⃣ One cycle detected
        assertEquals(1, result.getCycles().size());
        List<String> cycle = result.getCycles().get(0);
        assertTrue(cycle.containsAll(List.of("2", "3", "4")));

        // 3️⃣ Ordering constraints

        // 1 must be before 2
        assertBefore(sorted, "1", "2");

        // 6 must be before 3 (depends on cycle)
        assertBefore(sorted, "6", "3");

        // 5 must be after 3 (depends on cycle)
        assertBefore(sorted, "3", "5");

        // Entire cycle must be between 6 and 5
        int minCycleIndex = cycle.stream()
                .mapToInt(sorted::indexOf)
                .min()
                .orElseThrow();

        int maxCycleIndex = cycle.stream()
                .mapToInt(sorted::indexOf)
                .max()
                .orElseThrow();

        assertTrue(sorted.indexOf("6") < minCycleIndex);
        assertTrue(sorted.indexOf("5") > maxCycleIndex);
    }

    @Test
    void testLargeCycleDoesNotDropNodes() {
        List<SortItem<String>> items = List.of(
                item("WOTCMoreSparkWeapons",
                        Set.of(),
                        Set.of("zzzWeaponSkinReplacer", "X2WOTCCommunityHighlander",
                                "WOTCIridarTemplateMaster", "WepUpgradeFix")),
                item("MECTroopersLWOTC",
                        Set.of(),
                        Set.of("WOTCStormriderClass", "ABBPerkPack",
                                "WOTCMoreSparkWeapons", "ModJamLWOTC_M2")),
                item("BuildableUnits",
                        Set.of("WOTCRocketLaunchers"),
                        Set.of("ABBPerkPack", "MECTroopersLWOTC")),
                item("FullerOverrideBUAddOn",
                        Set.of(),
                        Set.of("BuildableUnits", "zzzWeaponSkinReplacer")),
                item("ModJamLWOTC_M2",
                        Set.of(),
                        Set.of("WOTCMoreSparkWeapons", "X2WOTCCommunityHighlander")),
                item("zzzWeaponSkinReplacer",
                        Set.of(),
                        Set.of("ModJamLWOTC_M2"))
        );

        SortResult<String> result = SortUtils.sort(items);

        // 1️⃣ Nothing lost
        assertEquals(items.size(), result.getSorted().size());
        assertTrue(result.getSorted().containsAll(List.of("WOTCMoreSparkWeapons", "MECTroopersLWOTC", "BuildableUnits", "FullerOverrideBUAddOn", "ModJamLWOTC_M2", "zzzWeaponSkinReplacer")));

        // 2️⃣ One cycle
        assertEquals(1, result.getCycles().size());
        assertTrue(result.getCycles().get(0)
                .containsAll(Set.of(
                        "WOTCMoreSparkWeapons",
                        "zzzWeaponSkinReplacer",
                        "ModJamLWOTC_M2"
                )));

        // 3️⃣ Ordering: FullerOverrideBUAddOn AFTER zzzWeaponSkinReplacer
        assertTrue(
                result.getSorted().indexOf("zzzWeaponSkinReplacer")
                        < result.getSorted().indexOf("FullerOverrideBUAddOn")
        );

        // 4 Ordering: BuildableUnits After MECTroopersLWOTC
        assertTrue(
                result.getSorted().indexOf("MECTroopersLWOTC")
                        < result.getSorted().indexOf("BuildableUnits")
        );
    }

    @Test
    void multipleItems_shouldKeepOrderIfNotDependOnAnything() {
        // given
        List<SortItem<String>> items = List.of(
                item("2", Set.of(), Set.of()),
                item("1", Set.of(), Set.of()),
                item("3", Set.of(), Set.of()),
                item("b2", Set.of("b1"), Set.of("b3", "b4")),
                item("b3", Set.of(), Set.of()),
                item("b1", Set.of(), Set.of()),
                item("c", Set.of(), Set.of()),

                item("a1", Set.of(), Set.of("a2")),
                item("a2", Set.of(), Set.of()),
                item("gg2", Set.of(), Set.of()),
                item("gg1", Set.of(), Set.of()),
                item("gg3", Set.of(), Set.of())
        );

        // when
        SortResult<String> result = SortUtils.sort(items);

        // then
        assertEquals("2", result.getSorted().get(0), toResolvedItemsString(result.getSorted()));
        assertEquals("1", result.getSorted().get(1), toResolvedItemsString(result.getSorted()));
        assertEquals("3", result.getSorted().get(2), toResolvedItemsString(result.getSorted()));

        assertEquals(items.size(), result.getSorted().size());
        assertBefore(result.getSorted(), "b2", "b1");
        assertBefore(result.getSorted(), "b3", "b2");

        assertBefore(result.getSorted(), "b1", "c");
        assertBefore(result.getSorted(), "b2", "c");
        assertBefore(result.getSorted(), "b3", "c");

        assertBefore(result.getSorted(), "c", "a1");
        assertBefore(result.getSorted(), "c", "a2");
        assertBefore(result.getSorted(), "c", "gg1");
        assertBefore(result.getSorted(), "c", "gg2");
        assertBefore(result.getSorted(), "c", "gg3");

        assertBefore(result.getSorted(), "a2", "a1");

        int lastIndex = result.getSorted().size() - 1;
        assertEquals("gg2", result.getSorted().get(lastIndex - 2), toResolvedItemsString(result.getSorted()));
        assertEquals("gg1", result.getSorted().get(lastIndex - 1), toResolvedItemsString(result.getSorted()));
        assertEquals("gg3", result.getSorted().get(lastIndex), toResolvedItemsString(result.getSorted()));
    }

    @Test
    void multipleItems_shouldSortModsSample() throws Exception {
        // given
        SortItem<String> item1 = new SortItem<>();
        item1.setValue("WOTCMoreSparkWeapons");
        item1.setBeforeValues(Set.of());
        item1.setAfterValues(Set.of(
                "zzzWeaponSkinReplacer",
                "X2WOTCCommunityHighlander",
                "WOTCIridarTemplateMaster",
                "WepUpgradeFix"
        ));

        // 2. ExpandedPlayableAliens
        SortItem<String> item2 = new SortItem<>();
        item2.setValue("ExpandedPlayableAliens");
        item2.setBeforeValues(Set.of());
        item2.setAfterValues(Set.of(
                "WOTCStormriderClass", "FrostDivision", "MeristPerkPack", "zzzWeaponSkinReplacer",
                "WoTCPathfinders", "MutonHunter", "WOTCIridarPerkPack", "LongWarOfTheChosen",
                "MutonHarriers", "MercPlasmaGhostTemplates", "PetRockPerkPack", "BstarsPerkPack",
                "Ketaros2DResources", "MitzrutiPerkPack", "TedModJamForLWOTC", "AbilityToSlotReassignment",
                "WelcometotheDromeDome", "WOTC_ExtendedPerkPack", "WotC_AshlynneMutonDestroyer", "ABBPerkPack",
                "AssTroopers", "PlayableTitanArmor", "WOTCIridarTemplateMaster", "BioDivision",
                "ShadowOpsPerkPack", "AbilityEditor", "WotC_AshlynneFlameViper", "PlayableXCOM2AliensLWOTC",
                "WOTCIridarAdventArsenalGhostTemplates"
        ));

        // 3. TedModJamForLWOTC
        SortItem<String> item3 = new SortItem<>();
        item3.setValue("TedModJamForLWOTC");
        item3.setBeforeValues(Set.of());
        item3.setAfterValues(Set.of(
                "WOTCClausImmolators", "FrostDivision", "WOTCMoreSparkWeapons", "WOTC_CombiWSLAB",
                "WOTCCoreCollection", "LongWarOfTheChosen", "MutonHarriers", "Ketaros2DResources",
                "NelVlesis_Icon_Overrides", "ImmolatorChemthrower", "MitzrutiPerkPack", "X2WOTCCommunityHighlander",
                "NegativeMobilityFix", "WotC_AshlynneAdvWarlock", "TruePrimarySecondaries", "RepurposeAbilities",
                "WOTC_GunRaisedAnim", "WOTCIridarTemplateMaster", "BioDivision", "ConfigurableImmunities",
                "ChooseYourAliens", "WOTCRocketLaunchers", "EverVigilantBugfixesConfigAddOn", "NormalizeLoot",
                "WOTCIridarLaserCoilAssets", "EnemyReskinnerRedux", "AbilityEditor"
        ));

        // 4. zzzWeaponSkinReplacer
        SortItem<String> item4 = new SortItem<>();
        item4.setValue("zzzWeaponSkinReplacer");
        item4.setBeforeValues(Set.of());
        item4.setAfterValues(Set.of(
                "TedModJamForLWOTC",
                "TruePrimarySecondaries",
                "X2WOTCCommunityHighlander",
                "LongWarOfTheChosen",
                "WOTC_GunRaisedAnim",
                "PrimarySecondaries",
                "XCOM2RPGOverhaul"
        ));

        // 5. PlayableXCOM2AliensLWOTC
        SortItem<String> item5 = new SortItem<>();
        item5.setValue("PlayableXCOM2AliensLWOTC");
        item5.setBeforeValues(Set.of("ModJamLWOTC"));
        item5.setAfterValues(Set.of(
                "ABBPerkPack", "SensibleMissionPenalties_WOTC", "zzzWeaponSkinReplacer", "IridarsPsiOverhaul",
                "RepurposeAbilities", "XV_ViperSkills", "Ketaros2DResources", "ZombiesDontCountWOTC",
                "ShadowOpsPerkPack", "MitzrutiPerkPack", "CryoPerkPack", "SlagAndMelta",
                "WOTC_ExtendedPerkPack", "X2WOTCCommunityHighlander", "MechatronicWarfarePerkPack", "ZombiesFix",
                "AbilityEditor", "NoUnconsciousAI"
        ));

        List<SortItem<String>> items = List.of(item1, item2, item3, item4, item5);

        // when
        SortResult<String> result = SortUtils.sort(items);

        // then
        assertEquals(items.size(), result.getSorted().size());
        assertBefore(result.getSorted(), "PlayableXCOM2AliensLWOTC", "ExpandedPlayableAliens");
    }

    private SortItem<String> item(String value, Set<String> before, Set<String> after) {
        SortItem<String> i = new SortItem<>();
        i.setValue(value);
        i.getBeforeValues().addAll(before);
        i.getAfterValues().addAll(after);
        return i;
    }

    /**
     * Assert that item1 comes before item2 in the sorted list.
     * On failure, prints all sorted items with their indices for debugging.
     */
    private void assertBefore(List<String> sorted, String itemBefore, String itemAfter) {
        int index1 = sorted.indexOf(itemBefore);
        int index2 = sorted.indexOf(itemAfter);

        if (index1 == -1 || index2 == -1 || index1 >= index2) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n❌ Assertion failed: '").append(itemBefore).append("' should come before '").append(itemAfter).append("'\n");
            sb.append("   ").append(itemBefore).append(" index: ").append(index1).append("\n");
            sb.append("   ").append(itemAfter).append(" index: ").append(index2).append("\n\n");
            sb.append(toResolvedItemsString(sorted));
            throw new AssertionError(sb.toString());
        }
    }

    private String toResolvedItemsString(List<String> sorted) {
        StringBuilder sb = new StringBuilder();
        sb.append("Resolved items:\n");
        for (int i = 0; i < sorted.size(); i++) {
            sb.append("[").append(i).append("] ").append(sorted.get(i)).append("\n");
        }
        return sb.toString();
    }
}
