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
            SortResult<R> result = new SortResult<>(
                    sorted.stream().map(mapper).collect(Collectors.toList()),
                    cycles.stream().map(list -> {
                        return list.stream().map(mapper).collect(Collectors.toList());
                    }).collect(Collectors.toList())
            );
            return result;
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

        for (SortItem<T> item : itemMap.values()) {
            graph.put(item.getValue(), new ArrayList<>());
            indegree.put(item.getValue(), 0);
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

        Queue<T> queue = new ArrayDeque<>();
        for (var e : indegree.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }

        List<T> sorted = new ArrayList<>();
        Set<T> processed = new LinkedHashSet<>();

        while (!queue.isEmpty()) {
            T val = queue.poll();
            sorted.add(val);
            processed.add(val);

            for (T next : graph.get(val)) {
                indegree.compute(next, (k, v) -> v - 1);
                if (indegree.get(next) == 0) queue.add(next);
            }
        }

        // Remaining nodes = cycles
        Set<T> remaining = indegree.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        List<List<T>> orderedCycles = new ArrayList<>();

        if (!remaining.isEmpty()) {
            // Use Tarjan SCC to detect cycles
            List<Set<T>> sccs = findSCCs(graph, remaining);

            // Flatten each cycle in deterministic order
            for (Set<T> scc : sccs) {
                List<T> cycleList = scc.stream()
                        .sorted(Comparator.comparing(Object::toString))
                        .toList();
                sorted.addAll(cycleList);
                orderedCycles.add(cycleList);
            }
        }

        return new SortResult<>(sorted, orderedCycles);
    }

    // Tarjan's SCC
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

                if (Objects.equals(lowlink.get(v), index.get(v))) {
                    Set<T> scc = new HashSet<>();
                    T w;
                    do {
                        w = stack.pop();
                        onStack.remove(w);
                        scc.add(w);
                    } while (!w.equals(v));

                    if (scc.size() > 1) {
                        sccs.add(scc);
                    }
                }
            }
        }

        Tarjan t = new Tarjan();
        for (T node : nodesToCheck) {
            if (!index.containsKey(node)) t.strongConnect(node);
        }

        return sccs;
    }
}