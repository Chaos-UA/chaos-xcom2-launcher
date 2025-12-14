package chaos.xcom.launcher.util;

import lombok.Data;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SortUtils {

    @Data
    public static class SortItem<T> {
        private T value;
        private Set<T> beforeValues = new HashSet<>();
        private Set<T> afterValues = new HashSet<>();
    }

    @Data
    public static class SortResult<T> {
        private final List<T> sorted;
        private final List<List<T>> cycles;

        public SortResult(List<T> sorted, List<List<T>> cycles) {
            this.sorted = sorted;
            this.cycles = cycles;
        }

        public <R> SortResult<R> map(Function<T, R> mapper) {
            return new SortResult<>(
                    sorted.stream().map(mapper).toList(),
                    cycles.stream()
                            .map(c -> c.stream().map(mapper).toList())
                            .toList()
            );
        }
    }

    public static <T> SortResult<T> sort(List<SortItem<T>> items) {
        LinkedHashMap<T, SortItem<T>> map = new LinkedHashMap<>();
        for (SortItem<T> item : items) {
            map.put(item.getValue(), item);
        }
        return sort(map);
    }

    public static <T> SortResult<T> sort(Map<T, SortItem<T>> itemMap) {
        if (itemMap.isEmpty()) {
            return new SortResult<>(List.of(), List.of());
        }

        Map<T, List<T>> graph = new LinkedHashMap<>();
        Map<T, Integer> indegree = new LinkedHashMap<>();

        for (T v : itemMap.keySet()) {
            graph.put(v, new ArrayList<>());
            indegree.put(v, 0);
        }

        // Build edges
        for (SortItem<T> item : itemMap.values()) {
            T val = item.getValue();

            for (T after : item.getAfterValues()) {
                if (itemMap.containsKey(after)) {
                    graph.get(after).add(val);
                    indegree.compute(val, (k, v) -> v + 1);
                }
            }

            for (T before : item.getBeforeValues()) {
                if (itemMap.containsKey(before)) {
                    graph.get(val).add(before);
                    indegree.compute(before, (k, v) -> v + 1);
                }
            }
        }

        // Kahn's algorithm
        Queue<T> queue = new ArrayDeque<>();
        for (var e : indegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }

        List<T> sorted = new ArrayList<>();

        while (!queue.isEmpty()) {
            T v = queue.poll();
            sorted.add(v);

            for (T next : graph.get(v)) {
                indegree.compute(next, (k, val) -> val - 1);
                if (indegree.get(next) == 0) {
                    queue.add(next);
                }
            }
        }

        // Remaining nodes (blocked by cycles)
        Set<T> remaining = indegree.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<List<T>> orderedCycles = new ArrayList<>();

        if (!remaining.isEmpty()) {
            List<Set<T>> sccs = findSCCs(graph, remaining);

            Set<T> cycleNodes = new HashSet<>();

            for (Set<T> scc : sccs) {
                if (scc.size() > 1) {
                    List<T> cycle = new ArrayList<>(scc);
                    orderedCycles.add(cycle);
                    cycleNodes.addAll(cycle);
                }
            }

            // Classify leftover nodes
            List<T> beforeCycles = new ArrayList<>();
            List<T> afterCycles = new ArrayList<>();

            for (T node : remaining) {
                if (cycleNodes.contains(node)) continue;

                boolean dependsOnCycle = false;
                for (T cycleNode : cycleNodes) {
                    if (graph.get(cycleNode).contains(node)) {
                        dependsOnCycle = true;
                        break;
                    }
                }

                if (dependsOnCycle) {
                    afterCycles.add(node);
                } else {
                    beforeCycles.add(node);
                }
            }

            // Remove wrongly emitted nodes (must come AFTER cycles)
            sorted.removeAll(afterCycles);

            // Insert BEFORE-cycle nodes just before cycles
            int cycleInsertIndex = sorted.size();
            sorted.addAll(cycleNodes);

            // Insert AFTER-cycle nodes
            sorted.addAll(afterCycles);
        }

        return new SortResult<>(sorted, orderedCycles);
    }

    // Tarjan SCC
    private static <T> List<Set<T>> findSCCs(Map<T, List<T>> graph, Set<T> nodesToCheck) {
        List<Set<T>> sccs = new ArrayList<>();
        Map<T, Integer> index = new HashMap<>();
        Map<T, Integer> lowlink = new HashMap<>();
        Deque<T> stack = new ArrayDeque<>();
        Set<T> onStack = new HashSet<>();
        int[] idx = {0};

        class Tarjan {
            void strongConnect(T v) {
                index.put(v, idx[0]);
                lowlink.put(v, idx[0]);
                idx[0]++;
                stack.push(v);
                onStack.add(v);

                for (T w : graph.get(v)) {
                    if (!nodesToCheck.contains(w)) continue;

                    if (!index.containsKey(w)) {
                        strongConnect(w);
                        lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
                    } else if (onStack.contains(w)) {
                        lowlink.put(v, Math.min(lowlink.get(v), index.get(w)));
                    }
                }

                if (lowlink.get(v).equals(index.get(v))) {
                    Set<T> scc = new LinkedHashSet<>();
                    T w;
                    do {
                        w = stack.pop();
                        onStack.remove(w);
                        scc.add(w);
                    } while (!w.equals(v));

                    sccs.add(scc);
                }
            }
        }

        Tarjan t = new Tarjan();
        for (T node : nodesToCheck) {
            if (!index.containsKey(node)) {
                t.strongConnect(node);
            }
        }

        return sccs;
    }
}
