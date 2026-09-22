package com.example.app.controller;

import com.example.app.model.MultiSheetModel;
import com.example.app.service.*;
import com.example.app.service.aiUsages.QwenAgentService;
import com.example.app.service.chartDashboard.PowerPointExportService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // ===== FXML FIELDS =====
    @FXML private TabPane mainTabPane;
    @FXML private Label filePathLabel;

    // ===== ROOT NODES FROM INCLUDED FXML =====
    // These are kept for potential future use, but suppressed warnings
    @SuppressWarnings("unused")
    @FXML private VBox dataViewRoot;
    @SuppressWarnings("unused")
    @FXML private VBox chartViewRoot;
    @SuppressWarnings("unused")
    @FXML private VBox dashboardViewRoot;
    @SuppressWarnings("unused")
    @FXML private VBox aiChatViewRoot;

    // ===== CONTROLLERS =====
    @FXML private DataController dataViewController;
    @FXML private ChartController chartViewController;
    @FXML private DashboardController dashboardViewController;
    @FXML private AiChatController aiChatViewController;

    // ===== AI =====
    @FXML private ProgressBar aiProgressBar;
    @FXML private Label aiStatusLabel;
    @FXML private Button aiReviewBtn;

    // ===== SERVICES =====
    private final DynamicImportService importService = new DynamicImportService();
    private final QwenAgentService qwenService = new QwenAgentService();
    private final PowerPointExportService exportService = new PowerPointExportService();

    // ================================================================
    // INITIALIZATION
    // ================================================================

    @FXML
    public void initialize() {
        // Connect sub-controllers
        if (chartViewController != null) {
            chartViewController.setDataController(dataViewController);
        }
        if (dashboardViewController != null) {
            dashboardViewController.setChartController(chartViewController);
        }

        // Setup AI Chat
        if (aiChatViewController != null) {
            aiChatViewController.setDataController(dataViewController);
        }

        // Connect Database to DataController
        if (dataViewController != null) {
            dataViewController.setDatabaseManager(qwenService.getDatabaseManager());
        }

        // Check for cached data
        if (qwenService.hasCachedData() && aiChatViewController != null) {
            aiChatViewController.updateDatabaseStatus(
                    qwenService.getTrackedFileName(),
                    qwenService.getSheetRowCounts()
            );
        }
    }

    // ================================================================
    // FILE IMPORT
    // ================================================================

    @FXML
    private void handleOpenFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Data Files", "*.csv", "*.xlsx", "*.xls")
        );
        File file = fileChooser.showOpenDialog(null);

        if (file == null) return;

        try {
            MultiSheetModel multiSheetModel = importService.importFile(file);
            filePathLabel.setText(file.getAbsolutePath());

            List<String> sheetNames = multiSheetModel.getSheetNames();
            if (!sheetNames.isEmpty()) {
                String firstSheet = sheetNames.getFirst();  // Using getFirst() instead of get(0)
                dataViewController.setData(multiSheetModel, firstSheet);

                MultiSheetModel.SheetData activeSheet = multiSheetModel.getActiveSheet();
                if (activeSheet != null) {
                    chartViewController.updateComboboxes(activeSheet.getHeaders());
                }
                chartViewController.populatePivotFieldList(multiSheetModel);
            
                // Link DataController back to MainController so it can refresh if needed
                // (Assuming there might be a need for it later, or just to ensure it's fully initialized)
            }

            notifyAI(file, multiSheetModel);
            ingestToSQLite(file);

        } catch (Exception e) {
            showError("File Import Error", "Could not read file: " + e.getMessage());
            logger.error("File import error: {}", e.getMessage(), e);
        }
    }

    private void notifyAI(File file, MultiSheetModel model) {
        if (aiChatViewController == null) return;
        var filteredData = dataViewController.getFilteredData();
        if (filteredData != null && !filteredData.isEmpty()) {
            aiChatViewController.notifyFileLoaded(file.getName(), new ArrayList<>(filteredData));
        }
    }

    private void ingestToSQLite(File file) {
        if (file.getName().toLowerCase().endsWith(".xlsx") ||
                file.getName().toLowerCase().endsWith(".xls")) {
            qwenService.ingestExcelToDatabase(file)
                    .thenRun(() -> Platform.runLater(() -> {
                        if (aiChatViewController != null) {
                            aiChatViewController.updateDatabaseStatus(
                                    qwenService.getTrackedFileName(),
                                    qwenService.getSheetRowCounts()
                            );
                        }
                    }))
                    .exceptionally(throwable -> {
                        Platform.runLater(() -> {
                            showError("Database Import", "Failed to import data: " + throwable.getMessage());
                        });
                        return null;
                    });
        }
    }

    // ================================================================
    // DELEGATE METHODS (called from FXML)
    // ================================================================

    @FXML
    private void handleUpdateDashboard() {
        if (chartViewController != null) chartViewController.handleUpdateDashboard();
    }

    @FXML
    private void handleUpdateBarChart() {
        if (chartViewController != null) chartViewController.handleUpdateBarChart();
    }

    @FXML
    private void handleUpdatePieChart() {
        if (chartViewController != null) chartViewController.handleUpdatePieChart();
    }

    @FXML
    private void handleClearBarChart() {
        if (chartViewController != null) chartViewController.handleClearBarChart();
    }

    @FXML
    private void handleClearPieChart() {
        if (chartViewController != null) chartViewController.handleClearPieChart();
    }

    @FXML
    private void handleClearDashboard() {
        if (dashboardViewController != null) dashboardViewController.handleClearDashboard();
    }

    @FXML
    private void handleExportDashboard() {
        if (dashboardViewController != null) dashboardViewController.handleExportDashboard();
    }

    @FXML
    private void handleAddPivotToDashboard() {
        if (dashboardViewController != null && chartViewController != null) {
            dashboardViewController.addPivotToDashboard(
                    chartViewController.getPivotTable(),
                    chartViewController.getPivotRowCombo().getValue(),
                    chartViewController.getPivotValueCombo().getValue(),
                    chartViewController.getPivotAggCombo().getValue()
            );
        }
    }

    @FXML
    private void handleAddBarChartToDashboard() {
        if (dashboardViewController != null && chartViewController != null) {
            dashboardViewController.addBarChartToDashboard(
                    chartViewController.getBarChart(),
                    chartViewController.getBarChartCategoryCombo().getValue(),
                    chartViewController.getBarChartValueCombo().getValue(),
                    chartViewController.getBarChartAggCombo().getValue()
            );
        }
    }

    @FXML
    private void handleAddPieChartToDashboard() {
        if (dashboardViewController != null && chartViewController != null) {
            dashboardViewController.addPieChartToDashboard(
                    chartViewController.getPieChart(),
                    chartViewController.getPieChartCategoryCombo().getValue(),
                    chartViewController.getPieChartValueCombo().getValue(),
                    chartViewController.getPieChartAggCombo().getValue()
            );
        }
    }

    @FXML
    private void handleAIReview() {
        if (dataViewController == null || dataViewController.getFilteredData() == null ||
                dataViewController.getFilteredData().isEmpty()) {
            showError("AI Review", "No data loaded to review.");
            return;
        }

        aiProgressBar.setVisible(true);
        aiProgressBar.setProgress(0);
        aiStatusLabel.setVisible(true);
        aiStatusLabel.setText("Starting AI Review...");
        aiReviewBtn.setDisable(true);

        var masterData = dataViewController.getFilteredData();
        List<Map<String, String>> dataList = new ArrayList<>(masterData);

        javafx.concurrent.Task<Void> aiAgentTask = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() {
                List<Map<String, String>> rowsSnapshot = new ArrayList<>(dataList);
                int totalRows = rowsSnapshot.size();

                for (int i = 0; i < totalRows; i++) {
                    if (isCancelled()) break;
                    final int rowIndex = i;
                    Map<String, String> rowCopy = new HashMap<>(rowsSnapshot.get(rowIndex));

                    updateMessage("Reviewing row " + (rowIndex + 1) + " of " + totalRows);
                    updateProgress(rowIndex + 1, totalRows);

                    try {
                        Map<String, String> corrections = qwenService.suggestCorrections(rowCopy).get();

                        if (corrections != null && !corrections.isEmpty()) {
                            logger.info(">>> AI SUCCESS: Applied corrections to Row {}", rowIndex);
                            for (Map.Entry<String, String> entry : corrections.entrySet()) {
                                String oldVal = rowCopy.getOrDefault(entry.getKey(), "[EMPTY]");
                                logger.info("   -> Field [{}]: '{}' changed to '{}'",
                                        entry.getKey(), oldVal, entry.getValue());
                            }

                            Platform.runLater(() -> {
                                masterData.get(rowIndex).putAll(corrections);
                                dataViewController.getDataTable().refresh();
                            });
                        }
                    } catch (Exception e) {
                        logger.error(">>> AI ERROR: Row {} -> {}", rowIndex, e.getMessage());
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                return null;
            }
        };

        aiProgressBar.progressProperty().bind(aiAgentTask.progressProperty());
        aiStatusLabel.textProperty().bind(aiAgentTask.messageProperty());

        aiAgentTask.setOnSucceeded(e -> {
            aiStatusLabel.textProperty().unbind();
            aiStatusLabel.setText("AI Review Completed.");
            aiReviewBtn.setDisable(false);
            showInfo("AI Review", "The AI Agent has completed scanning and correcting your data.");
        });

        aiAgentTask.setOnFailed(e -> {
            aiStatusLabel.textProperty().unbind();
            aiStatusLabel.setText("AI Review Failed.");
            aiReviewBtn.setDisable(false);
            showError("AI Review Error", aiAgentTask.getException().getMessage());
        });

        Thread aiThread = new Thread(aiAgentTask);
        aiThread.setDaemon(true);
        aiThread.start();
    }

    // ================================================================
    // EXPORT
    // ================================================================

    @FXML
    private void handleExport() {
        var filteredData = dataViewController.getFilteredData();
        if (filteredData == null || filteredData.isEmpty()) {
            showError("Export Error", "No data to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PowerPoint Files", "*.pptx"));
        fileChooser.setInitialFileName("dashboard_export.pptx");
        File file = fileChooser.showSaveDialog(null);  // Changed from showOpenDialog to showSaveDialog

        if (file == null) return;

        try {
            Tab activeTab = mainTabPane.getSelectionModel().getSelectedItem();
            String tabText = activeTab != null ? activeTab.getText() : "";

            // Use reflection to get columnSelector - suppress warning
            var columnSelectorField = dataViewController.getClass().getDeclaredField("columnSelector");
            columnSelectorField.setAccessible(true);
            @SuppressWarnings("unchecked")
            CheckComboBox<String> selector = (CheckComboBox<String>) columnSelectorField.get(dataViewController);
            List<String> selectedColumns = new ArrayList<>(selector.getCheckModel().getCheckedItems());

            if ("Table".equals(tabText)) {
                exportService.exportToPPTX(new ArrayList<>(filteredData), selectedColumns, file);
            } else if ("Pivot & Charts".equals(tabText)) {
                String title = chartViewController.getPivotRowCombo().getValue() + " vs " +
                        chartViewController.getPivotValueCombo().getValue() + " (" +
                        chartViewController.getPivotAggCombo().getValue() + ")";
                exportService.exportPivotToPPTX(chartViewController.getCurrentPivotResults(), title, null, file);
            } else {
                exportService.exportToPPTX(new ArrayList<>(filteredData), selectedColumns, file);
            }

            showInfo("Export Success", "Exported successfully to " + file.getName());
        } catch (Exception e) {
            showError("Export Error", "Failed to export: " + e.getMessage());
            logger.error("Export error: {}", e.getMessage(), e);
        }
    }

    // ================================================================
    // TOOL HANDLERS
    // ================================================================

    @FXML
    private void handleRegionMassAdder() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/app/view/RegionMassAdderView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Region Mass Adder");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root, 800, 550));
            stage.showAndWait();
        } catch (Exception e) {
            showError("Region Mass Adder", "Could not open Region Mass Adder: " + e.getMessage());
            logger.error("Region Mass Adder error: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void handleRegionAnalyser() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/app/view/RegionAnalyserView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("2025/2026 Region Analyser");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root, 1100, 700));
            stage.showAndWait();
        } catch (Exception e) {
            showError("Region Analyser", "Could not open Region Analyser: " + e.getMessage());
            logger.error("Region Analyser error: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void handleUAListUpdater() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/app/view/UAListUpdaterView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("UA List Updater");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root, 1000, 700));
            stage.showAndWait();
        } catch (Exception e) {
            showError("UA List Updater", "Could not open UA List Updater: " + e.getMessage());
            logger.error("UA List Updater error: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void handleInvoiceListUpdater() {
        showInfo("Invoice List Updater", "The Invoice List Updater tool will be available in a future update.");
    }

    @FXML
    private void handleCountryAnalyser() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/app/view/CountryAnalyserView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("🌍 Country Analyser");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root, 1000, 600));
            stage.showAndWait();
        } catch (Exception e) {
            showError("Country Analyser", "Could not open Country Analyser: " + e.getMessage());
            logger.error("Country Analyser error: {}", e.getMessage(), e);
        }
    }

    // ================================================================
    // HELPERS
    // ================================================================

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

    public DataController getDataController() {
        return dataViewController;
    }

    @FXML
    private void handleReviewErrors() {
        if (dataViewController == null || dataViewController.getFilteredData() == null) {
            showError("Review Errors", "No data loaded.");
            return;
        }

        var filteredData = dataViewController.getFilteredData();
        if (filteredData.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Review Errors");
            alert.setHeaderText("No errors to review");
            alert.setContentText("All data appears to be valid.");
            alert.showAndWait();
            return;
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Review Errors");
        alert.setHeaderText("Data Validation");
        alert.setContentText("Total rows: " + filteredData.size() +
                "\n\nUse the 'AI Review' button to scan for corrections.");
        alert.showAndWait();
    }
}