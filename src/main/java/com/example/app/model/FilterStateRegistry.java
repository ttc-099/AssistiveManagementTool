package com.example.app.model;

import java.util.*;
import java.util.function.Predicate;

public class FilterStateRegistry {
    private final Map<String, List<Predicate<Map<String, String>>>> columnPredicates = new HashMap<>();

    public void clear() {
        columnPredicates.clear();
    }

    public void clearColumn(String columnName) {
        columnPredicates.remove(columnName);
    }

    public void addPredicate(String columnName, Predicate<Map<String, String>> predicate) {
        columnPredicates.computeIfAbsent(columnName, k -> new ArrayList<>()).add(predicate);
    }

    public void setPredicates(String columnName, List<Predicate<Map<String, String>>> predicates) {
        if (predicates == null || predicates.isEmpty()) {
            columnPredicates.remove(columnName);
        } else {
            columnPredicates.put(columnName, new ArrayList<>(predicates));
        }
    }

    public boolean hasFilter(String columnName) {
        return columnPredicates.containsKey(columnName) && !columnPredicates.get(columnName).isEmpty();
    }

    public Predicate<Map<String, String>> getCombinedPredicate() {
        return row -> columnPredicates.values().stream()
                .flatMap(List::stream)
                .allMatch(p -> p.test(row));
    }
}
