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

        public <R> SortResult<R> map(Function<T, R> mapper) {
            return new SortResult<>(
                    sorted.stream().map(mapper).toList(),
                    cycles.stream()
                            .map(c -> c.stream().map(mapper).toList())
                            .toList()
            );
        }
    }

    public static <T> SortResult<T> sort(Collection<SortItem<T>> input) {
        Map<String, T> originalValueMap = new LinkedHashMap<>();
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        Set<String> explicitInputSet = new LinkedHashSet<>();

        // 1) Initialize structures and build standard directed edges (From -> To)
        // Must be done in a single pass to preserve the historical discovery order of implicit nodes!
        for (SortItem<T> item : input) {
            String valKey = normalize(item.getValue());
            originalValueMap.putIfAbsent(valKey, item.getValue());
            explicitInputSet.add(valKey);
            graph.putIfAbsent(valKey, new LinkedHashSet<>());

            for (T a : item.getAfterValues()) {
                String aKey = normalize(a);
                originalValueMap.putIfAbsent(aKey, a);
                graph.putIfAbsent(aKey, new LinkedHashSet<>());
                graph.get(aKey).add(valKey); // a must come before valKey
            }

            for (T b : item.getBeforeValues()) {
                String bKey = normalize(b);
                originalValueMap.putIfAbsent(bKey, b);
                graph.putIfAbsent(bKey, new LinkedHashSet<>());
                graph.get(valKey).add(bKey); // valKey must come before b
            }
        }

        // 2) Snapshot strict initial sequence
        List<String> fullDiscoveryOrder = new ArrayList<>(originalValueMap.keySet());
        Map<String, Integer> baseIndexMap = new HashMap<>();
        for (int i = 0; i < fullDiscoveryOrder.size(); i++) {
            baseIndexMap.put(fullDiscoveryOrder.get(i), i);
        }

        // 3) Identify cycles independently for reporting via Tarjan's SCC
        List<List<T>> detectedCycles = new ArrayList<>();
        List<Set<String>> sccs = findSCCs(graph, new HashSet<>(fullDiscoveryOrder));
        for (Set<String> scc : sccs) {
            if (scc.size() > 1) {
                List<T> cycleNodes = scc.stream()
                        .filter(explicitInputSet::contains)
                        .map(originalValueMap::get)
                        .collect(Collectors.toList());
                if (!cycleNodes.isEmpty()) {
                    detectedCycles.add(cycleNodes);
                }
            }
        }

        // 4) Build a stable DFS topological sort
        // We sort neighbors descending so when we reverse the final result, the original order is strictly preserved.
        Map<String, List<String>> sortedGraph = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
            List<String> neighbors = new ArrayList<>(entry.getValue());
            neighbors.sort((a, b) -> Integer.compare(baseIndexMap.get(b), baseIndexMap.get(a)));
            sortedGraph.put(entry.getKey(), neighbors);
        }

        List<String> reverseOrderNodes = new ArrayList<>(fullDiscoveryOrder);
        reverseOrderNodes.sort((a, b) -> Integer.compare(baseIndexMap.get(b), baseIndexMap.get(a)));

        Set<String> visited = new HashSet<>();
        Set<String> onStack = new HashSet<>();
        List<String> resultIds = new ArrayList<>();

        // Start DFS from back to front
        for (String node : reverseOrderNodes) {
            if (!visited.contains(node)) {
                dfs(node, sortedGraph, visited, onStack, resultIds);
            }
        }

        // Reverse to get perfect Topological Order
        Collections.reverse(resultIds);

        // 5) Extract final explicit nodes
        List<T> finalSorted = new ArrayList<>();
        for (String id : resultIds) {
            if (explicitInputSet.contains(id)) {
                finalSorted.add(originalValueMap.get(id));
            }
        }

        return new SortResult<>(finalSorted, detectedCycles);
    }

    private static void dfs(String u, Map<String, List<String>> graph, Set<String> visited, Set<String> onStack, List<String> resultIds) {
        visited.add(u);
        onStack.add(u); // Track active path to detect and ignore cycle back-edges safely

        for (String v : graph.getOrDefault(u, Collections.emptyList())) {
            if (!visited.contains(v)) {
                dfs(v, graph, visited, onStack, resultIds);
            }
        }

        onStack.remove(u);
        resultIds.add(u);
    }

    private static String normalize(Object val) {
        if (val == null) return "";
        return val.toString().toLowerCase(Locale.ROOT).trim();
    }

    private static List<Set<String>> findSCCs(Map<String, Set<String>> graph, Set<String> nodes) {
        List<Set<String>> result = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        int[] idx = {0};

        class DFS {
            void visit(String v) {
                index.put(v, idx[0]);
                low.put(v, idx[0]);
                idx[0]++;
                stack.push(v);
                onStack.add(v);

                for (String w : graph.getOrDefault(v, Collections.emptySet())) {
                    if (!nodes.contains(w)) continue;
                    if (!index.containsKey(w)) {
                        visit(w);
                        low.put(v, Math.min(low.get(v), low.get(w)));
                    } else if (onStack.contains(w)) {
                        low.put(v, Math.min(low.get(v), index.get(w)));
                    }
                }

                if (low.get(v).equals(index.get(v))) {
                    Set<String> scc = new LinkedHashSet<>();
                    String w;
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
        for (String v : nodes) {
            if (!index.containsKey(v)) {
                dfs.visit(v);
            }
        }
        return result;
    }
}