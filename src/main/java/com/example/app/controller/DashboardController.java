package com.example.app.controller;

import com.example.app.service.chartDashboard.DashboardExportService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Callback;

import java.io.File;
import java.util.ArrayList;
import java.util.Optional;

public class DashboardController {

    // ===== FXML FIELDS =====
    @FXML private VBox dashboardContainer;
    @FXML private ScrollPane dashboardScrollPane;

    // ===== DATA =====
    private final ObservableList<DashboardItem> dashboardItems = FXCollections.observableArrayList();
    private int dashboardItemCounter = 0;
    private final DashboardExportService dashboardExportService = new DashboardExportService();

    // ===== REFERENCE TO PARENT =====
    private ChartController chartController;

    // ===== INNER CLASS =====
    public static class DashboardItem {
        private final String type;
        private final String title;
        private final Node content;
        private final Node controlPanel;

        public DashboardItem(String type, String title, Node content, Node controlPanel) {
            this.type = type;
            this.title = title;
            this.content = content;
            this.controlPanel = controlPanel;
        }

        public String getType() { return type; }
        public String getTitle() { return title; }
        public Node getContent() { return content; }
        public Node getControlPanel() { return controlPanel; }
    }

    // ===== SETTERS =====
    public void setChartController(ChartController chartController) {
        this.chartController = chartController;
    }

    // ===== INITIALIZATION =====
    @FXML
    public void initialize() {
        dashboardContainer.setStyle("-fx-background-color: #f8f9fa;");
        refreshDashboard();
    }

    // ===== ADD TO DASHBOARD =====
    public void addPivotToDashboard(TableView<?> pivotTable, String rowGroup, String valueGroup, String aggType) {
        if (pivotTable == null || pivotTable.getItems().isEmpty()) {
            showError("Add to Dashboard", "Please update the pivot table first.");
            return;
        }

        String title = "Pivot Table: " + rowGroup + " vs " + valueGroup + " (" + aggType + ")";
        TableView<?> dashboardPivot = createClonedPivotTable(pivotTable);

        DashboardItem item = new DashboardItem("pivot", title, dashboardPivot, createRemoveButton());
        addToDashboard(item);
    }

    public void addBarChartToDashboard(BarChart<String, Number> barChart,
                                       String category, String value, String agg) {
        if (barChart == null || barChart.getData().isEmpty()) {
            showError("Add to Dashboard", "Please update the bar chart first.");
            return;
        }

        String title = "Bar Chart: " + category + " by " + agg + " of " + value;
        BarChart<String, Number> dashboardChart = createClonedBarChart(barChart);

        DashboardItem item = new DashboardItem("barchart", title, dashboardChart, createRemoveButton());
        addToDashboard(item);
    }

    public void addPieChartToDashboard(PieChart pieChart, String category, String value, String agg) {
        if (pieChart == null || pieChart.getData().isEmpty()) {
            showError("Add to Dashboard", "Please update the pie chart first.");
            return;
        }

        String title = "Pie Chart: " + category + " by " + agg + " of " + value;
        PieChart dashboardChart = createClonedPieChart(pieChart);

        DashboardItem item = new DashboardItem("piechart", title, dashboardChart, createRemoveButton());
        addToDashboard(item);
    }

    // ===== CLONE HELPERS =====
    @SuppressWarnings({"unchecked", "rawtypes"})
    private TableView<?> createClonedPivotTable(TableView<?> original) {
        TableView<Object> table = new TableView<>();
        table.setPrefHeight(250);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Clone columns - use raw types to avoid generic issues
        for (TableColumn<?, ?> col : original.getColumns()) {
            TableColumn<Object, Object> newCol = new TableColumn<>(col.getText());

            // Copy the cell value factory safely
            var factory = col.getCellValueFactory();
            if (factory != null) {
                // Use raw type to avoid generic mismatch
                newCol.setCellValueFactory((Callback) factory);
            }

            newCol.setPrefWidth(col.getPrefWidth());
            table.getColumns().add(newCol);
        }

        // Clone data
        table.setItems(FXCollections.observableArrayList(original.getItems()));
        return table;
    }

    private BarChart<String, Number> createClonedBarChart(BarChart<String, Number> original) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setPrefHeight(300);
        chart.setLegendVisible(true);

        for (XYChart.Series<String, Number> series : original.getData()) {
            XYChart.Series<String, Number> clonedSeries = new XYChart.Series<>();
            clonedSeries.setName(series.getName());
            for (XYChart.Data<String, Number> data : series.getData()) {
                clonedSeries.getData().add(new XYChart.Data<>(data.getXValue(), data.getYValue()));
            }
            chart.getData().add(clonedSeries);
        }
        chart.setTitle(original.getTitle());
        return chart;
    }

    private PieChart createClonedPieChart(PieChart original) {
        PieChart chart = new PieChart();
        chart.setAnimated(false);
        chart.setPrefHeight(300);
        chart.setLegendVisible(true);

        for (PieChart.Data data : original.getData()) {
            chart.getData().add(new PieChart.Data(data.getName(), data.getPieValue()));
        }
        chart.setTitle(original.getTitle());
        return chart;
    }

    // ===== DASHBOARD MANAGEMENT =====
    private Node createRemoveButton() {
        Button removeBtn = new Button("✕ Remove");
        removeBtn.setStyle("-fx-base: #dc3545; -fx-text-fill: white; -fx-font-size: 11px;");
        removeBtn.setPrefWidth(80);
        removeBtn.setOnAction(e -> {
            DashboardItem toRemove = null;
            for (DashboardItem item : dashboardItems) {
                if (item.getControlPanel() == removeBtn) {
                    toRemove = item;
                    break;
                }
            }
            if (toRemove != null) {
                dashboardItems.remove(toRemove);
                refreshDashboard();
            }
        });
        return removeBtn;
    }

    private void addToDashboard(DashboardItem item) {
        dashboardItems.add(item);
        refreshDashboard();
        showInfo("Added to Dashboard", "Item added to dashboard successfully.");
    }

    private void refreshDashboard() {
        dashboardContainer.getChildren().clear();

        if (dashboardItems.isEmpty()) {
            Label emptyLabel = new Label("No items added to dashboard yet. Use 'Add to Dashboard' buttons.");
            emptyLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #6c757d; -fx-wrap-text: true;");
            dashboardContainer.getChildren().add(emptyLabel);
            return;
        }

        for (DashboardItem item : dashboardItems) {
            VBox itemContainer = new VBox(5);
            itemContainer.setStyle(
                    "-fx-border-color: #dee2e6; -fx-border-width: 1; -fx-padding: 10; " +
                            "-fx-background-color: white; -fx-border-radius: 5; -fx-background-radius: 5;"
            );

            HBox header = new HBox(10);
            header.setAlignment(Pos.CENTER_LEFT);

            Label titleLabel = new Label(item.getTitle());
            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox controlBox = new HBox(5);
            controlBox.setAlignment(Pos.CENTER_RIGHT);
            controlBox.getChildren().add(item.getControlPanel());

            header.getChildren().addAll(titleLabel, spacer, controlBox);

            Node content = item.getContent();
            if (content instanceof TableView) {
                ((TableView<?>) content).setPrefHeight(250);
            } else if (content instanceof Chart) {
                ((Chart) content).setPrefHeight(300);
            }

            itemContainer.getChildren().addAll(header, content);
            dashboardContainer.getChildren().add(itemContainer);
        }
    }

    // ===== CLEAR DASHBOARD =====
    @FXML
    public void handleClearDashboard() {
        if (dashboardItems.isEmpty()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear Dashboard");
        confirm.setHeaderText("Clear all dashboard items?");
        confirm.setContentText("This action cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            dashboardItems.clear();
            refreshDashboard();
        }
    }

    // ===== EXPORT DASHBOARD =====
    @FXML
    public void handleExportDashboard() {
        if (dashboardItems.isEmpty()) {
            showError("Export Dashboard", "No items to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PowerPoint Files", "*.pptx"));
        fileChooser.setInitialFileName("dashboard_export.pptx");
        File file = fileChooser.showSaveDialog(null);

        if (file == null) return;

        try {
            showInfo("Exporting Dashboard", "Preparing dashboard export...");
            dashboardExportService.exportDashboardToPPTX(new ArrayList<>(dashboardItems), file);
            showInfo("Export Success", "Dashboard exported successfully to:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            showError("Export Dashboard Error", "Failed to export dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===== HELPERS =====
    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}