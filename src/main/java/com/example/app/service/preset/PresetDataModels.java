package com.example.app.service.preset;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import java.util.Map;

/**
 * Data models for preset table rows
 */
public class PresetDataModels {

    /**
     * Row model for Local vs International tables
     * Supports both FY (with Intake header rows) and UGPG (without Intake)
     */
    public static class LocalVsInternationalRow {
        private final SimpleStringProperty programme;
        private final SimpleBooleanProperty isHeader;
        private final SimpleIntegerProperty lApplications;
        private final SimpleIntegerProperty iApplications;
        private final SimpleIntegerProperty lOffers;
        private final SimpleIntegerProperty iOffers;
        private final SimpleIntegerProperty lCA;
        private final SimpleIntegerProperty iCA;
        private final SimpleIntegerProperty lUA;
        private final SimpleIntegerProperty iUA;

        // Constructor for regular data rows
        public LocalVsInternationalRow(String programme,
                                       int lApplications, int iApplications,
                                       int lOffers, int iOffers,
                                       int lCA, int iCA,
                                       int lUA, int iUA) {
            this.programme = new SimpleStringProperty(programme);
            this.isHeader = new SimpleBooleanProperty(false);
            this.lApplications = new SimpleIntegerProperty(lApplications);
            this.iApplications = new SimpleIntegerProperty(iApplications);
            this.lOffers = new SimpleIntegerProperty(lOffers);
            this.iOffers = new SimpleIntegerProperty(iOffers);
            this.lCA = new SimpleIntegerProperty(lCA);
            this.iCA = new SimpleIntegerProperty(iCA);
            this.lUA = new SimpleIntegerProperty(lUA);
            this.iUA = new SimpleIntegerProperty(iUA);
        }

        // Constructor for header rows (Intake headers)
        public LocalVsInternationalRow(String headerText) {
            this.programme = new SimpleStringProperty(headerText);
            this.isHeader = new SimpleBooleanProperty(true);
            this.lApplications = new SimpleIntegerProperty(0);
            this.iApplications = new SimpleIntegerProperty(0);
            this.lOffers = new SimpleIntegerProperty(0);
            this.iOffers = new SimpleIntegerProperty(0);
            this.lCA = new SimpleIntegerProperty(0);
            this.iCA = new SimpleIntegerProperty(0);
            this.lUA = new SimpleIntegerProperty(0);
            this.iUA = new SimpleIntegerProperty(0);
        }

        // Getters
        public String getProgramme() { return programme.get(); }
        public SimpleStringProperty programmeProperty() { return programme; }
        public boolean isIsHeader() { return isHeader.get(); }
        public SimpleBooleanProperty isHeaderProperty() { return isHeader; }
        public int getLApplications() { return lApplications.get(); }
        public SimpleIntegerProperty lApplicationsProperty() { return lApplications; }
        public int getIApplications() { return iApplications.get(); }
        public SimpleIntegerProperty iApplicationsProperty() { return iApplications; }
        public int getLOffers() { return lOffers.get(); }
        public SimpleIntegerProperty lOffersProperty() { return lOffers; }
        public int getIOffers() { return iOffers.get(); }
        public SimpleIntegerProperty iOffersProperty() { return iOffers; }
        public int getLCA() { return lCA.get(); }
        public SimpleIntegerProperty lCAProperty() { return lCA; }
        public int getICA() { return iCA.get(); }
        public SimpleIntegerProperty iCAProperty() { return iCA; }
        public int getLUA() { return lUA.get(); }
        public SimpleIntegerProperty lUAProperty() { return lUA; }
        public int getIUA() { return iUA.get(); }
        public SimpleIntegerProperty iUAProperty() { return iUA; }

        // Convenience method
        public int getTotalAcceptancesL() { return lCA.get() + lUA.get(); }
        public int getTotalAcceptancesI() { return iCA.get() + iUA.get(); }
    }

    /**
     * Row model for Programme Interests tables
     */
    public static class ProgrammeInterestRow {
        private final SimpleStringProperty programme;
        private final SimpleBooleanProperty isHeader;
        private final Map<String, Integer> decisionCounts;
        private final int total;

        // Constructor for regular data rows
        public ProgrammeInterestRow(String programme, Map<String, Integer> decisionCounts, int total) {
            this.programme = new SimpleStringProperty(programme);
            this.isHeader = new SimpleBooleanProperty(false);
            this.decisionCounts = decisionCounts;
            this.total = total;
        }

        // Constructor for header rows (Intake headers)
        public ProgrammeInterestRow(String headerText) {
            this.programme = new SimpleStringProperty(headerText);
            this.isHeader = new SimpleBooleanProperty(true);
            this.decisionCounts = Map.of();
            this.total = 0;
        }

        public String getProgramme() { return programme.get(); }
        public SimpleStringProperty programmeProperty() { return programme; }
        public boolean isIsHeader() { return isHeader.get(); }
        public SimpleBooleanProperty isHeaderProperty() { return isHeader; }
        public Map<String, Integer> getDecisionCounts() { return decisionCounts; }
        public int getTotal() { return total; }

        public int getCountForDecision(String decision) {
            return decisionCounts.getOrDefault(decision, 0);
        }
    }

    /**
     * Helper class for SQL query results
     */
    public static class CountResult {
        public final String group1;      // Intake or Programme
        public final String group2;      // Programme or Course
        public final String nationality; // "Malaysian" or "Non Malaysian"
        public final String decision;    // UO, CO, CA, UA, etc.
        public final int count;

        public CountResult(String group1, String group2, String nationality, String decision, int count) {
            this.group1 = group1;
            this.group2 = group2;
            this.nationality = nationality;
            this.decision = decision;
            this.count = count;
        }
    }
}