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
        List<T> inputOrder = new ArrayList<>();

        for (SortItemOneDirection<T> v : items) {
            T value = v.getValue();
            graph.put(value, new ArrayList<>());
            indegree.put(value, 0);
            inputOrder.add(value);
        }

        for (SortItemOneDirection<T> item : items) {
            for (T after : item.getAfterValues()) {
                // ignore unknown targets
                if (!graph.containsKey(after)) {
                    continue;
                }
                graph.get(item.getValue()).add(after);
                indegree.put(after, indegree.get(after) + 1);
            }
        }

        /* ---------- Stable Kahn (preserve input order among available nodes) ---------- */
        Map<T, Integer> inputIndex = new HashMap<>();
        for (int i = 0; i < inputOrder.size(); i++) {
            inputIndex.put(inputOrder.get(i), i);
        }

        PriorityQueue<T> pq = new PriorityQueue<>(Comparator.comparingInt(inputIndex::get));
        for (T node : inputOrder) if (indegree.get(node) == 0) pq.add(node);

        List<T> sorted = new ArrayList<>();
        while (!pq.isEmpty()) {
            T v = pq.poll();
            sorted.add(v);
            for (T nxt : graph.get(v)) {
                indegree.put(nxt, indegree.get(nxt) - 1);
                if (indegree.get(nxt) == 0) pq.add(nxt);
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
        // collect cycle SCCs (size>1 or self-loop if present)
        List<Set<T>> cycleSCCs = sccs.stream().filter(s -> s.size() > 1).toList();
        Set<T> cycleNodes = cycleSCCs.stream().flatMap(Set::stream).collect(Collectors.toCollection(LinkedHashSet::new));
        for (Set<T> scc : cycleSCCs) cycles.add(new ArrayList<>(scc));

        /* ---------- Final merge: Emit in phases (all respecting input order):
           1) sorted acyclic nodes (from Kahn) excluding cycle-related nodes
           2) nodes that can reach cycles (preCycle)
           3) cycle SCC blocks (in input order)
           4) nodes reachable from cycles (postCycle)
           5) any remaining nodes
         */
        // Build reverse graph for reachability checks
        Map<T, List<T>> reverseGraph = new LinkedHashMap<>();
        for (T v : graph.keySet()) reverseGraph.put(v, new ArrayList<>());
        for (Map.Entry<T, List<T>> e : graph.entrySet()) {
            T from = e.getKey();
            for (T to : e.getValue()) {
                reverseGraph.get(to).add(from);
            }
        }

        // compute postCycle: nodes reachable from any cycle node
        Set<T> postCycle = new LinkedHashSet<>();
        ArrayDeque<T> q = new ArrayDeque<>(cycleNodes);
        while (!q.isEmpty()) {
            T cur = q.poll();
            for (T nxt : graph.getOrDefault(cur, List.of())) {
                if (!cycleNodes.contains(nxt) && postCycle.add(nxt)) q.add(nxt);
            }
        }

        // compute preCycle: nodes that can reach cycle nodes
        Set<T> preCycle = new LinkedHashSet<>();
        q.clear();
        q.addAll(cycleNodes);
        while (!q.isEmpty()) {
            T cur = q.poll();
            for (T prev : reverseGraph.getOrDefault(cur, List.of())) {
                if (!cycleNodes.contains(prev) && preCycle.add(prev)) q.add(prev);
            }
        }

        List<T> finalSorted = new ArrayList<>();
        Set<T> emitted = new HashSet<>();

        // Map node -> its SCC for quick lookup
        Map<T, Set<T>> nodeToScc = new HashMap<>();
        for (Set<T> scc : cycleSCCs) for (T v : scc) nodeToScc.put(v, scc);

        // 1) emit sorted acyclic nodes that are not part of cycles or postCycle (these came from Kahn)
        for (T node : inputOrder) {
            if (sorted.contains(node) && !cycleNodes.contains(node) && !postCycle.contains(node) && !emitted.contains(node)) {
                finalSorted.add(node);
                emitted.add(node);
            }
        }

        // 2) emit preCycle nodes in input order
        for (T node : inputOrder) {
            if (preCycle.contains(node) && !emitted.contains(node)) {
                finalSorted.add(node);
                emitted.add(node);
            }
        }

        // 3) emit cycle SCCs in input order (emit each SCC members in input order)
        for (T node : inputOrder) {
            if (cycleNodes.contains(node) && !emitted.contains(node)) {
                Set<T> scc = nodeToScc.get(node);
                for (T n : inputOrder) {
                    if (scc.contains(n) && !emitted.contains(n)) {
                        finalSorted.add(n);
                        emitted.add(n);
                    }
                }
            }
        }

        // 4) emit postCycle nodes in input order
        for (T node : inputOrder) {
            if (postCycle.contains(node) && !emitted.contains(node)) {
                finalSorted.add(node);
                emitted.add(node);
            }
        }

        // 5) any remaining (fallback)
        for (T node : inputOrder) {
            if (!emitted.contains(node)) {
                finalSorted.add(node);
                emitted.add(node);
            }
        }

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
