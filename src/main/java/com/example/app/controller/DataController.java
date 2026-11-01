package com.example.app.controller;

import com.example.app.model.FilterStateRegistry;
import com.example.app.model.MultiSheetModel;
import com.example.app.service.aiUsages.QwenAgentService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.control.CheckListView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DataController {

    // ===== FXML FIELDS =====
    @FXML private CheckComboBox<String> columnSelector;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private TextField searchField;
    @FXML private TableView<Map<String, String>> dataTable;
    @FXML private Label rowCountLabel;
    @FXML private Label colCountLabel;

    // Sheet selector
    @FXML private ComboBox<String> sheetSelector;
    @FXML private Label sheetStatusLabel;
    @FXML private Label rowStatusLabel;
    @FXML private Label colStatusLabel;
    @FXML private Label saveStatusLabel;
    @FXML private Label bottomRowCountLabel;
    @FXML private Label bottomColCountLabel;

    // Select All / Deselect All
    @FXML private CheckBox selectAllColumnsCheckBox;

    // ===== SERVICES =====
    // shared singleton: ONE model in RAM, loaded async — never construct per-controller
    private final QwenAgentService qwenService = QwenAgentService.getInstance();

    // ===== DATA =====
    private MultiSheetModel multiSheetModel;
    private String currentSheet;
    private ObservableList<Map<String, String>> masterData = FXCollections.observableArrayList();
    private FilteredList<Map<String, String>> filteredData;
    private SortedList<Map<String, String>> sortedData;
    private final FilterStateRegistry filterRegistry = new FilterStateRegistry();
    private com.example.app.db.DatabaseManager dbManager;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Set<String> modifiedRowIds = new HashSet<>();

    // ===== FIX: Flag to prevent recursive updates =====
    private boolean isUpdatingColumns = false;
    // ===== FIX: Flag to prevent paste recursion =====
    private boolean isPasting = false;

    // ===== EDITING SUPPORT =====
    private boolean isEditingEnabled = true;
    private final Clipboard systemClipboard = Clipboard.getSystemClipboard();

    // ================================================================
    // SETTERS
    // ================================================================

    public void setData(MultiSheetModel model, String sheetName) {
        this.multiSheetModel = model;
        modifiedRowIds.clear();
        if (saveStatusLabel != null) saveStatusLabel.setText("");

        if (model != null && sheetName != null) {
            isUpdatingColumns = true;
            try {
                List<String> sheetNames = model.getSheetNames();
                sheetSelector.getItems().setAll(sheetNames);
                sheetSelector.getSelectionModel().select(sheetName);
            } finally {
                isUpdatingColumns = false;
            }
            loadSheet(sheetName);
        }
    }

    public FilteredList<Map<String, String>> getFilteredData() {
        return filteredData;
    }

    public MultiSheetModel getMultiSheetModel() {
        return multiSheetModel;
    }

    public String getCurrentSheet() {
        return currentSheet;
    }

    public TableView<Map<String, String>> getDataTable() {
        return dataTable;
    }

    public void setDatabaseManager(com.example.app.db.DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void setEditingEnabled(boolean enabled) {
        this.isEditingEnabled = enabled;
        dataTable.setEditable(enabled);
    }

    // ================================================================
    // INITIALIZATION
    // ================================================================

    @FXML
    public void initialize() {
        // Column selector listener - check flag to prevent recursion
        columnSelector.getCheckModel().getCheckedItems().addListener((ListChangeListener<String>) c -> {
            if (!isUpdatingColumns) {
                updateTableColumns();
                updateMetadataLabels();
                updateSelectAllCheckBox();
            }
        });

        // Filter listeners
        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyGlobalFilters());
        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyGlobalFilters());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyGlobalFilters());

        // Sheet selector listener
        if (sheetSelector != null) {
            sheetSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.equals(oldVal)) {
                    handleSheetChange();
                }
            });
        }

        // Setup table
        dataTable.setPlaceholder(new Label("No data loaded. Use 'Open File' to begin."));
        setupAlternatingRows(dataTable);

        // Enable cell editing
        setupEditableTable();

        // Connect Database to DataController
        if (dbManager == null && qwenService != null) {
            dbManager = qwenService.getDatabaseManager();
        }

        // ===== FIX: Keyboard shortcuts for copy/paste =====
        setupKeyboardShortcuts();

        // Setup Select All checkbox
        if (selectAllColumnsCheckBox != null) {
            selectAllColumnsCheckBox.setSelected(true);
            selectAllColumnsCheckBox.setOnAction(e -> handleSelectAllColumns());
        }

        System.out.println("✅ DataController initialized");
    }

    // ================================================================
    // SELECT ALL / DESELECT ALL COLUMNS
    // ================================================================

    @FXML
    private void handleSelectAllColumns() {
        if (selectAllColumnsCheckBox == null || columnSelector == null) return;

        boolean selected = selectAllColumnsCheckBox.isSelected();
        if (selected) {
            columnSelector.getCheckModel().checkAll();
        } else {
            columnSelector.getCheckModel().clearChecks();
        }
    }

    private void updateSelectAllCheckBox() {
        if (selectAllColumnsCheckBox == null || columnSelector == null) return;

        int totalItems = columnSelector.getItems().size();
        int checkedItems = columnSelector.getCheckModel().getCheckedItems().size();

        if (totalItems == 0) {
            selectAllColumnsCheckBox.setIndeterminate(false);
            selectAllColumnsCheckBox.setSelected(false);
        } else if (checkedItems == totalItems) {
            selectAllColumnsCheckBox.setIndeterminate(false);
            selectAllColumnsCheckBox.setSelected(true);
        } else if (checkedItems == 0) {
            selectAllColumnsCheckBox.setIndeterminate(false);
            selectAllColumnsCheckBox.setSelected(false);
        } else {
            selectAllColumnsCheckBox.setIndeterminate(true);
        }
    }

    // ================================================================
    // CELL EDITING
    // ================================================================

    private void setupEditableTable() {
        dataTable.setEditable(true);
        dataTable.getSelectionModel().setCellSelectionEnabled(true);
        dataTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // ===== FIX: Request focus when clicked =====
        dataTable.setOnMouseClicked(event -> {
            dataTable.requestFocus();
        });

        // Direct typing to edit
        dataTable.setOnKeyPressed(event -> {
            if (isEditingEnabled && !event.isControlDown() && !event.isAltDown() &&
                    (event.getCode().isLetterKey() || event.getCode().isDigitKey() || event.getCode() == KeyCode.SPACE)) {

                TablePosition<Map<String, String>, ?> pos = dataTable.getFocusModel().getFocusedCell();
                if (pos != null && pos.getTableColumn() != null) {
                    dataTable.edit(pos.getRow(), pos.getTableColumn());
                }
            }
        });
    }

    /**
     * Create an editable cell for a TableColumn
     */
    private <T> Callback<TableColumn<Map<String, String>, T>, TableCell<Map<String, String>, T>>
    createEditableCellFactory() {
        return new Callback<TableColumn<Map<String, String>, T>, TableCell<Map<String, String>, T>>() {
            @Override
            public TableCell<Map<String, String>, T> call(TableColumn<Map<String, String>, T> param) {
                return new TableCell<Map<String, String>, T>() {
                    private final TextField textField = new TextField();

                    {
                        textField.setOnAction(e -> commitEdit());
                        textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                            if (!newVal && isEditing()) {
                                commitEdit();
                            }
                        });
                        textField.setOnKeyPressed(event -> {
                            if (event.getCode() == KeyCode.ESCAPE) {
                                cancelEdit();
                                event.consume();
                            } else if (event.getCode() == KeyCode.TAB) {
                                commitEdit();
                                TableView<Map<String, String>> table = getTableView();
                                int col = table.getColumns().indexOf(getTableColumn());
                                if (event.isShiftDown()) {
                                    if (col > 0) table.getSelectionModel().selectLeftCell();
                                } else {
                                    if (col < table.getColumns().size() - 1) table.getSelectionModel().selectRightCell();
                                }
                                event.consume();
                            } else if (event.getCode() == KeyCode.ENTER) {
                                commitEdit();
                                TableView<Map<String, String>> table = getTableView();
                                if (event.isShiftDown()) {
                                    table.getSelectionModel().selectAboveCell();
                                } else {
                                    table.getSelectionModel().selectBelowCell();
                                }
                                event.consume();
                            }
                        });
                    }

                    @Override
                    protected void updateItem(T item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                            setStyle("");
                        } else {
                            setText(item.toString());
                            setGraphic(null);
                            Map<String, String> row = getTableRow() != null ? getTableRow().getItem() : null;
                            if (row != null && modifiedRowIds.contains(row.get("_row_id"))) {
                                setStyle("-fx-background-color: #fff9c4;");
                            } else {
                                setStyle("");
                            }
                        }
                    }

                    @Override
                    public void startEdit() {
                        if (!isEditingEnabled) return;
                        super.startEdit();
                        String currentValue = getItem() != null ? getItem().toString() : "";
                        textField.setText(currentValue);
                        setText(null);
                        setGraphic(textField);
                        textField.requestFocus();
                        textField.selectAll();
                    }

                    @Override
                    public void cancelEdit() {
                        super.cancelEdit();
                        setText(getItem() != null ? getItem().toString() : "");
                        setGraphic(null);
                    }

                    @Override
                    public void commitEdit(T newValue) {
                        super.commitEdit(newValue);
                        updateCellInMasterData(newValue != null ? newValue.toString() : "");
                    }

                    private void commitEdit() {
                        if (!isEditing()) return;
                        String newValue = textField.getText();
                        @SuppressWarnings("unchecked")
                        T typedValue = (T) newValue;
                        commitEdit(typedValue);
                    }

                    private void updateCellInMasterData(String newValue) {
                        Map<String, String> row = getTableRow() != null ? getTableRow().getItem() : null;
                        if (row != null) {
                            String columnName = getTableColumn().getText();
                            String oldVal = row.get(columnName);
                            if (Objects.equals(oldVal, newValue)) return;

                            row.put(columnName, newValue);
                            String rowId = row.get("_row_id");
                            if (rowId != null) {
                                modifiedRowIds.add(rowId);
                                syncUpdateToDatabase(rowId, columnName, newValue);
                                setStyle("-fx-background-color: #fff9c4;");
                            }
                            getTableView().refresh();
                        }
                    }
                };
            }
        };
    }

    // ================================================================
    // COPY / PASTE SUPPORT
    // ================================================================

    private void setupKeyboardShortcuts() {
        // ===== FIX: Use scene-wide event filter =====
        dataTable.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.isControlDown() && event.getCode() == KeyCode.C) {
                        if (!dataTable.getSelectionModel().getSelectedCells().isEmpty()) {
                            handleCopy();
                            event.consume();
                        }
                    } else if (event.isControlDown() && event.getCode() == KeyCode.V) {
                        if (!dataTable.getSelectionModel().getSelectedCells().isEmpty()) {
                            handlePaste();
                            event.consume();
                        }
                    }
                });
            }
        });

        // Add to current scene if available
        Scene scene = dataTable.getScene();
        if (scene != null) {
            scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.C) {
                    if (!dataTable.getSelectionModel().getSelectedCells().isEmpty()) {
                        handleCopy();
                        event.consume();
                    }
                } else if (event.isControlDown() && event.getCode() == KeyCode.V) {
                    if (!dataTable.getSelectionModel().getSelectedCells().isEmpty()) {
                        handlePaste();
                        event.consume();
                    }
                }
            });
        }

        // Backup: direct key handler on table
        dataTable.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                if (!dataTable.getSelectionModel().getSelectedCells().isEmpty()) {
                    handleCopy();
                    event.consume();
                }
            } else if (event.isControlDown() && event.getCode() == KeyCode.V) {
                if (!dataTable.getSelectionModel().getSelectedCells().isEmpty()) {
                    handlePaste();
                    event.consume();
                }
            }
        });

        System.out.println("✅ Keyboard shortcuts (Ctrl+C, Ctrl+V) enabled for table");
    }

    @FXML
    private void handleCopy() {
        System.out.println("📋 Copy triggered!");

        ObservableList<TablePosition> selectedCells = dataTable.getSelectionModel().getSelectedCells();
        System.out.println("   Selected cells: " + selectedCells.size());

        if (selectedCells.isEmpty()) {
            showStatusMessage("No cells selected to copy");
            return;
        }

        StringBuilder clipboardContent = new StringBuilder();
        int currentRow = -1;

        List<TablePosition> sortedCells = new ArrayList<>(selectedCells);
        sortedCells.sort((a, b) -> {
            if (a.getRow() != b.getRow()) return Integer.compare(a.getRow(), b.getRow());
            return Integer.compare(a.getColumn(), b.getColumn());
        });

        for (TablePosition pos : sortedCells) {
            int row = pos.getRow();
            int col = pos.getColumn();

            if (row != currentRow && currentRow != -1) {
                clipboardContent.append("\n");
            }
            currentRow = row;

            Map<String, String> rowData = dataTable.getItems().get(row);
            if (rowData != null) {
                String columnName = dataTable.getColumns().get(col).getText();
                String value = rowData.getOrDefault(columnName, "");
                if (clipboardContent.length() > 0 && clipboardContent.charAt(clipboardContent.length() - 1) != '\n') {
                    clipboardContent.append("\t");
                }
                clipboardContent.append(value);
            }
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(clipboardContent.toString());
        systemClipboard.setContent(content);

        System.out.println("   Copied: '" + clipboardContent.toString() + "'");
        showStatusMessage("Copied " + selectedCells.size() + " cell(s) to clipboard");
    }

    @FXML
    private void handlePaste() {
        // Prevent recursive paste
        if (isPasting) return;
        isPasting = true;

        try {
            System.out.println("📋 Paste triggered!");

            if (!systemClipboard.hasString()) {
                showStatusMessage("Clipboard is empty or does not contain text");
                return;
            }

            String clipboardText = systemClipboard.getString();
            System.out.println("   Clipboard text: '" + clipboardText + "'");

            if (clipboardText == null || clipboardText.trim().isEmpty()) {
                showStatusMessage("Clipboard is empty");
                return;
            }

            ObservableList<TablePosition> selectedCells = dataTable.getSelectionModel().getSelectedCells();
            System.out.println("   Selected cells: " + selectedCells.size());

            if (selectedCells.isEmpty()) {
                showStatusMessage("Please select cells to paste into");
                return;
            }

            // Parse clipboard data
            String[] rows = clipboardText.split("\\R");
            String[][] values = new String[rows.length][];
            int maxCols = 0;

            for (int i = 0; i < rows.length; i++) {
                String row = rows[i];
                if (row == null || row.trim().isEmpty()) {
                    values[i] = new String[0];
                    continue;
                }
                values[i] = row.split("\t", -1);
                maxCols = Math.max(maxCols, values[i].length);
            }

            if (maxCols == 0) {
                showStatusMessage("No data to paste");
                return;
            }

            TablePosition anchor = selectedCells.get(0);
            int startRow = anchor.getRow();
            int startCol = dataTable.getColumns().indexOf(anchor.getTableColumn());
            if (startCol == -1) startCol = 0;

            List<Map<String, Object>> batch = new ArrayList<>();
            int pastedRows = 0;
            int pastedCols = 0;

            // Single value into multiple selected cells
            if (selectedCells.size() > 1 && values.length == 1 && values[0].length == 1) {
                String value = values[0][0];
                for (TablePosition pos : selectedCells) {
                    Map<String, String> rowData = dataTable.getItems().get(pos.getRow());
                    if (rowData == null) continue;

                    String colName = pos.getTableColumn().getText();
                    if (colName == null) continue;

                    String oldValue = rowData.get(colName);
                    if (Objects.equals(oldValue, value)) continue;

                    rowData.put(colName, value);
                    String rowId = rowData.get("_row_id");
                    if (rowId != null) {
                        modifiedRowIds.add(rowId);
                        batch.add(Map.of("id", rowId, "col", colName, "val", value));
                    }
                    pastedCols++;
                }
                showStatusMessage("Filled " + pastedCols + " cell(s) with same value");
            } else {
                // Block paste
                List<TablePosition> sortedSelection = new ArrayList<>(selectedCells);
                sortedSelection.sort((pos1, pos2) -> {
                    int rowCompare = Integer.compare(pos1.getRow(), pos2.getRow());
                    if (rowCompare != 0) return rowCompare;
                    return Integer.compare(pos1.getColumn(), pos2.getColumn());
                });

                TablePosition startPos = sortedSelection.get(0);
                startRow = startPos.getRow();
                startCol = dataTable.getColumns().indexOf(startPos.getTableColumn());
                if (startCol == -1) startCol = 0;

                int totalColumns = dataTable.getColumns().size();
                int pasteCols = Math.min(maxCols, totalColumns - startCol);

                for (int r = 0; r < values.length && r + startRow < dataTable.getItems().size(); r++) {
                    Map<String, String> rowData = dataTable.getItems().get(r + startRow);
                    if (rowData == null) continue;

                    String[] rowValues = values[r];
                    int colsToPaste = Math.min(rowValues.length, pasteCols);

                    for (int c = 0; c < colsToPaste; c++) {
                        TableColumn<Map<String, String>, ?> column = dataTable.getColumns().get(startCol + c);
                        String colName = column.getText();
                        if (colName == null) continue;

                        String value = rowValues[c] != null ? rowValues[c] : "";
                        String oldValue = rowData.get(colName);
                        if (Objects.equals(oldValue, value)) continue;

                        rowData.put(colName, value);
                        String rowId = rowData.get("_row_id");
                        if (rowId != null) {
                            modifiedRowIds.add(rowId);
                            batch.add(Map.of("id", rowId, "col", colName, "val", value));
                        }
                        pastedCols++;
                    }
                    pastedRows++;
                }

                showStatusMessage("Pasted " + pastedRows + " row(s), " + pastedCols + " cell(s)");
            }

            if (!batch.isEmpty()) {
                syncBatchToDatabase(batch);
            } else {
                showStatusMessage("No changes to save (values already match)");
            }

            dataTable.refresh();

        } catch (Exception e) {
            System.err.println("❌ Paste error: " + e.getMessage());
            e.printStackTrace();
            showStatusMessage("Error pasting data: " + e.getMessage());
            dataTable.refresh();
        } finally {
            isPasting = false;
        }
    }

    // ================================================================
    // DATABASE SYNC
    // ================================================================

    private void syncUpdateToDatabase(String rowId, String column, String value) {
        syncBatchToDatabase(List.of(Map.of("id", rowId, "col", column, "val", value)));
    }

    private void syncBatchToDatabase(List<Map<String, Object>> updates) {
        if (dbManager == null || currentSheet == null || updates.isEmpty()) {
            return;
        }

        Platform.runLater(() -> updateSaveStatus("Saving...", "#6c757d"));

        dbExecutor.submit(() -> {
            String tableName = sanitizeTableName(currentSheet);
            Connection conn = null;

            try {
                conn = dbManager.getConnection();
                conn.setAutoCommit(false);

                // Group updates by column for better batching
                Map<String, List<Map<String, Object>>> updatesByColumn = new HashMap<>();
                for (Map<String, Object> update : updates) {
                    String column = sanitizeColumnName((String) update.get("col"));
                    updatesByColumn.computeIfAbsent(column, k -> new ArrayList<>()).add(update);
                }

                int totalUpdates = updates.size();
                int executedUpdates = 0;

                for (Map.Entry<String, List<Map<String, Object>>> entry : updatesByColumn.entrySet()) {
                    String column = entry.getKey();
                    List<Map<String, Object>> columnUpdates = entry.getValue();

                    String sql = "UPDATE \"" + tableName + "\" SET \"" + column + "\" = ? WHERE _row_id = ?";

                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        int batchSize = 0;

                        for (Map<String, Object> update : columnUpdates) {
                            String value = (String) update.get("val");
                            String rowId = (String) update.get("id");

                            pstmt.setString(1, value != null ? value : "");
                            pstmt.setString(2, rowId);
                            pstmt.addBatch();
                            batchSize++;

                            if (batchSize >= 100) {
                                pstmt.executeBatch();
                                executedUpdates += batchSize;
                                batchSize = 0;
                                System.out.println("✅ Executed batch of 100 updates for column: " + column);
                            }
                        }

                        if (batchSize > 0) {
                            pstmt.executeBatch();
                            executedUpdates += batchSize;
                            System.out.println("✅ Executed final batch of " + batchSize + " updates for column: " + column);
                        }
                    }
                }

                conn.commit();
                System.out.println("✅ Database sync complete: " + totalUpdates + " updates");

                int finalExecuted = executedUpdates;
                Platform.runLater(() -> {
                    modifiedRowIds.clear();
                    updateSaveStatus("✅ Saved (" + finalExecuted + " updates)", "#28a745");
                    dataTable.refresh();
                });

            } catch (SQLException e) {
                System.err.println("❌ Database sync error: " + e.getMessage());
                e.printStackTrace();

                if (conn != null) {
                    try {
                        conn.rollback();
                        System.out.println("🔄 Transaction rolled back");
                    } catch (SQLException rollbackEx) {
                        System.err.println("⚠️ Rollback failed: " + rollbackEx.getMessage());
                    }
                }

                Platform.runLater(() -> {
                    updateSaveStatus("❌ Error", "#dc3545");
                    showError("Database Sync Error",
                            "Failed to update database: " + e.getMessage() +
                                    "\n\nYour changes are still visible in the table but may not be saved.\n" +
                                    "Try refreshing the data or check your database connection.");
                });

            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException e) {
                        // Ignore
                    }
                }
            }
        });
    }

    // ================================================================
    // SHEET LOADING
    // ================================================================

    public void loadSheet(String sheetName) {
        if (multiSheetModel == null || sheetName == null) {
            System.out.println("⚠️ loadSheet: multiSheetModel or sheetName is null");
            return;
        }

        MultiSheetModel.SheetData sheetData = multiSheetModel.getSheet(sheetName);
        if (sheetData == null) {
            System.out.println("⚠️ loadSheet: SheetData is null for: " + sheetName);
            return;
        }

        currentSheet = sheetName;
        System.out.println("📊 Loading sheet: " + sheetName +
                " (" + sheetData.getRowCount() + " rows, " +
                sheetData.getColumnCount() + " columns)");

        if (sheetData.getRows().isEmpty()) {
            masterData = FXCollections.observableArrayList();
            filteredData = new FilteredList<>(masterData, p -> true);
            sortedData = new SortedList<>(filteredData);
            sortedData.comparatorProperty().bind(dataTable.comparatorProperty());
            dataTable.setItems(sortedData);
            dataTable.getColumns().clear();

            rowCountLabel.setText("0 rows loaded");
            colCountLabel.setText("0 columns selected");

            if (sheetStatusLabel != null) {
                sheetStatusLabel.setText("Sheet: " + sheetName + " (empty)");
            }

            dataTable.setPlaceholder(new Label("Sheet '" + sheetName + "' has no data rows."));
            return;
        }

        // ===== FIX: Set flag to prevent listener from triggering =====
        isUpdatingColumns = true;

        try {
            // Clear existing columns
            dataTable.getColumns().clear();

            // Create new ObservableList from sheet data
            masterData = FXCollections.observableArrayList();
            masterData.addAll(sheetData.getRows());
            System.out.println("🔍 DEBUG: masterData size = " + masterData.size());

            // Update column selector (listener won't fire because flag is true)
            List<String> headers = sheetData.getHeaders();
            columnSelector.getItems().setAll(headers);
            columnSelector.getCheckModel().checkAll();

            // Recreate filtered and sorted lists
            filteredData = new FilteredList<>(masterData, p -> true);
            sortedData = new SortedList<>(filteredData);
            sortedData.comparatorProperty().bind(dataTable.comparatorProperty());

            // Set items on the table
            dataTable.setItems(sortedData);
            System.out.println("🔍 DEBUG: dataTable items set, size = " + dataTable.getItems().size());

            // Apply filters and update UI
            filterRegistry.clear();
            applyGlobalFilters();

            // Temporarily unset flag so updateTableColumns can run
            isUpdatingColumns = false;
            updateTableColumns();
            isUpdatingColumns = true;

            // Update metadata labels
            updateMetadataLabels();

            // Update Select All checkbox
            updateSelectAllCheckBox();

            // Update status labels
            if (sheetStatusLabel != null) {
                sheetStatusLabel.setText("Sheet: " + sheetName);
            }
            if (rowStatusLabel != null) {
                rowStatusLabel.setText("Rows: " + sheetData.getRowCount());
            }
            if (colStatusLabel != null) {
                colStatusLabel.setText("Columns: " + sheetData.getColumnCount());
            }
            if (bottomRowCountLabel != null) {
                bottomRowCountLabel.setText(sheetData.getRowCount() + " rows");
            }
            if (bottomColCountLabel != null) {
                bottomColCountLabel.setText(sheetData.getColumnCount() + " columns");
            }

            dataTable.setPlaceholder(new Label("No records match the active criteria."));
            dataTable.refresh();

            System.out.println("✅ Loaded sheet: " + sheetName +
                    " (" + sheetData.getRowCount() + " rows, " +
                    sheetData.getColumnCount() + " columns)");
            System.out.println("🔍 DEBUG: Table columns = " + dataTable.getColumns().size());
            System.out.println("🔍 DEBUG: Table items = " + dataTable.getItems().size());

        } finally {
            // ===== FIX: Always reset the flag =====
            isUpdatingColumns = false;
        }
    }

    // ================================================================
    // TABLE COLUMN UPDATE
    // ================================================================

    private void updateTableColumns() {
        dataTable.getColumns().clear();
        List<String> selectedColumns = columnSelector.getCheckModel().getCheckedItems();

        System.out.println("🔍 DEBUG: updateTableColumns - creating " + selectedColumns.size() + " columns");

        for (String colName : selectedColumns) {
            TableColumn<Map<String, String>, String> column = new TableColumn<>(colName);
            column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOrDefault(colName, "")));

            // Make cell editable
            column.setCellFactory(createEditableCellFactory());

            // Setup column menu (filters & sorting)
            setupColumnMenu(column, colName);

            dataTable.getColumns().add(column);
        }

        // Ensure items are still there
        if (dataTable.getItems() == null || dataTable.getItems().isEmpty()) {
            if (sortedData != null && !sortedData.isEmpty()) {
                dataTable.setItems(sortedData);
                System.out.println("🔍 DEBUG: Re-set items after column update");
            }
        }

        autoSizeColumns();
        updateSelectAllCheckBox();
    }

    // ================================================================
    // EXCEL-STYLE FILTERS & SORTING
    // ================================================================

    private void setupColumnMenu(TableColumn<Map<String, String>, String> column, String colName) {
        Label headerLabel = new Label(colName);

        if (filterRegistry.hasFilter(colName)) {
            headerLabel.setText(colName + " \u25BD");
            headerLabel.setStyle("-fx-text-fill: #0078d4; -fx-font-weight: bold;");
        }

        headerLabel.setStyle(headerLabel.getStyle() + " -fx-cursor: hand;");

        column.setGraphic(headerLabel);
        column.setText("");

        // Sort menu items
        MenuItem sortAsc = new MenuItem("Sort A to Z ▲");
        sortAsc.setOnAction(e -> {
            column.setSortType(TableColumn.SortType.ASCENDING);
            if (!dataTable.getSortOrder().contains(column)) {
                dataTable.getSortOrder().add(column);
            }
        });

        MenuItem sortDesc = new MenuItem("Sort Z to A ▼");
        sortDesc.setOnAction(e -> {
            column.setSortType(TableColumn.SortType.DESCENDING);
            if (!dataTable.getSortOrder().contains(column)) {
                dataTable.getSortOrder().add(column);
            }
        });

        MenuItem clearSort = new MenuItem("Clear Sort");
        clearSort.setOnAction(e -> {
            dataTable.getSortOrder().remove(column);
        });

        // Filter menu items
        MenuItem clearFilter = new MenuItem("Clear Filter from [" + colName + "]");
        clearFilter.setOnAction(e -> {
            filterRegistry.clearColumn(colName);
            applyCombinedFilters();
            updateTableColumns();
        });

        // Unique values checklist
        CustomMenuItem uniqueValuesItem = createUniqueValueChecklist(colName);

        // Advanced filters
        Menu advancedFilters = createAdvancedFiltersMenu(colName);

        ContextMenu contextMenu = new ContextMenu(
                sortAsc,
                sortDesc,
                clearSort,
                new SeparatorMenuItem(),
                clearFilter,
                new SeparatorMenuItem(),
                uniqueValuesItem,
                new SeparatorMenuItem(),
                advancedFilters
        );

        column.getGraphic().setOnMouseClicked(e -> {
            contextMenu.show(column.getGraphic(), e.getScreenX(), e.getScreenY());
        });

        column.setContextMenu(contextMenu);
    }

    // ================================================================
    // STATUS MESSAGE
    // ================================================================

    private void showStatusMessage(String message) {
        if (rowStatusLabel != null) {
            String currentText = rowStatusLabel.getText();
            rowStatusLabel.setText(message);
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    // ignore
                }
                Platform.runLater(() -> {
                    if (rowStatusLabel != null && rowStatusLabel.getText().equals(message)) {
                        rowStatusLabel.setText(currentText);
                    }
                });
            }).start();
        }
        System.out.println("ℹ️ " + message);
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    @FXML
    public void handleSheetChange() {
        String selectedSheet = sheetSelector.getValue();
        if (selectedSheet != null && !selectedSheet.equals(currentSheet)) {
            loadSheet(selectedSheet);
        }
    }

    @FXML
    public void refreshData() {
        if (currentSheet != null && multiSheetModel != null) {
            masterData = FXCollections.observableArrayList();
            loadSheet(currentSheet);
        }
    }

    private void autoSizeColumns() {
        Platform.runLater(() -> {
            final double MIN_COLUMN_WIDTH = 80.0;
            final double MAX_COLUMN_WIDTH = 300.0;

            for (TableColumn<Map<String, String>, ?> column : dataTable.getColumns()) {
                double maxContentWidth = 0;

                String headerText = column.getText();
                if (headerText != null) {
                    javafx.scene.text.Text text = new javafx.scene.text.Text(headerText);
                    text.setFont(javafx.scene.text.Font.font("System", 13));
                    double headerWidth = text.getLayoutBounds().getWidth() + 20.0;
                    maxContentWidth = Math.max(maxContentWidth, headerWidth);
                }

                int rowCount = 0;
                for (Map<String, String> row : dataTable.getItems()) {
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

    private CustomMenuItem createUniqueValueChecklist(String colName) {
        Set<String> uniqueValues = masterData.stream()
                .map(row -> row.getOrDefault(colName, ""))
                .collect(Collectors.toCollection(TreeSet::new));

        CheckListView<String> checkListView = new CheckListView<>(FXCollections.observableArrayList(uniqueValues));
        checkListView.setPrefHeight(200);

        CheckBox selectAll = new CheckBox("(Select All)");
        selectAll.setSelected(true);
        selectAll.setOnAction(e -> {
            if (selectAll.isSelected()) {
                checkListView.getCheckModel().checkAll();
            } else {
                checkListView.getCheckModel().clearChecks();
            }
        });

        checkListView.getCheckModel().checkAll();
        checkListView.getCheckModel().getCheckedItems().addListener((ListChangeListener<String>) c -> {
            List<String> checked = checkListView.getCheckModel().getCheckedItems();
            selectAll.setSelected(checked.size() == uniqueValues.size());

            filterRegistry.setPredicates(colName + "_unique",
                    List.of(row -> checked.contains(row.getOrDefault(colName, ""))));
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
                    createFilterDialogItem(colName, "Greater Than...",
                            val -> row -> tryParseDouble(row.get(colName)) > tryParseDouble(val)),
                    createFilterDialogItem(colName, "Less Than...",
                            val -> row -> tryParseDouble(row.get(colName)) < tryParseDouble(val)),
                    createBetweenFilterItem(colName)
            );
        } else {
            menu.getItems().addAll(
                    createFilterDialogItem(colName, "Equals...",
                            val -> row -> row.getOrDefault(colName, "").equalsIgnoreCase(val)),
                    createFilterDialogItem(colName, "Contains...",
                            val -> row -> row.getOrDefault(colName, "").toLowerCase().contains(val.toLowerCase())),
                    createFilterDialogItem(colName, "Begins With...",
                            val -> row -> row.getOrDefault(colName, "").toLowerCase().startsWith(val.toLowerCase())),
                    createFilterDialogItem(colName, "Ends With...",
                            val -> row -> row.getOrDefault(colName, "").toLowerCase().endsWith(val.toLowerCase()))
            );
        }

        return menu;
    }

    private MenuItem createFilterDialogItem(String colName, String label,
                                            java.util.function.Function<String, Predicate<Map<String, String>>> predicateFactory) {
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
        try { return Double.parseDouble(val); }
        catch (Exception e) { return 0; }
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

        if (bottomRowCountLabel != null) {
            bottomRowCountLabel.setText(rows + " rows");
        }
        if (bottomColCountLabel != null) {
            bottomColCountLabel.setText(cols + " columns");
        }
    }

    private void updateSaveStatus(String text, String color) {
        if (saveStatusLabel != null) {
            saveStatusLabel.setText(text);
            saveStatusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11px; -fx-font-weight: bold;");
        }
    }

    private String sanitizeTableName(String name) {
        return name.trim().replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_");
    }

    private String sanitizeColumnName(String name) {
        return name.trim().replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_");
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
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

    @FXML
    public void handleRefreshData() {
        refreshData();
        modifiedRowIds.clear();
        if (saveStatusLabel != null) saveStatusLabel.setText("");
        dataTable.refresh();
    }
}