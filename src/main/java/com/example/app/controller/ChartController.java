package com.example.app.controller;

import com.example.app.model.MultiSheetModel;
import com.example.app.service.chartDashboard.PivotDataService;
import com.example.app.service.preset.PresetService;
import com.example.app.service.preset.PresetTableViewBuilder;
import com.example.app.service.preset.PresetDataModels.LocalVsInternationalRow;
import com.example.app.service.preset.PresetDataModels.ProgrammeInterestRow;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.*;

public class ChartController {

    // ===== FXML FIELDS =====
    // View Selector
    @FXML private ComboBox<String> viewSelector;
    @FXML private VBox pivotView;
    @FXML private VBox barChartView;
    @FXML private VBox pieChartView;

    // Pivot Table
    @FXML private ComboBox<String> pivotRowCombo;
    @FXML private ComboBox<String> pivotValueCombo;
    @FXML private ComboBox<String> pivotAggCombo;
    @FXML private TableView<PivotDataService.PivotResult> pivotTable;
    @FXML private TableColumn<PivotDataService.PivotResult, String> pivotGroupCol;
    @FXML private TableColumn<PivotDataService.PivotResult, Number> pivotValueCol;
    @FXML private VBox pivotFieldsContainer;

    // Bar Chart
    @FXML private ComboBox<String> barChartCategoryCombo;
    @FXML private ComboBox<String> barChartValueCombo;
    @FXML private ComboBox<String> barChartAggCombo;
    @FXML private ComboBox<String> barChartGroupCombo;
    @FXML private BarChart<String, Number> barChart;

    // Pie Chart
    @FXML private ComboBox<String> pieChartCategoryCombo;
    @FXML private ComboBox<String> pieChartValueCombo;
    @FXML private ComboBox<String> pieChartAggCombo;
    @FXML private PieChart pieChart;

    // ===== PRESET FXML FIELDS =====
    @FXML private ComboBox<String> presetSelector;

    // ===== REFERENCES =====
    private DashboardController dashboardController;
    private DataController dataController;

    // ===== DATA =====
    private final PivotDataService pivotService = new PivotDataService();
    private List<PivotDataService.PivotResult> currentPivotResults = new ArrayList<>();

    // ===== PRESET SERVICES =====
    private PresetService presetService;
    private final PresetTableViewBuilder tableBuilder = new PresetTableViewBuilder();

    // ===== SETTERS =====
    public void setDataController(DataController dataController) {
        this.dataController = dataController;
        // Initialize preset service when data controller is set
        if (dataController != null) {
            // Get dbManager from DataController via reflection
            try {
                var field = dataController.getClass().getDeclaredField("dbManager");
                field.setAccessible(true);
                var dbManager = (com.example.app.db.DatabaseManager) field.get(dataController);
                if (dbManager != null) {
                    this.presetService = new PresetService(dbManager);
                }
            } catch (Exception e) {
                System.out.println("⚠️ Could not initialize PresetService: " + e.getMessage());
            }
        }
    }

    public void setDashboardController(DashboardController dashboardController) {
        this.dashboardController = dashboardController;
    }

    public void updateComboboxes(List<String> headers) {
        pivotRowCombo.getItems().setAll(headers);
        pivotValueCombo.getItems().setAll(headers);
        barChartCategoryCombo.getItems().setAll(headers);
        barChartValueCombo.getItems().setAll(headers);
        barChartGroupCombo.getItems().setAll(headers);
        barChartGroupCombo.getItems().add(0, "None");
        pieChartCategoryCombo.getItems().setAll(headers);
        pieChartValueCombo.getItems().setAll(headers);

        if (!headers.isEmpty()) {
            pivotRowCombo.getSelectionModel().select(0);
            pivotValueCombo.getSelectionModel().select(Math.min(1, headers.size() - 1));
            barChartCategoryCombo.getSelectionModel().select(0);
            barChartValueCombo.getSelectionModel().select(Math.min(1, headers.size() - 1));
            barChartGroupCombo.getSelectionModel().select("None");
            pieChartCategoryCombo.getSelectionModel().select(0);
            pieChartValueCombo.getSelectionModel().select(Math.min(1, headers.size() - 1));
        }
    }

    public void populatePivotFieldList(MultiSheetModel model) {
        if (pivotFieldsContainer == null) {
            System.out.println("⚠️ pivotFieldsContainer is null - skipping");
            return;
        }

        pivotFieldsContainer.getChildren().clear();
        if (model == null) return;

        MultiSheetModel.SheetData activeSheet = model.getActiveSheet();
        if (activeSheet == null) return;

        for (String header : activeSheet.getHeaders()) {
            CheckBox cb = new CheckBox(header);
            cb.setStyle("-fx-font-size: 11px;");
            pivotFieldsContainer.getChildren().add(cb);
        }
        System.out.println("✅ Added " + pivotFieldsContainer.getChildren().size() + " fields to pivotFieldsContainer");
    }

    // ===== GETTERS =====
    public List<PivotDataService.PivotResult> getCurrentPivotResults() {
        return currentPivotResults;
    }

    public TableView<PivotDataService.PivotResult> getPivotTable() {
        return pivotTable;
    }

    public BarChart<String, Number> getBarChart() {
        return barChart;
    }

    public PieChart getPieChart() {
        return pieChart;
    }

    public ComboBox<String> getPivotRowCombo() {
        return pivotRowCombo;
    }

    public ComboBox<String> getPivotValueCombo() {
        return pivotValueCombo;
    }

    public ComboBox<String> getPivotAggCombo() {
        return pivotAggCombo;
    }

    public ComboBox<String> getBarChartCategoryCombo() {
        return barChartCategoryCombo;
    }

    public ComboBox<String> getBarChartValueCombo() {
        return barChartValueCombo;
    }

    public ComboBox<String> getBarChartAggCombo() {
        return barChartAggCombo;
    }

    public ComboBox<String> getPieChartCategoryCombo() {
        return pieChartCategoryCombo;
    }

    public ComboBox<String> getPieChartValueCombo() {
        return pieChartValueCombo;
    }

    public ComboBox<String> getPieChartAggCombo() {
        return pieChartAggCombo;
    }

    // ===== INITIALIZATION =====
    @FXML
    public void initialize() {
        // Pivot aggregations
        pivotAggCombo.setItems(FXCollections.observableArrayList("Count", "Sum", "Average"));
        pivotAggCombo.getSelectionModel().select("Count");

        // Bar chart aggregations
        barChartAggCombo.setItems(FXCollections.observableArrayList("Count", "Sum", "Average", "Min", "Max"));
        barChartAggCombo.getSelectionModel().select("Count");

        // Pie chart aggregations
        pieChartAggCombo.setItems(FXCollections.observableArrayList("Count", "Sum", "Average", "Min", "Max"));
        pieChartAggCombo.getSelectionModel().select("Count");

        // Pivot table columns
        pivotGroupCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getGroup()));
        pivotValueCol.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getValue()));

        // Alternating rows
        setupAlternatingRows(pivotTable);

        // ===== VIEW SELECTOR =====
        viewSelector.setItems(FXCollections.observableArrayList("Pivot Table", "Bar Chart", "Pie Chart"));
        viewSelector.getSelectionModel().select("Pivot Table");

        viewSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            switchView(newVal);
        });

        // Default to Pivot view
        switchView("Pivot Table");

        // ===== PRESET SYSTEM =====
        setupPresetSystem();

        // ===== INITIALIZE CHART AXES =====
        initializeChartAxes();

        // Debug
        System.out.println("✅ ChartController initialized with Pivot Table view");
    }

    // ===== FIX: Initialize chart axes with proper settings =====
    private void initializeChartAxes() {
        // Configure Bar Chart Y Axis
        NumberAxis barYAxis = (NumberAxis) barChart.getYAxis();
        if (barYAxis != null) {
            barYAxis.setAutoRanging(true);
            barYAxis.setForceZeroInRange(false);
            barYAxis.setLowerBound(0);
            barYAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(barYAxis) {
                @Override
                public String toString(Number object) {
                    if (object == null) return "";
                    if (object.doubleValue() == 0) return "0";
                    if (object.doubleValue() >= 1000) {
                        return String.format("%.0fK", object.doubleValue() / 1000);
                    }
                    return String.format("%.0f", object.doubleValue());
                }
            });
        }

        // Configure Pie Chart
        pieChart.setLabelsVisible(true);
        pieChart.setClockwise(true);

        // Set initial empty state
        barChart.setTitle("Select data and click Update");
        pieChart.setTitle("Select data and click Update");
    }

    // ===== FIX: Reset chart axes for proper rendering =====
    private void resetChartAxes() {
        // Reset Bar Chart Y Axis
        NumberAxis barYAxis = (NumberAxis) barChart.getYAxis();
        if (barYAxis != null) {
            barYAxis.setAutoRanging(true);
            barYAxis.setForceZeroInRange(false);
            barYAxis.setLowerBound(0);
            // Don't set upper bound - let it auto-calculate
        }

        // Reset Bar Chart X Axis
        CategoryAxis barXAxis = (CategoryAxis) barChart.getXAxis();
        if (barXAxis != null) {
            barXAxis.setCategories(FXCollections.observableArrayList());
            barXAxis.setAutoRanging(true);
        }

        // Reset Pie Chart
        pieChart.getData().clear();

        // Force layout refresh
        Platform.runLater(() -> {
            barChart.layout();
            barChart.requestLayout();
            pieChart.layout();
            pieChart.requestLayout();
        });
    }

    // ================================================================
    // ===== PRESET SYSTEM =====
    // ================================================================

    private void setupPresetSystem() {
        // Setup preset selector
        presetSelector.setItems(FXCollections.observableArrayList(
                "Local vs International (FY)",
                "Local vs International (UG)",
                "Programme Interests (FY - Malaysian)",
                "Programme Interests (FY - Non-Malaysian)",
                "Programme Interests (UGPG - Malaysian)",
                "Programme Interests (UGPG - Non-Malaysian)"
        ));
        presetSelector.getSelectionModel().selectFirst();
    }

    @FXML
    private void handlePreset() {
        if (presetService == null) {
            showError("Preset Error", "Database not initialized. Please load data first.");
            return;
        }

        String selected = presetSelector.getValue();
        if (selected == null) return;

        // Switch to Pivot Table view
        switchView("Pivot Table");

        try {
            switch (selected) {
                case "Local vs International (FY)":
                    executeLocalVsInternationalFY();
                    break;
                case "Local vs International (UG)":
                    executeLocalVsInternationalUG();
                    break;
                case "Programme Interests (FY - Malaysian)":
                    executeProgrammeInterestsFY("Malaysian");
                    break;
                case "Programme Interests (FY - Non-Malaysian)":
                    executeProgrammeInterestsFY("Non Malaysian");
                    break;
                case "Programme Interests (UGPG - Malaysian)":
                    executeProgrammeInterestsUGPG("Malaysian");
                    break;
                case "Programme Interests (UGPG - Non-Malaysian)":
                    executeProgrammeInterestsUGPG("Non Malaysian");
                    break;
                default:
                    showError("Preset Error", "Unknown preset: " + selected);
            }
        } catch (Exception e) {
            showError("Preset Error", "Failed to execute preset: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void executeLocalVsInternationalFY() {
        var data = presetService.getLocalVsInternationalFY();
        TableView<LocalVsInternationalRow> table = tableBuilder.buildLIVTableView(data, "Local vs International (FY)");
        replacePivotTable(table);
    }

    private void executeLocalVsInternationalUG() {
        var data = presetService.getLocalVsInternationalUGPG();
        TableView<LocalVsInternationalRow> table = tableBuilder.buildLIVTableView(data, "Local vs International (UGPG)");
        replacePivotTable(table);
    }

    private void executeProgrammeInterestsFY(String nationality) {
        var data = presetService.getProgrammeInterestsFY(nationality);
        var decisions = presetService.getDecisionCodes();
        TableView<ProgrammeInterestRow> table = tableBuilder.buildPITableView(data, decisions, "Programme Interests (FY - " + nationality + ")");
        replacePivotTable(table);
    }

    private void executeProgrammeInterestsUGPG(String nationality) {
        var data = presetService.getProgrammeInterestsUGPG(nationality);
        var decisions = presetService.getDecisionCodes();
        TableView<ProgrammeInterestRow> table = tableBuilder.buildPITableView(data, decisions, "Programme Interests (UGPG - " + nationality + ")");
        replacePivotTable(table);
    }

    @SuppressWarnings("unchecked")
    private void replacePivotTable(TableView<?> newTable) {
        // Remove old table from pivotView
        pivotView.getChildren().removeIf(node -> node instanceof TableView);

        // Make the new table grow and fill
        VBox.setVgrow(newTable, javafx.scene.layout.Priority.ALWAYS);
        newTable.setPrefHeight(400);

        // Add it to the pivot view (after the HBox of controls)
        if (!pivotView.getChildren().isEmpty()) {
            int insertIndex = 1; // After the first child (HBox)
            pivotView.getChildren().add(insertIndex, newTable);
        } else {
            pivotView.getChildren().add(newTable);
        }

        // Try to update the pivotTable reference for compatibility
        try {
            var field = this.getClass().getDeclaredField("pivotTable");
            field.setAccessible(true);
            field.set(this, newTable);
        } catch (Exception e) {
            // If we can't set it, it's okay - the UI is updated
        }
    }

    // ================================================================
    // ===== VIEW SWITCHER =====
    // ================================================================

    private void switchView(String view) {
        // Hide all views
        pivotView.setVisible(false);
        pivotView.setManaged(false);
        barChartView.setVisible(false);
        barChartView.setManaged(false);
        pieChartView.setVisible(false);
        pieChartView.setManaged(false);

        // Show selected view
        if (view == null) return;

        switch (view) {
            case "Pivot Table":
                pivotView.setVisible(true);
                pivotView.setManaged(true);
                System.out.println("🔍 Switched to Pivot Table view");
                break;
            case "Bar Chart":
                barChartView.setVisible(true);
                barChartView.setManaged(true);
                System.out.println("🔍 Switched to Bar Chart view");
                break;
            case "Pie Chart":
                pieChartView.setVisible(true);
                pieChartView.setManaged(true);
                System.out.println("🔍 Switched to Pie Chart view");
                break;
            default:
                pivotView.setVisible(true);
                pivotView.setManaged(true);
                break;
        }
    }

    // ================================================================
    // ===== ADD TO DASHBOARD METHODS =====
    // ================================================================

    @FXML
    public void handleAddPivotToDashboard() {
        if (dashboardController == null) {
            showError("Add to Dashboard", "DashboardController is null. Please try again.");
            return;
        }

        if (pivotTable.getItems().isEmpty()) {
            showError("Add to Dashboard", "Pivot table is empty. Please update the pivot table first.");
            return;
        }

        dashboardController.addPivotToDashboard(
                pivotTable,
                pivotRowCombo.getValue(),
                pivotValueCombo.getValue(),
                pivotAggCombo.getValue()
        );
        System.out.println("✅ Added pivot table to dashboard");
    }

    @FXML
    public void handleAddBarChartToDashboard() {
        if (dashboardController == null) {
            showError("Add to Dashboard", "DashboardController is null. Please try again.");
            return;
        }

        if (barChart.getData().isEmpty()) {
            showError("Add to Dashboard", "Bar chart is empty. Please update the chart first.");
            return;
        }

        dashboardController.addBarChartToDashboard(
                barChart,
                barChartCategoryCombo.getValue(),
                barChartValueCombo.getValue(),
                barChartAggCombo.getValue()
        );
        System.out.println("✅ Added bar chart to dashboard");
    }

    @FXML
    public void handleAddPieChartToDashboard() {
        if (dashboardController == null) {
            showError("Add to Dashboard", "DashboardController is null. Please try again.");
            return;
        }

        if (pieChart.getData().isEmpty()) {
            showError("Add to Dashboard", "Pie chart is empty. Please update the chart first.");
            return;
        }

        dashboardController.addPieChartToDashboard(
                pieChart,
                pieChartCategoryCombo.getValue(),
                pieChartValueCombo.getValue(),
                pieChartAggCombo.getValue()
        );
        System.out.println("✅ Added pie chart to dashboard");
    }

    // ================================================================
    // ===== PIVOT TABLE =====
    // ================================================================

    @FXML
    public void handleUpdateDashboard() {
        System.out.println("🔍 Pivot Update called");

        if (dataController == null) {
            showError("Pivot Update", "DataController is null. Please load data first.");
            System.out.println("❌ dataController is null");
            return;
        }

        var filteredData = dataController.getFilteredData();
        if (filteredData == null || filteredData.isEmpty()) {
            showError("Pivot Update", "No data loaded. Please load an Excel file first.");
            System.out.println("❌ filteredData is null or empty");
            return;
        }

        String rowGroup = pivotRowCombo.getValue();
        String valMetric = pivotValueCombo.getValue();
        String aggType = pivotAggCombo.getValue();

        if (rowGroup == null || valMetric == null || aggType == null) {
            showError("Pivot Update", "Please select Row, Value, and Aggregation fields.");
            System.out.println("❌ Missing selections: rowGroup=" + rowGroup + ", valMetric=" + valMetric + ", aggType=" + aggType);
            return;
        }

        System.out.println("📊 Calculating pivot: rowGroup=" + rowGroup + ", valMetric=" + valMetric + ", aggType=" + aggType);
        System.out.println("📊 Data size: " + filteredData.size() + " rows");

        try {
            currentPivotResults = pivotService.calculatePivot(
                    new ArrayList<>(filteredData), rowGroup, valMetric, aggType
            );

            System.out.println("📊 Pivot results: " + currentPivotResults.size() + " rows");

            // Update the table
            pivotTable.getItems().setAll(currentPivotResults);
            pivotTable.refresh();

            // Update charts with the same data
            updateCharts();

            System.out.println("✅ Pivot table updated with " + currentPivotResults.size() + " results");

        } catch (Exception e) {
            showError("Pivot Update", "Error calculating pivot: " + e.getMessage());
            System.err.println("❌ Pivot error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================================================================
    // ===== BAR CHART =====
    // ================================================================

    @FXML
    public void handleUpdateBarChart() {
        if (dataController == null || dataController.getFilteredData() == null) {
            showError("Bar Chart Update", "No data loaded. Please load an Excel file first.");
            return;
        }
        var filteredData = dataController.getFilteredData();

        if (filteredData.isEmpty()) {
            showError("Bar Chart Update", "No data loaded.");
            return;
        }

        String category = barChartCategoryCombo.getValue();
        String value = barChartValueCombo.getValue();
        String aggregation = barChartAggCombo.getValue();
        String groupBy = barChartGroupCombo.getValue();

        if (category == null || value == null || aggregation == null) {
            showError("Bar Chart Update", "Please select Category and Value fields.");
            return;
        }

        try {
            // Reset axes before updating
            resetChartAxes();

            List<PivotDataService.PivotResult> barData = pivotService.calculatePivot(
                    new ArrayList<>(filteredData), category, value, aggregation
            );

            if (groupBy != null && !"None".equals(groupBy) && !groupBy.equals(category)) {
                updateBarChartWithGrouping(barData, groupBy);
            } else {
                updateBarChart(barData);
            }

            // Force UI refresh
            Platform.runLater(() -> {
                barChart.layout();
                barChart.requestLayout();
            });

        } catch (Exception e) {
            showError("Bar Chart Update", "Error updating bar chart: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===== FIX: Updated bar chart rendering =====
    private void updateBarChart(List<PivotDataService.PivotResult> data) {
        barChart.getData().clear();

        if (data == null || data.isEmpty()) {
            barChart.setTitle("No data to display");
            return;
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(barChartAggCombo.getValue() + " of " + barChartValueCombo.getValue());

        // Find max value for axis scaling
        double maxValue = 0;
        for (PivotDataService.PivotResult res : data) {
            double val = res.getValue() != null ? res.getValue() : 0;
            if (val > maxValue) maxValue = val;
            series.getData().add(new XYChart.Data<>(res.getGroup(), val));
        }

        // Add series to chart
        barChart.getData().add(series);

        // Set title
        barChart.setTitle(barChartAggCombo.getValue() + " of " + barChartValueCombo.getValue() +
                " by " + barChartCategoryCombo.getValue());

        // Configure Y axis with proper bounds
        NumberAxis yAxis = (NumberAxis) barChart.getYAxis();
        if (yAxis != null) {
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(0);
            // Add 10% padding to the top
            double upperBound = maxValue * 1.1;
            if (upperBound < 10) upperBound = 10;
            yAxis.setUpperBound(upperBound);
            yAxis.setForceZeroInRange(true);
        }

        applyChartStyles(barChart);
    }

    // ===== FIX: Updated grouped bar chart rendering =====
    private void updateBarChartWithGrouping(List<PivotDataService.PivotResult> data, String groupBy) {
        barChart.getData().clear();

        if (data == null || data.isEmpty()) {
            barChart.setTitle("No data to display");
            return;
        }

        // Group data by the grouping column
        Map<String, List<PivotDataService.PivotResult>> groupedData = new LinkedHashMap<>();
        for (PivotDataService.PivotResult res : data) {
            groupedData.computeIfAbsent(res.getGroup(), k -> new ArrayList<>()).add(res);
        }

        // Find max value for axis scaling
        double maxValue = 0;
        for (List<PivotDataService.PivotResult> list : groupedData.values()) {
            for (PivotDataService.PivotResult res : list) {
                double val = res.getValue() != null ? res.getValue() : 0;
                if (val > maxValue) maxValue = val;
            }
        }

        List<String> groupKeys = new ArrayList<>(groupedData.keySet());
        for (int i = 0; i < Math.min(groupKeys.size(), 10); i++) {
            String group = groupKeys.get(i);
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(group);

            for (PivotDataService.PivotResult res : groupedData.get(group)) {
                double val = res.getValue() != null ? res.getValue() : 0;
                series.getData().add(new XYChart.Data<>(res.getGroup(), val));
            }

            if (!series.getData().isEmpty()) {
                barChart.getData().add(series);
            }
        }

        barChart.setTitle("Grouped Bar Chart: " + barChartCategoryCombo.getValue() + " by " + groupBy);

        // Configure Y axis with proper bounds
        NumberAxis yAxis = (NumberAxis) barChart.getYAxis();
        if (yAxis != null) {
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(0);
            double upperBound = maxValue * 1.1;
            if (upperBound < 10) upperBound = 10;
            yAxis.setUpperBound(upperBound);
            yAxis.setForceZeroInRange(true);
        }

        applyChartStyles(barChart);
    }

    // ================================================================
    // ===== PIE CHART =====
    // ================================================================

    @FXML
    public void handleUpdatePieChart() {
        if (dataController == null || dataController.getFilteredData() == null) {
            showError("Pie Chart Update", "No data loaded. Please load an Excel file first.");
            return;
        }
        var filteredData = dataController.getFilteredData();

        if (filteredData.isEmpty()) {
            showError("Pie Chart Update", "No data loaded.");
            return;
        }

        String category = pieChartCategoryCombo.getValue();
        String value = pieChartValueCombo.getValue();
        String aggregation = pieChartAggCombo.getValue();

        if (category == null || value == null || aggregation == null) {
            showError("Pie Chart Update", "Please select Category and Value fields.");
            return;
        }

        try {
            List<PivotDataService.PivotResult> pieData =
                    pivotService.calculatePivot(new ArrayList<>(filteredData), category, value, aggregation);
            updatePieChart(pieData);

            Platform.runLater(() -> {
                pieChart.layout();
                pieChart.requestLayout();
            });

        } catch (Exception e) {
            showError("Pie Chart Update", "Error updating pie chart: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===== FIX: Updated pie chart rendering =====
    private void updatePieChart(List<PivotDataService.PivotResult> data) {
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();

        if (data == null || data.isEmpty()) {
            pieChart.setData(FXCollections.observableArrayList());
            pieChart.setTitle("No data to display");
            return;
        }

        for (PivotDataService.PivotResult res : data) {
            double val = res.getValue() != null ? res.getValue() : 0;
            if (val > 0) {
                pieData.add(new PieChart.Data(res.getGroup(), val));
            }
        }

        pieChart.setData(pieData);
        pieChart.setTitle(pieChartAggCombo.getValue() + " of " + pieChartValueCombo.getValue() +
                " by " + pieChartCategoryCombo.getValue());

        // Ensure labels are visible
        pieChart.setLabelsVisible(true);
        pieChart.setClockwise(true);

        applyChartStyles(pieChart);
    }

    // ================================================================
    // ===== CLEAR METHODS =====
    // ================================================================

    @FXML
    public void handleClearBarChart() {
        barChartCategoryCombo.getSelectionModel().clearSelection();
        barChartValueCombo.getSelectionModel().clearSelection();
        barChartGroupCombo.getSelectionModel().select("None");
        barChart.getData().clear();
        barChart.setTitle("");

        // Reset axes
        NumberAxis yAxis = (NumberAxis) barChart.getYAxis();
        if (yAxis != null) {
            yAxis.setAutoRanging(true);
            yAxis.setForceZeroInRange(false);
            yAxis.setLowerBound(0);
        }

        Platform.runLater(() -> {
            barChart.layout();
            barChart.requestLayout();
        });
    }

    @FXML
    public void handleClearPieChart() {
        pieChartCategoryCombo.getSelectionModel().clearSelection();
        pieChartValueCombo.getSelectionModel().clearSelection();
        pieChart.getData().clear();
        pieChart.setTitle("");

        Platform.runLater(() -> {
            pieChart.layout();
            pieChart.requestLayout();
        });
    }

    private void updateCharts() {
        if (currentPivotResults != null && !currentPivotResults.isEmpty()) {
            // Reset axes before updating
            resetChartAxes();
            updateBarChart(currentPivotResults);
            updatePieChart(currentPivotResults);

            Platform.runLater(() -> {
                barChart.layout();
                barChart.requestLayout();
                pieChart.layout();
                pieChart.requestLayout();
            });
        }
    }

    // ================================================================
    // ===== STYLES =====
    // ================================================================

    private void applyChartStyles(PieChart chart) {
        if (chart.getData().isEmpty()) return;

        List<String> colorList = Arrays.asList("#003366", "#FF6600", "#006633", "#CC0000", "#660099", "#3399FF", "#FFCC00");

        Platform.runLater(() -> {
            int i = 0;
            for (PieChart.Data data : chart.getData()) {
                Node node = data.getNode();
                if (node != null) {
                    node.setStyle("-fx-pie-color: " + colorList.get(i % colorList.size()) + ";");
                }
                i++;
            }
        });
    }

    private void applyChartStyles(BarChart<String, Number> chart) {
        if (chart.getData().isEmpty()) return;

        List<String> colorList = Arrays.asList("#003366", "#FF6600", "#006633", "#CC0000", "#660099", "#3399FF", "#FFCC00");

        Platform.runLater(() -> {
            int seriesIndex = 0;
            for (XYChart.Series<String, Number> series : chart.getData()) {
                int j = 0;
                String color = colorList.get(seriesIndex % colorList.size());
                for (XYChart.Data<String, Number> data : series.getData()) {
                    Node node = data.getNode();
                    if (node != null) {
                        node.setStyle("-fx-bar-fill: " + color + ";");
                        j++;
                    }
                }
                seriesIndex++;
            }
        });
    }

    private <S> void setupAlternatingRows(TableView<S> table) {
        table.setRowFactory(tv -> new TableRow<S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (getIndex() % 2 == 0) {
                    setStyle("-fx-background-color: white;");
                } else {
                    setStyle("-fx-background-color: #f9f9f9;");
                }
            }
        });
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}