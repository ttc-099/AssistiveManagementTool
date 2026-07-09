package com.example.app.service;

import java.util.Map;

/**
 * Abstract Data Repair Interface for LLM Auto-Correction.
 */
public interface LLMDataRepairService {

    /**
     * Pass a messy data row to an external LLM API to return an intelligently 
     * formatted and corrected JSON map of the cell contents.
     * 
     * @param rawRow The messy data row.
     * @return The corrected data row.
     */
    Map<String, String> suggestCorrections(Map<String, String> rawRow);
}
