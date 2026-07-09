package com.example.app.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GeographicValidationService {

    private static final List<String> COUNTRIES = Arrays.asList("Malaysia", "Singapore", "Thailand", "Indonesia", "Vietnam");
    private static final List<String> STATES = Arrays.asList("Johor", "Selangor", "Kedah", "Kelantan", "Malacca", "Negeri Sembilan", "Pahang", "Penang", "Perak", "Perlis", "Sabah", "Sarawak", "Terengganu");

    public record ValidationError(String header, String value, String suggestion, int rowIndex, Map<String, String> row) {}

    public List<ValidationError> validate(List<Map<String, String>> data) {
        List<ValidationError> errors = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            Map<String, String> row = data.get(i);
            
            // Check Country column for states
            String country = row.getOrDefault("Country", "");
            if (isState(country)) {
                errors.add(new ValidationError("Country", country, "Move '" + country + "' to State", i, row));
            } else if (!country.isEmpty() && !isCountry(country)) {
                // Unknown country - could be flagged but we focus on misalignment as per requirement
            }

            // Check State column for countries
            String state = row.getOrDefault("State", "");
            if (isCountry(state)) {
                errors.add(new ValidationError("State", state, "Move '" + state + "' to Country", i, row));
            }
        }
        return errors;
    }

    private boolean isCountry(String value) {
        return COUNTRIES.stream().anyMatch(c -> c.equalsIgnoreCase(value));
    }

    private boolean isState(String value) {
        return STATES.stream().anyMatch(s -> s.equalsIgnoreCase(value));
    }
}
