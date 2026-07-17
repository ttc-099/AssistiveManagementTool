package com.example.app.service.chartDashboard;

import java.util.*;
import java.util.stream.Collectors;

public class PivotDataService {

    public static class PivotResult {
        private final String group;
        private final Double value;

        public PivotResult(String group, Double value) {
            this.group = group;
            this.value = value;
        }

        public String getGroup() { return group; }
        public Double getValue() { return value; }
    }

    public List<PivotResult> calculatePivot(List<Map<String, String>> data, String groupColumn, String valueColumn, String aggregationType) {
        if (data == null || data.isEmpty() || groupColumn == null || valueColumn == null) {
            return Collections.emptyList();
        }

        Map<String, List<Map<String, String>>> groupedData = data.stream()
                .collect(Collectors.groupingBy(row -> row.getOrDefault(groupColumn, "Unknown")));

        List<PivotResult> results = new ArrayList<>();

        for (Map.Entry<String, List<Map<String, String>>> entry : groupedData.entrySet()) {
            String groupName = entry.getKey();
            List<Map<String, String>> rows = entry.getValue();
            Double aggregatedValue = 0.0;

            switch (aggregationType) {
                case "Sum":
                    aggregatedValue = rows.stream()
                            .mapToDouble(row -> tryParseDouble(row.get(valueColumn)))
                            .sum();
                    break;
                case "Average":
                    aggregatedValue = rows.stream()
                            .mapToDouble(row -> tryParseDouble(row.get(valueColumn)))
                            .average()
                            .orElse(0.0);
                    break;
                case "Count":
                default:
                    aggregatedValue = (double) rows.size();
                    break;
            }
            results.add(new PivotResult(groupName, aggregatedValue));
        }

        results.sort(Comparator.comparing(PivotResult::getGroup));
        return results;
    }

    private double tryParseDouble(String value) {
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.replaceAll("[^\\d.]", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
