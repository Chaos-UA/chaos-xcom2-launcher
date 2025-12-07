package chaos.xcom.launcher.util;

import static org.junit.jupiter.api.Assertions.*;

import chaos.xcom.launcher.util.SortUtils.SortItem;
import chaos.xcom.launcher.util.SortUtils.SortResult;
import org.junit.jupiter.api.Test;

import java.util.*;


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
        assertEquals(result.getSorted(), List.of("A", "B", "C"));

        // One cycle detected
        assertEquals(1, result.getCycles().size());
        List<String> cycle = result.getCycles().get(0);
        assertEquals(3, cycle.size());
        assertEquals(cycle, List.of("A", "B", "C"));
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

//    @Test
//    public void testSortWithMultipleCycles() {
//        List<SortItem<String>> items = new ArrayList<>();
//        SortItem<String> itemA = new SortItem<>();
//        itemA.setValue("A");
//        itemA.getAfterValues().add("B");
//        items.add(itemA);
//        SortItem<String> itemB = new SortItem<>();
//        itemB.setValue("B");
//        itemB.getAfterValues().add("C");
//        items.add(itemB);
//        SortItem<String> itemC = new SortItem<>();
//        itemC.setValue("C");
//        itemC.getAfterValues().add("D");
//        items.add(itemC);
//        SortItem<String> itemD = new SortItem<>();
//        itemD.setValue("D");
//        itemD.getAfterValues().add("A");
//        items.add(itemD);
//        SortResult<String> result = SortUtils.sort(items);
//        assertTrue(result.getSorted().isEmpty()); // or assert some kind of error message indicating multiple cycles
//    }

}
