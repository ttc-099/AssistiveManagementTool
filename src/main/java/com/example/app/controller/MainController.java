package com.example.app.controller;

import com.example.app.model.*;
import com.example.app.service.*;
import com.example.app.service.DashboardExportService;
import com.example.app.service.GeographicValidationService.ValidationError;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.control.CheckListView;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javafx.scene.SnapshotParameters;
import javafx.scene.chart.*;
import javafx.scene.image.WritableImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;
import java.awt.image.BufferedImage;

public class MainController {

    @FXML private TabPane mainTabPane;

    // Pivot Table fields
    @FXML private ComboBox<String> pivotRowCombo;
    @FXML private ComboBox<String> pivotValueCombo;
    @FXML private ComboBox<String> pivotAggCombo;
    @FXML private TableView<PivotDataService.PivotResult> pivotTable;
    @FXML private TableColumn<PivotDataService.PivotResult, String> pivotGroupCol;
    @FXML private TableColumn<PivotDataService.PivotResult, Number> pivotValueCol;
    @FXML private VBox pivotFieldsContainer;

    // Bar Chart fields
    @FXML private ComboBox<String> barChartCategoryCombo;
    @FXML private ComboBox<String> barChartValueCombo;
    @FXML private ComboBox<String> barChartAggCombo;
    @FXML private ComboBox<String> barChartGroupCombo;

    // Pie Chart fields
    @FXML private ComboBox<String> pieChartCategoryCombo;
    @FXML private ComboBox<String> pieChartValueCombo;
    @FXML private ComboBox<String> pieChartAggCombo;

    @FXML private Label filePathLabel;
    @FXML private CheckComboBox<String> columnSelector;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private TextField searchField;
    @FXML private TableView<Map<String, String>> dataTable;
    @FXML private Label rowCountLabel;
    @FXML private Label colCountLabel;
    @FXML private Button reviewErrorsBtn;

    @FXML private ProgressBar aiProgressBar;
    @FXML private Label aiStatusLabel;
    @FXML private Button aiReviewBtn;
    @FXML private CheckBox aiAutoApplyCheck;

    @FXML private PieChart pieChart;
    @FXML private BarChart<String, Number> barChart;

    // Dashboard fields
    @FXML private VBox dashboardContainer;
    @FXML private ScrollPane dashboardScrollPane;

    private final DynamicImportService importService = new DynamicImportService();
    private final PowerPointExportService exportService = new PowerPointExportService();
    private final FilterStateRegistry filterRegistry = new FilterStateRegistry();
    private final GeographicValidationService geoValidationService = new GeographicValidationService();
    private final GroqAgentService groqService = new GroqAgentService();

    private AppDataModel model;
    private ObservableList<Map<String, String>> masterData = FXCollections.observableArrayList();
    private FilteredList<Map<String, String>> filteredData;
    private SortedList<Map<String, String>> sortedData;

    private List<ValidationError> currentErrors = new ArrayList<>();
    private final PivotDataService pivotService = new PivotDataService();
    private List<PivotDataService.PivotResult> currentPivotResults = new ArrayList<>();

    // Dashboard items
    private final ObservableList<DashboardItem> dashboardItems = FXCollections.observableArrayList();
    private int dashboardItemCounter = 0;
    private final DashboardExportService dashboardExportService = new DashboardExportService();

    // Inner class for dashboard items
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

    @FXML
    public void initialize() {
        // Initialize pivot aggregations
        pivotAggCombo.setItems(FXCollections.observableArrayList("Count", "Sum", "Average"));
        pivotAggCombo.getSelectionModel().select("Count");

        // Initialize bar chart aggregations
        barChartAggCombo.setItems(FXCollections.observableArrayList("Count", "Sum", "Average", "Min", "Max"));
        barChartAggCombo.getSelectionModel().select("Count");

        // Initialize pie chart aggregations
        pieChartAggCombo.setItems(FXCollections.observableArrayList("Count", "Sum", "Average", "Min", "Max"));
        pieChartAggCombo.getSelectionModel().select("Count");

        pivotGroupCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getGroup()));
        pivotValueCol.setCellValueFactory(d -> {
            Double val = d.getValue().getValue();
            return new javafx.beans.property.SimpleDoubleProperty(val);
        });

        columnSelector.getCheckModel().getCheckedItems().addListener((ListChangeListener<String>) c -> {
            updateTableColumns();
            updateMetadataLabels();
        });

        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyGlobalFilters());
        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyGlobalFilters());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyGlobalFilters());

        dataTable.setPlaceholder(new Label("No data loaded. Use 'Open File' to begin."));

        // Excel Styling: Alternating Row Colors
        setupAlternatingRows(dataTable);
        setupAlternatingRows(pivotTable);

        // Setup dashboard
        setupDashboard();

        // Disable chart update buttons initially
        // They will be enabled when data is loaded
    }

    private void setupDashboard() {
        dashboardContainer.setStyle("-fx-background-color: #f8f9fa;");
        refreshDashboard();
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

    // ============ HELP METHODS ============

    @FXML
    private void handlePivotHelp() {
        showHelpDialog("Pivot Table Guide",
                "Rows: Select the field you want to group your data by. Each unique value in this field becomes a row in the pivot table.\n\n" +
                        "Values: Select the field containing the numeric data you want to aggregate (sum, count, average, etc.).\n\n" +
                        "Summarize by: Choose how you want to aggregate the values:\n" +
                        "  • Count: Counts the number of records in each group\n" +
                        "  • Sum: Adds up all numeric values in each group\n" +
                        "  • Average: Calculates the average value in each group\n\n" +
                        "Tip: Use 'Add to Dashboard' to save this pivot table to your dashboard for later reference."
        );
    }

    @FXML
    private void handleBarChartHelp() {
        showHelpDialog("Bar Chart Guide",
                "Categories (X-Axis): Select the field that defines the categories/bars in your chart. Each unique value becomes a bar.\n\n" +
                        "Values (Y-Axis): Select the numeric field you want to visualize. This determines the height of each bar.\n\n" +
                        "Aggregation: Choose how to aggregate the values for each category:\n" +
                        "  • Count: Number of records per category\n" +
                        "  • Sum: Total sum of values per category\n" +
                        "  • Average: Average value per category\n" +
                        "  • Min: Minimum value per category\n" +
                        "  • Max: Maximum value per category\n\n" +
                        "Group By (Optional): Add a second dimension to create grouped bar charts. Each group gets its own color.\n\n" +
                        "Tip: Use 'Add to Dashboard' to save this chart to your dashboard."
        );
    }

    @FXML
    private void handlePieChartHelp() {
        showHelpDialog("Pie Chart Guide",
                "Categories (Slices): Select the field that defines the slices of your pie chart. Each unique value becomes a slice.\n\n" +
                        "Values: Select the numeric field you want to visualize. This determines the size of each slice.\n\n" +
                        "Aggregation: Choose how to aggregate the values for each category:\n" +
                        "  • Count: Number of records per category\n" +
                        "  • Sum: Total sum of values per category\n" +
                        "  • Average: Average value per category\n" +
                        "  • Min: Minimum value per category\n" +
                        "  • Max: Maximum value per category\n\n" +
                        "Tip: Use 'Add to Dashboard' to save this chart to your dashboard."
        );
    }

    private void showHelpDialog(String title, String content) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Help: " + title);
        dialog.setHeaderText(title);

        javafx.scene.text.Text text = new javafx.scene.text.Text(content);
        text.setWrappingWidth(450);
        javafx.scene.text.TextFlow textFlow = new javafx.scene.text.TextFlow(text);
        textFlow.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(textFlow);
        dialog.getDialogPane().setPrefWidth(500);

        ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(closeButton);

        dialog.showAndWait();
    }

    // ============ DASHBOARD METHODS ============

    @FXML
    private void handleAddPivotToDashboard() {
        if (pivotTable.getItems().isEmpty()) {
            showError("Add to Dashboard", "Please update the pivot table first before adding to dashboard.");
            return;
        }

        String title = "Pivot Table: " + pivotRowCombo.getValue() + " vs " + pivotValueCombo.getValue() +
                " (" + pivotAggCombo.getValue() + ")";

        TableView<PivotDataService.PivotResult> dashboardPivot = createClonedPivotTable();
        dashboardPivot.setItems(FXCollections.observableArrayList(pivotTable.getItems()));

        String uniqueId = "pivot_" + (++dashboardItemCounter);
        DashboardItem item = new DashboardItem("pivot", title, dashboardPivot, createRemoveButton(uniqueId));
        addToDashboard(item);
    }

    @FXML
    private void handleAddBarChartToDashboard() {
        if (barChart.getData().isEmpty()) {
            showError("Add to Dashboard", "Please update the bar chart first before adding to dashboard.");
            return;
        }

        String title = "Bar Chart: " + barChartCategoryCombo.getValue() + " by " +
                barChartAggCombo.getValue() + " of " + barChartValueCombo.getValue();

        BarChart<String, Number> dashboardChart = createClonedBarChart();
        dashboardChart.setData(cloneChartData(barChart.getData()));
        dashboardChart.setTitle(title);
        dashboardChart.setPrefHeight(300);
        dashboardChart.setLegendVisible(true);

        String uniqueId = "barchart_" + (++dashboardItemCounter);
        DashboardItem item = new DashboardItem("barchart", title, dashboardChart, createRemoveButton(uniqueId));
        addToDashboard(item);
    }

    @FXML
    private void handleAddPieChartToDashboard() {
        if (pieChart.getData().isEmpty()) {
            showError("Add to Dashboard", "Please update the pie chart first before adding to dashboard.");
            return;
        }

        String title = "Pie Chart: " + pieChartCategoryCombo.getValue() + " by " +
                pieChartAggCombo.getValue() + " of " + pieChartValueCombo.getValue();

        PieChart dashboardChart = createClonedPieChart();
        dashboardChart.setData(clonePieChartData(pieChart.getData()));
        dashboardChart.setTitle(title);
        dashboardChart.setPrefHeight(300);
        dashboardChart.setLegendVisible(true);

        String uniqueId = "piechart_" + (++dashboardItemCounter);
        DashboardItem item = new DashboardItem("piechart", title, dashboardChart, createRemoveButton(uniqueId));
        addToDashboard(item);
    }

    private TableView<PivotDataService.PivotResult> createClonedPivotTable() {
        TableView<PivotDataService.PivotResult> table = new TableView<>();
        table.setPrefHeight(250);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<PivotDataService.PivotResult, String> groupCol = new TableColumn<>("Category");
        groupCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getGroup()));
        groupCol.setPrefWidth(200);

        TableColumn<PivotDataService.PivotResult, Number> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(d -> new javafx.beans.property.SimpleDoubleProperty(d.getValue().getValue()));
        valueCol.setPrefWidth(150);

        table.getColumns().addAll(groupCol, valueCol);
        setupAlternatingRows(table);

        return table;
    }

    private BarChart<String, Number> createClonedBarChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setPrefHeight(300);
        return chart;
    }

    private PieChart createClonedPieChart() {
        PieChart chart = new PieChart();
        chart.setAnimated(false);
        chart.setPrefHeight(300);
        return chart;
    }

    private ObservableList<XYChart.Series<String, Number>> cloneChartData(ObservableList<XYChart.Series<String, Number>> originalData) {
        ObservableList<XYChart.Series<String, Number>> clonedData = FXCollections.observableArrayList();
        for (XYChart.Series<String, Number> series : originalData) {
            XYChart.Series<String, Number> clonedSeries = new XYChart.Series<>();
            clonedSeries.setName(series.getName());
            for (XYChart.Data<String, Number> data : series.getData()) {
                clonedSeries.getData().add(new XYChart.Data<>(data.getXValue(), data.getYValue()));
            }
            clonedData.add(clonedSeries);
        }
        return clonedData;
    }

    private ObservableList<PieChart.Data> clonePieChartData(ObservableList<PieChart.Data> originalData) {
        ObservableList<PieChart.Data> clonedData = FXCollections.observableArrayList();
        for (PieChart.Data data : originalData) {
            clonedData.add(new PieChart.Data(data.getName(), data.getPieValue()));
        }
        return clonedData;
    }

    private Node createRemoveButton(String uniqueId) {
        Button removeBtn = new Button("✕ Remove");
        removeBtn.setStyle("-fx-base: #dc3545; -fx-text-fill: white; -fx-font-size: 11px;");
        removeBtn.setPrefWidth(80);
        removeBtn.setOnAction(e -> {
            DashboardItem toRemove = null;
            for (DashboardItem item : dashboardItems) {
                if (item.getControlPanel() == removeBtn || item.getControlPanel().equals(removeBtn)) {
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
        // Switch to dashboard tab
        for (Tab tab : mainTabPane.getTabs()) {
            if ("Dashboard".equals(tab.getText())) {
                mainTabPane.getSelectionModel().select(tab);
                break;
            }
        }
        showInfo("Added to Dashboard", "Item added to dashboard successfully.");
    }

    private void refreshDashboard() {
        dashboardContainer.getChildren().clear();

        if (dashboardItems.isEmpty()) {
            Label emptyLabel = new Label("No items added to dashboard yet. Use 'Add to Dashboard' buttons in the Pivot Table, Bar Chart, or Pie Chart tabs.");
            emptyLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #6c757d; -fx-wrap-text: true;");
            dashboardContainer.getChildren().add(emptyLabel);
            return;
        }

        for (DashboardItem item : dashboardItems) {
            VBox itemContainer = new VBox(5);
            itemContainer.setStyle("-fx-border-color: #dee2e6; -fx-border-width: 1; -fx-padding: 10; -fx-background-color: white; -fx-border-radius: 5; -fx-background-radius: 5;");

            // Header with title and remove button
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

            // Content
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

    @FXML
    private void handleClearDashboard() {
        if (dashboardItems.isEmpty()) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear Dashboard");
        confirm.setHeaderText("Clear all dashboard items?");
        confirm.setContentText("This will remove all items from your dashboard. This action cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            dashboardItems.clear();
            refreshDashboard();
        }
    }

    @FXML
    private void handleExportDashboard() {
        if (dashboardItems.isEmpty()) {
            showError("Export Dashboard", "No items to export. Add some items to the dashboard first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PowerPoint Files", "*.pptx"));
        fileChooser.setInitialFileName("dashboard_export.pptx");
        File file = fileChooser.showSaveDialog(null);

        if (file == null) return;

        try {
            // Show progress indicator
            showInfo("Exporting Dashboard", "Preparing dashboard export with " + dashboardItems.size() + " items...");

            // Export using the service
            dashboardExportService.exportDashboardToPPTX(new ArrayList<>(dashboardItems), file);

            showInfo("Export Success", "Dashboard exported successfully to:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            showError("Export Dashboard Error", "Failed to export dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ============ EXISTING METHODS (unchanged below) ============

    @FXML
    private void handleOpenFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Data Files", "*.csv", "*.xlsx")
        );
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                dataTable.getColumns().clear();

                model = importService.importFile(file);
                filePathLabel.setText(file.getAbsolutePath());

                columnSelector.getItems().setAll(model.getHeaders());
                columnSelector.getCheckModel().checkAll();

                populatePivotFieldList();

                // Populate all comboboxes with headers
                List<String> headers = model.getHeaders();
                pivotRowCombo.getItems().setAll(headers);
                pivotValueCombo.getItems().setAll(headers);

                barChartCategoryCombo.getItems().setAll(headers);
                barChartValueCombo.getItems().setAll(headers);
                barChartGroupCombo.getItems().setAll(headers);
                barChartGroupCombo.getItems().add(0, "None");
                barChartGroupCombo.getSelectionModel().select("None");

                pieChartCategoryCombo.getItems().setAll(headers);
                pieChartValueCombo.getItems().setAll(headers);

                if (!headers.isEmpty()) {
                    pivotRowCombo.getSelectionModel().select(0);
                    pivotValueCombo.getSelectionModel().select(0);

                    barChartCategoryCombo.getSelectionModel().select(0);
                    barChartValueCombo.getSelectionModel().select(1);

                    pieChartCategoryCombo.getSelectionModel().select(0);
                    pieChartValueCombo.getSelectionModel().select(1);
                }

                masterData.setAll(model.getRawData());
                filteredData = new FilteredList<>(masterData, p -> true);

                sortedData = new SortedList<>(filteredData);
                sortedData.comparatorProperty().bind(dataTable.comparatorProperty());
                dataTable.setItems(sortedData);

                filterRegistry.clear();
                applyGlobalFilters();
                updateTableColumns();
                autoSizeColumns(dataTable);
                updateMetadataLabels();

                performValidation();

                Label emptyLabel = new Label("No records match the active criteria. Click 'Refresh View' or clear filters to reset.");
                dataTable.setPlaceholder(emptyLabel);

            } catch (Exception e) {
                showError("File Import Error", "Could not read file: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void populatePivotFieldList() {
        pivotFieldsContainer.getChildren().clear();
        for (String header : model.getHeaders()) {
            CheckBox cb = new CheckBox(header);
            cb.setOnAction(e -> {
                if (cb.isSelected()) {
                    // Logic to add to pivot can be more complex,
                    // for now we just allow selecting via combos as before
                }
            });
            pivotFieldsContainer.getChildren().add(cb);
        }
    }

    @FXML
    private void handleAIReview() {
        if (masterData.isEmpty()) {
            showError("AI Review", "No data loaded to review.");
            return;
        }

        aiProgressBar.setVisible(true);
        aiProgressBar.setProgress(0);
        aiStatusLabel.setVisible(true);
        aiStatusLabel.setText("Starting AI Review...");
        aiReviewBtn.setDisable(true);

        javafx.concurrent.Task<Void> aiAgentTask = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                var rows = masterData;
                int totalRows = rows.size();

                for (int i = 0; i < totalRows; i++) {
                    if (isCancelled()) break;
                    final int rowIndex = i;
                    Map<String, String> row = rows.get(rowIndex);

                    updateMessage("Reviewing row " + (rowIndex + 1) + " of " + totalRows);
                    updateProgress(rowIndex + 1, totalRows);

                    try {
                        Map<String, String> corrections = groqService.suggestCorrections(row).get();

                        if (corrections != null && !corrections.isEmpty()) {
                            System.out.println(">>> AI SUCCESS: Applied corrections to Row " + rowIndex);
                            corrections.forEach((key, newVal) -> {
                                String oldVal = row.getOrDefault(key, "[EMPTY]");
                                System.out.println("   -> Field [" + key + "]: '" + oldVal + "' changed to '" + newVal + "'");
                            });

                            Platform.runLater(() -> {
                                row.putAll(corrections);
                                dataTable.refresh();
                                autoSizeColumns(dataTable);
                            });
                        } else {
                            System.out.println(">>> AI REVIEW: Row " + rowIndex + " checked. No corrections required.");
                        }
                    } catch (Exception e) {
                        System.err.println(">>> AI ERROR: Execution failed for Row " + rowIndex + " -> " + e.getMessage());
                    }

                    Thread.sleep(1000);
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

    private void performValidation() {
        currentErrors = geoValidationService.validate(masterData);
        if (!currentErrors.isEmpty()) {
            reviewErrorsBtn.setVisible(true);
            reviewErrorsBtn.setText("Review Data Errors (" + currentErrors.size() + ")");
        } else {
            reviewErrorsBtn.setVisible(false);
        }
    }

    @FXML
    private void handleReviewErrors() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/app/view/ErrorReviewView.fxml"));
            Parent root = loader.load();
            ErrorReviewController controller = loader.getController();
            controller.setErrors(currentErrors);

            Stage stage = new Stage();
            stage.setTitle("Review Data Errors");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isAccepted()) {
                applyCorrections(controller.getSelectedCorrections());
                performValidation();
                dataTable.refresh();
                updateMetadataLabels();
            }
        } catch (Exception e) {
            showError("Review Error", "Could not open review dialog: " + e.getMessage());
        }
    }

    private void applyCorrections(List<ValidationError> selectedCorrections) {
        for (ValidationError error : selectedCorrections) {
            Map<String, String> row = error.row();
            String suggestion = error.suggestion();
            if (suggestion.startsWith("Move '") && suggestion.contains("' to ")) {
                String targetHeader = suggestion.substring(suggestion.lastIndexOf(" ") + 1);
                row.put(targetHeader, error.value());
                row.put(error.header(), "");
            }
        }
    }

    private void updateTableColumns() {
        dataTable.getColumns().clear();
        List<String> selectedColumns = columnSelector.getCheckModel().getCheckedItems();
        for (String colName : selectedColumns) {
            TableColumn<Map<String, String>, String> column = new TableColumn<>(colName);
            column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOrDefault(colName, "")));

            setupColumnMenu(column, colName);

            dataTable.getColumns().add(column);
        }
        autoSizeColumns(dataTable);
    }

    private void autoSizeColumns(TableView<Map<String, String>> tableView) {
        Platform.runLater(() -> {
            final double MIN_COLUMN_WIDTH = 80.0;
            final double MAX_COLUMN_WIDTH = 300.0;

            for (TableColumn<Map<String, String>, ?> column : tableView.getColumns()) {
                double maxContentWidth = 0;

                String headerText = column.getText();
                if (headerText != null) {
                    javafx.scene.text.Text text = new javafx.scene.text.Text(headerText);
                    text.setFont(javafx.scene.text.Font.font("System", 13));
                    double headerWidth = text.getLayoutBounds().getWidth() + 20.0;
                    maxContentWidth = Math.max(maxContentWidth, headerWidth);
                }

                int rowCount = 0;
                for (Map<String, String> row : tableView.getItems()) {
                    if (rowCount++ > 100) break;
                    Object cellValue = column.getCellData(row);
                    if (cellValue != null && !cellValue.toString().isEmpty()) {
                        javafx.scene.text.Text text = new javafx.scene.text.Text(cellValue.toString());
                        text.setFont(javafx.scene.text.Font.font("System", 13));
                        double cellWidth = text.getLayoutBounds().getWidth() + 25.0;
                        maxContentWidth = Math.max(maxContentWidth, cellWidth);
                    }
                }

                if (maxContentWidth == 0) {
                    maxContentWidth = 120.0;
                }

                double calculatedWidth = Math.min(
                        Math.max(MIN_COLUMN_WIDTH, maxContentWidth + 20.0),
                        MAX_COLUMN_WIDTH
                );

                column.setMinWidth(MIN_COLUMN_WIDTH);
                column.setPrefWidth(calculatedWidth);
                column.setMaxWidth(MAX_COLUMN_WIDTH);
            }
        });
    }

    private void setupColumnMenu(TableColumn<Map<String, String>, String> column, String colName) {
        Label headerLabel = new Label(colName);
        if (filterRegistry.hasFilter(colName)) {
            headerLabel.setText(colName + " \u25BD");
            headerLabel.setStyle("-fx-text-fill: #0078d4; -fx-font-weight: bold;");
        }
        column.setGraphic(headerLabel);
        column.setText("");

        MenuItem sortAsc = new MenuItem("Sort A to Z");
        sortAsc.setOnAction(e -> {
            column.setSortType(TableColumn.SortType.ASCENDING);
            if (!dataTable.getSortOrder().contains(column)) {
                dataTable.getSortOrder().add(column);
            }
        });

        MenuItem sortDesc = new MenuItem("Sort Z to A");
        sortDesc.setOnAction(e -> {
            column.setSortType(TableColumn.SortType.DESCENDING);
            if (!dataTable.getSortOrder().contains(column)) {
                dataTable.getSortOrder().add(column);
            }
        });

        MenuItem clearFilter = new MenuItem("Clear Filter from [" + colName + "]");
        clearFilter.setOnAction(e -> {
            filterRegistry.clearColumn(colName);
            applyCombinedFilters();
            updateTableColumns();
        });

        CustomMenuItem uniqueValuesItem = createUniqueValueChecklist(colName);
        Menu advancedFilters = createAdvancedFiltersMenu(colName);

        ContextMenu contextMenu = new ContextMenu(sortAsc, sortDesc, new SeparatorMenuItem(), clearFilter, new SeparatorMenuItem(), uniqueValuesItem, new SeparatorMenuItem(), advancedFilters);

        column.getGraphic().setOnMouseClicked(e -> {
            contextMenu.show(column.getGraphic(), e.getScreenX(), e.getScreenY());
        });
    }

    private CustomMenuItem createUniqueValueChecklist(String colName) {
        Set<String> uniqueValues = masterData.stream()
                .map(row -> row.getOrDefault(colName, ""))
                .collect(Collectors.toCollection(TreeSet::new));

        CheckListView<String> checkListView = new CheckListView<>(FXCollections.observableArrayList(uniqueValues));
        checkListView.setPrefHeight(200);

        CheckBox selectAll = new CheckBox("(Select All)");
        selectAll.setSelected(true);
        selectAll.setOnAction(e -> {
            if (selectAll.isSelected()) checkListView.getCheckModel().checkAll();
            else checkListView.getCheckModel().clearChecks();
        });

        checkListView.getCheckModel().checkAll();
        checkListView.getCheckModel().getCheckedItems().addListener((ListChangeListener<String>) c -> {
            List<String> checked = checkListView.getCheckModel().getCheckedItems();
            selectAll.setSelected(checked.size() == uniqueValues.size());

            filterRegistry.setPredicates(colName + "_unique", List.of(row -> checked.contains(row.getOrDefault(colName, ""))));
            applyCombinedFilters();
            updateTableColumns();
        });

        VBox vbox = new VBox(5, selectAll, checkListView);
        vbox.setPadding(new Insets(5));

        CustomMenuItem customMenuItem = new CustomMenuItem(vbox);
        customMenuItem.setHideOnClick(false);
        return customMenuItem;
    }

    private Menu createAdvancedFiltersMenu(String colName) {
        Menu menu = new Menu("Filters");

        boolean isNumeric = masterData.stream()
                .map(row -> row.getOrDefault(colName, ""))
                .filter(s -> !s.isEmpty())
                .limit(10)
                .allMatch(s -> s.matches("-?\\d+(\\.\\d+)?"));

        if (isNumeric) {
            menu.getItems().addAll(
                    createFilterDialogItem(colName, "Greater Than...", val -> row -> tryParseDouble(row.get(colName)) > tryParseDouble(val)),
                    createFilterDialogItem(colName, "Less Than...", val -> row -> tryParseDouble(row.get(colName)) < tryParseDouble(val)),
                    createBetweenFilterItem(colName)
            );
        } else {
            menu.getItems().addAll(
                    createFilterDialogItem(colName, "Equals...", val -> row -> row.getOrDefault(colName, "").equalsIgnoreCase(val)),
                    createFilterDialogItem(colName, "Contains...", val -> row -> row.getOrDefault(colName, "").toLowerCase().contains(val.toLowerCase())),
                    createFilterDialogItem(colName, "Begins With...", val -> row -> row.getOrDefault(colName, "").toLowerCase().startsWith(val.toLowerCase())),
                    createFilterDialogItem(colName, "Ends With...", val -> row -> row.getOrDefault(colName, "").toLowerCase().endsWith(val.toLowerCase()))
            );
        }
        return menu;
    }

    private MenuItem createFilterDialogItem(String colName, String label, java.util.function.Function<String, Predicate<Map<String, String>>> predicateFactory) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(e -> {
            String result = showInputDialog(label, "Enter criteria for " + colName + ":");
            if (result != null) {
                filterRegistry.addPredicate(colName, predicateFactory.apply(result));
                applyCombinedFilters();
                updateTableColumns();
            }
        });
        return item;
    }

    private MenuItem createBetweenFilterItem(String colName) {
        MenuItem item = new MenuItem("Between...");
        item.setOnAction(e -> {
            String min = showInputDialog("Between", "Enter Minimum:");
            String max = showInputDialog("Between", "Enter Maximum:");
            if (min != null && max != null) {
                filterRegistry.addPredicate(colName, row -> {
                    double val = tryParseDouble(row.get(colName));
                    return val >= tryParseDouble(min) && val <= tryParseDouble(max);
                });
                applyCombinedFilters();
                updateTableColumns();
            }
        });
        return item;
    }

    private String showInputDialog(String title, String header) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        return dialog.showAndWait().orElse(null);
    }

    private double tryParseDouble(String val) {
        try { return Double.parseDouble(val); } catch (Exception e) { return 0; }
    }

    private void applyGlobalFilters() {
        if (filteredData == null) return;

        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        String searchText = searchField.getText().toLowerCase();

        List<Predicate<Map<String, String>>> globals = new ArrayList<>();

        if (start != null || end != null) {
            globals.add(row -> checkDateRange(row, start, end));
        }

        if (!searchText.isEmpty()) {
            globals.add(row -> row.values().stream().anyMatch(v -> v.toLowerCase().contains(searchText)));
        }

        filterRegistry.setPredicates("__global__", globals);
        applyCombinedFilters();
    }

    private void applyCombinedFilters() {
        if (filteredData != null) {
            filteredData.setPredicate(filterRegistry.getCombinedPredicate());
            updateMetadataLabels();
        }
    }

    private boolean checkDateRange(Map<String, String> row, LocalDate start, LocalDate end) {
        for (String val : row.values()) {
            try {
                LocalDate date = parseDate(val);
                if (date != null) {
                    if (start != null && date.isBefore(start)) return false;
                    if (end != null && date.isAfter(end)) return false;
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private LocalDate parseDate(String val) {
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );
        for (DateTimeFormatter fmt : formatters) {
            try {
                return LocalDate.parse(val, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private void updateMetadataLabels() {
        int rows = (filteredData != null) ? filteredData.size() : 0;
        int cols = columnSelector.getCheckModel().getCheckedItems().size();
        rowCountLabel.setText(rows + " rows loaded");
        colCountLabel.setText(cols + " columns selected");
    }

    @FXML
    private void handleUpdateDashboard() {
        if (filteredData == null) return;

        String rowGroup = pivotRowCombo.getValue();
        String valMetric = pivotValueCombo.getValue();
        String aggType = pivotAggCombo.getValue();

        if (rowGroup == null || valMetric == null || aggType == null) return;

        currentPivotResults = pivotService.calculatePivot(new ArrayList<>(filteredData), rowGroup, valMetric, aggType);
        pivotTable.getItems().setAll(currentPivotResults);

        updateCharts();
    }

    // New method for independent bar chart updates
    @FXML
    private void handleUpdateBarChart() {
        if (filteredData == null || filteredData.isEmpty()) {
            showError("Bar Chart Update", "No data loaded. Please load data first.");
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
            // Calculate pivot for bar chart
            List<PivotDataService.PivotResult> barData;

            if (groupBy != null && !"None".equals(groupBy) && !groupBy.equals(category)) {
                // Two-level grouping - create grouped bar chart
                barData = pivotService.calculatePivot(new ArrayList<>(filteredData), category, value, aggregation);
                updateBarChartWithGrouping(barData, groupBy);
            } else {
                // Simple bar chart
                barData = pivotService.calculatePivot(new ArrayList<>(filteredData), category, value, aggregation);
                updateBarChart(barData);
            }
        } catch (Exception e) {
            showError("Bar Chart Update", "Error updating bar chart: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateBarChart(List<PivotDataService.PivotResult> data) {
        barChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(barChartAggCombo.getValue() + " of " + barChartValueCombo.getValue());

        for (PivotDataService.PivotResult res : data) {
            series.getData().add(new XYChart.Data<>(res.getGroup(), res.getValue()));
        }

        barChart.getData().add(series);
        barChart.setTitle(barChartAggCombo.getValue() + " of " + barChartValueCombo.getValue() + " by " + barChartCategoryCombo.getValue());

        applyChartStyles(barChart);
    }

    private void updateBarChartWithGrouping(List<PivotDataService.PivotResult> data, String groupBy) {
        // Group the data by the groupBy field
        Map<String, List<PivotDataService.PivotResult>> groupedData = data.stream()
                .collect(Collectors.groupingBy(PivotDataService.PivotResult::getGroup));

        barChart.getData().clear();

        // For simplicity, create multiple series based on unique values in groupBy
        Set<String> groups = new HashSet<>();
        for (PivotDataService.PivotResult res : data) {
            groups.add(res.getGroup());
        }

        // Create a series for each group (limited to 10 for readability)
        List<String> groupList = new ArrayList<>(groups);
        for (int i = 0; i < Math.min(groupList.size(), 10); i++) {
            String group = groupList.get(i);
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(group);

            for (PivotDataService.PivotResult res : data) {
                if (res.getGroup().equals(group)) {
                    series.getData().add(new XYChart.Data<>(res.getGroup(), res.getValue()));
                }
            }

            if (!series.getData().isEmpty()) {
                barChart.getData().add(series);
            }
        }

        barChart.setTitle("Grouped Bar Chart: " + barChartCategoryCombo.getValue() + " by " + groupBy);
        applyChartStyles(barChart);
    }

    // New method for independent pie chart updates
    @FXML
    private void handleUpdatePieChart() {
        if (filteredData == null || filteredData.isEmpty()) {
            showError("Pie Chart Update", "No data loaded. Please load data first.");
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
        } catch (Exception e) {
            showError("Pie Chart Update", "Error updating pie chart: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updatePieChart(List<PivotDataService.PivotResult> data) {
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();

        for (PivotDataService.PivotResult res : data) {
            pieData.add(new PieChart.Data(res.getGroup(), res.getValue()));
        }

        pieChart.setData(pieData);
        pieChart.setTitle(pieChartAggCombo.getValue() + " of " + pieChartValueCombo.getValue() + " by " + pieChartCategoryCombo.getValue());

        applyChartStyles(pieChart);
    }

    // Clear methods for charts
    @FXML
    private void handleClearBarChart() {
        barChartCategoryCombo.getSelectionModel().clearSelection();
        barChartValueCombo.getSelectionModel().clearSelection();
        barChartGroupCombo.getSelectionModel().select("None");
        barChart.getData().clear();
        barChart.setTitle("");
    }

    @FXML
    private void handleClearPieChart() {
        pieChartCategoryCombo.getSelectionModel().clearSelection();
        pieChartValueCombo.getSelectionModel().clearSelection();
        pieChart.getData().clear();
        pieChart.setTitle("");
    }

    private void updateCharts() {
        // This method is now split into separate methods
        // but kept for backward compatibility with existing code
        updateBarChart(currentPivotResults);
        updatePieChart(currentPivotResults);
    }

    private void applyChartStyles(PieChart chart) {
        String[] colors = new String[7];
        colors[0] = "#003366";
        colors[1] = "#FF6600";
        colors[2] = "#006633";
        colors[3] = "#CC0000";
        colors[4] = "#660099";
        colors[5] = "#3399FF";
        colors[6] = "#FFCC00";
        int i = 0;
        for (PieChart.Data data : chart.getData()) {
            data.getNode().setStyle("-fx-pie-color: " + colors[i % colors.length] + ";");
            i++;
        }
    }

    private void applyChartStyles(BarChart<String, Number> chart) {
        if (chart.getData().isEmpty()) return;

        String[] colors = new String[7];
        colors[0] = "#003366";
        colors[1] = "#FF6600";
        colors[2] = "#006633";
        colors[3] = "#CC0000";
        colors[4] = "#660099";
        colors[5] = "#3399FF";
        colors[6] = "#FFCC00";

        for (XYChart.Series<String, Number> series : chart.getData()) {
            int j = 0;
            for (XYChart.Data<String, Number> data : series.getData()) {
                Node node = data.getNode();
                if (node != null) {
                    node.setStyle("-fx-bar-fill: " + colors[j % colors.length] + ";");
                    j++;
                }
            }
        }
    }

    @FXML
    private void handleExport() {
        if (filteredData == null || filteredData.isEmpty()) {
            showError("Export Error", "No data to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PowerPoint Files", "*.pptx"));
        fileChooser.setInitialFileName("dashboard_export.pptx");
        File file = fileChooser.showOpenDialog(null);

        if (file == null) return;

        try {
            Tab activeTab = mainTabPane.getSelectionModel().getSelectedItem();
            String tabText = activeTab != null ? activeTab.getText() : "";

            if ("Table".equals(tabText)) {
                List<String> selectedColumns = new ArrayList<>(columnSelector.getCheckModel().getCheckedItems());
                exportService.exportToPPTX(new ArrayList<>(filteredData), selectedColumns, file);
            } else if ("Pivot Table".equals(tabText)) {
                String title = pivotRowCombo.getValue() + " vs " + pivotValueCombo.getValue() + " (" + pivotAggCombo.getValue() + ")";
                exportService.exportPivotToPPTX(currentPivotResults, title, null, file);
            } else if ("Bar Chart".equals(tabText)) {
                byte[] chartImage = captureNodeSnapshot(barChart);
                String title = "Bar Chart - " + barChartCategoryCombo.getValue();
                exportService.exportPivotToPPTX(currentPivotResults, title, chartImage, file);
            } else if ("Pie Chart".equals(tabText)) {
                byte[] chartImage = captureNodeSnapshot(pieChart);
                String title = "Pie Chart - " + pieChartCategoryCombo.getValue();
                exportService.exportPivotToPPTX(currentPivotResults, title, chartImage, file);
            } else {
                List<String> selectedColumns = new ArrayList<>(columnSelector.getCheckModel().getCheckedItems());
                exportService.exportToPPTX(new ArrayList<>(filteredData), selectedColumns, file);
            }

            showInfo("Export Success", "Exported successfully to " + file.getName());
        } catch (Exception e) {
            showError("Export Error", "Failed to export: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private byte[] captureNodeSnapshot(Node node) throws Exception {
        WritableImage image = node.snapshot(new SnapshotParameters(), null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = reader.getColor(x, y);
                int argb = ((int) (c.getOpacity() * 255) << 24) |
                        ((int) (c.getRed() * 255) << 16) |
                        ((int) (c.getGreen() * 255) << 8) |
                        ((int) (c.getBlue() * 255));
                bufferedImage.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", baos);
        return baos.toByteArray();
    }

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

    @FXML
    private void handleRegionMassAdder() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/app/view/RegionMassAdderView.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Region Mass Adder");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root, 800, 550));
            stage.showAndWait();

        } catch (Exception e) {
            showError("Region Mass Adder", "Could not open Region Mass Adder: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleRegionAnalyser() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/app/view/RegionAnalyserView.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("2025/2026 Region Analyser");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root, 1100, 700));
            stage.showAndWait();

        } catch (Exception e) {
            showError("Region Analyser", "Could not open Region Analyser: " + e.getMessage());
            e.printStackTrace();
        }
    }
}