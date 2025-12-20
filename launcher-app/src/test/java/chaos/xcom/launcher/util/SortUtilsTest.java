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
        assertTrue(result.getSorted().indexOf("A") < result.getSorted().indexOf("B"));
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
        assertTrue(sorted.indexOf("1") < sorted.indexOf("2"));

        // 6 must be before 3 (depends on cycle)
        assertTrue(sorted.indexOf("6") < sorted.indexOf("3"));

        // 5 must be after 3 (depends on cycle)
        assertTrue(sorted.indexOf("5") > sorted.indexOf("3"));

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
        List<SortUtils.SortItem<String>> items = List.of(
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

        SortUtils.SortResult<String> result = SortUtils.sort(items);

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

    private SortUtils.SortItem<String> item(String value, Set<String> before, Set<String> after) {
        SortUtils.SortItem<String> i = new SortUtils.SortItem<>();
        i.setValue(value);
        i.getBeforeValues().addAll(before);
        i.getAfterValues().addAll(after);
        return i;
    }

}
