
package chaos.xcom.launcher.util;

import lombok.Data;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SortUtils {

    /* =======================
       Input DTO
       ======================= */
    @Data
    public static class SortItem<T> {
        private T value;
        private Set<T> beforeValues = new HashSet<>();
        private Set<T> afterValues = new HashSet<>();
    }

    /* =======================
       Internal one-direction model
       value -> afterValues
       ======================= */
    @Data
    private static class SortItemOneDirection<T> {
        private T value;
        private Set<T> afterValues = new HashSet<>();
    }

    /* =======================
       Result DTO
       ======================= */
    @Data
    public static class SortResult<T> {
        private final List<T> sorted;
        private final List<List<T>> cycles;

        public <R> SortResult<R> map(Function<T, R> mapper) {
            return new SortResult<>(
                    sorted.stream().map(mapper).toList(),
                    cycles.stream()
                            .map(c -> c.stream().map(mapper).toList())
                            .toList()
            );
        }
    }

    /* =======================
       Public API
       ======================= */

    public static <T> SortResult<T> sort(Collection<SortItem<T>> input) {
        Collection<SortItemOneDirection<T>> oneDir = convert(input);
        return sortOneDirection(oneDir);
    }

    /* =======================
       Conversion: before/after → after-only
       ======================= */
    private static <T> Collection<SortItemOneDirection<T>> convert(Collection<SortItem<T>> input
    ) {
        Map<T, SortItemOneDirection<T>> result = new LinkedHashMap<>();

        for (SortItem<T> v : input) {
            SortItemOneDirection<T> od = new SortItemOneDirection<>();
            od.setValue(v.getValue());
            result.put(v.getValue(), od);
        }

        for (SortItem<T> item : input) {
            T v = item.getValue();

            // v must be AFTER x → x -> v
            for (T after : item.getAfterValues()) {
                if (result.containsKey(after)) {
                    result.get(after).getAfterValues().add(v);
                }
            }

            // v must be BEFORE x → v -> x
            for (T before : item.getBeforeValues()) {
                if (result.containsKey(before)) {
                    result.get(v).getAfterValues().add(before);
                }
            }
        }

        return result.values();
    }

    /* =======================
       Core algorithm
       ======================= */
    private static <T> SortResult<T> sortOneDirection(Collection<SortItemOneDirection<T>> items) {
        Map<T, List<T>> graph = new LinkedHashMap<>();
        Map<T, Integer> indegree = new LinkedHashMap<>();

        for (SortItemOneDirection<T> v : items) {
            graph.put(v.getValue(), new ArrayList<>());
            indegree.put(v.getValue(), 0);
        }

        for (SortItemOneDirection<T> item : items) {
            for (T after : item.getAfterValues()) {
                graph.get(item.getValue()).add(after);
                indegree.put(after, indegree.get(after) + 1);
            }
        }

        /* ---------- Kahn ---------- */
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
                indegree.put(next, indegree.get(next) - 1);
                if (indegree.get(next) == 0) {
                    queue.add(next);
                }
            }
        }

        /* ---------- Cycles ---------- */
        Set<T> remaining = indegree.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<List<T>> cycles = new ArrayList<>();

        if (remaining.isEmpty()) {
            return new SortResult<>(sorted, cycles);
        }

        List<Set<T>> sccs = findSCCs(graph, remaining);

        Set<T> cycleNodes = new LinkedHashSet<>();
        for (Set<T> scc : sccs) {
            if (scc.size() > 1) {
                cycles.add(new ArrayList<>(scc));
                cycleNodes.addAll(scc);
            }
        }

        /* ---------- Placement ---------- */
        List<T> beforeCycle = new ArrayList<>();
        List<T> afterCycle = new ArrayList<>();

        // Build reverse graph for reachability checks
        Map<T, List<T>> reverseGraph = new LinkedHashMap<>();
        for (T v : graph.keySet()) {
            reverseGraph.put(v, new ArrayList<>());
        }
        for (Map.Entry<T, List<T>> e : graph.entrySet()) {
            T from = e.getKey();
            for (T to : e.getValue()) {
                reverseGraph.get(to).add(from);
            }
        }

        // Compute nodes reachable from cycle nodes (cycle -> ... -> node)
        Set<T> reachableFromCycle = new LinkedHashSet<>();
        ArrayDeque<T> q = new ArrayDeque<>();
        for (T c : cycleNodes) {
            q.add(c);
        }
        while (!q.isEmpty()) {
            T cur = q.poll();
            for (T neigh : graph.get(cur)) {
                if (!reachableFromCycle.contains(neigh) && !cycleNodes.contains(neigh)) {
                    reachableFromCycle.add(neigh);
                    q.add(neigh);
                }
            }
        }

        // Compute nodes that can reach cycle nodes (node -> ... -> cycle)
        Set<T> canReachCycle = new LinkedHashSet<>();
        q.clear();
        for (T c : cycleNodes) {
            q.add(c);
        }
        while (!q.isEmpty()) {
            T cur = q.poll();
            for (T pred : reverseGraph.get(cur)) {
                if (!canReachCycle.contains(pred) && !cycleNodes.contains(pred)) {
                    canReachCycle.add(pred);
                    q.add(pred);
                }
            }
        }

        for (T v : remaining) {
            if (cycleNodes.contains(v)) {
                continue;
            }

            boolean comesAfterCycle = false;
            boolean comesBeforeCycle = false;

            // transitive relationships: if reachable from cycle -> comes after
            if (reachableFromCycle.contains(v)) {
                comesAfterCycle = true;
            }

            // if can reach cycle -> comes before
            if (canReachCycle.contains(v)) {
                comesBeforeCycle = true;
            }

            // If both flags false, this node is unrelated (kept as unrelated)
            if (comesBeforeCycle && !comesAfterCycle) {
                beforeCycle.add(v);
            } else if (comesAfterCycle && !comesBeforeCycle) {
                afterCycle.add(v);
            }
            // else: unrelated -> will be handled below
        }

        // Collect remaining unrelated nodes (were not in Kahn result)
        Set<T> others = new LinkedHashSet<>(remaining);
        others.removeAll(cycleNodes);
        others.removeAll(beforeCycle);
        others.removeAll(afterCycle);
        List<T> unrelated = new ArrayList<>(others);

        // Ensure removed items are not duplicated
        sorted.removeAll(beforeCycle);
        sorted.removeAll(afterCycle);
        sorted.removeAll(unrelated);

        List<T> finalSorted = new ArrayList<>(sorted);
        finalSorted.addAll(beforeCycle);
        finalSorted.addAll(unrelated);   // put unrelated before cycles to keep constraints safe
        finalSorted.addAll(cycleNodes);
        finalSorted.addAll(afterCycle);

        return new SortResult<>(finalSorted, cycles);
    }

    /* =======================
       Tarjan SCC
       ======================= */
    private static <T> List<Set<T>> findSCCs(
            Map<T, List<T>> graph,
            Set<T> nodes
    ) {
        List<Set<T>> result = new ArrayList<>();
        Map<T, Integer> index = new HashMap<>();
        Map<T, Integer> low = new HashMap<>();
        Deque<T> stack = new ArrayDeque<>();
        Set<T> onStack = new HashSet<>();
        int[] idx = {0};

        class DFS {
            void visit(T v) {
                index.put(v, idx[0]);
                low.put(v, idx[0]);
                idx[0]++;
                stack.push(v);
                onStack.add(v);

                for (T w : graph.get(v)) {
                    if (!nodes.contains(w)) continue;

                    if (!index.containsKey(w)) {
                        visit(w);
                        low.put(v, Math.min(low.get(v), low.get(w)));
                    } else if (onStack.contains(w)) {
                        low.put(v, Math.min(low.get(v), index.get(w)));
                    }
                }

                if (low.get(v).equals(index.get(v))) {
                    Set<T> scc = new LinkedHashSet<>();
                    T w;
                    do {
                        w = stack.pop();
                        onStack.remove(w);
                        scc.add(w);
                    } while (!w.equals(v));
                    result.add(scc);
                }
            }
        }

        DFS dfs = new DFS();
        for (T v : nodes) {
            if (!index.containsKey(v)) {
                dfs.visit(v);
            }
        }

        return result;
    }
}
