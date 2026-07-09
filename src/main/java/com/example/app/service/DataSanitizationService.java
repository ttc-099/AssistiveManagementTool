package com.example.app.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class DataSanitizationService {

    private static final Set<String> GEOGRAPHIC_HEADERS = new HashSet<>(Arrays.asList(
            "Country", "Region", "State", "Postcode", "City", "Nationality"
    ));

    public String sanitize(String header, String value) {
        if (value == null) return "";
        
        // White Space Stripping (Automatically .trim() all trailing and leading white space)
        String sanitized = value.trim();

        // Proper Name Case Conversion for geographic/demographic columns
        // Force strings out of ALL UPPERCASE or all lowercase into clean Proper Name Title Case
        if (isGeographicHeader(header)) {
            sanitized = toTitleCase(sanitized);
        }

        return sanitized;
    }

    private boolean isGeographicHeader(String header) {
        if (header == null) return false;
        return GEOGRAPHIC_HEADERS.stream().anyMatch(h -> h.equalsIgnoreCase(header));
    }

    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return input;
        
        return Arrays.stream(input.toLowerCase().split("\\s+"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
