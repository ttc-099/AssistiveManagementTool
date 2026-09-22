package com.example.app.service.preset;

import com.example.app.service.preset.PresetDataModels.LocalVsInternationalRow;
import com.example.app.service.preset.PresetDataModels.ProgrammeInterestRow;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * Builds dynamic TableView for preset data
 */
public class PresetTableViewBuilder {

    // ================================================================
    // LOCAL VS INTERNATIONAL TABLE
    // ================================================================

    /**
     * Build a TableView for Local vs International data with sub-columns
     */
    public TableView<LocalVsInternationalRow> buildLIVTableView(
            ObservableList<LocalVsInternationalRow> data,
            String tableTitle) {

        TableView<LocalVsInternationalRow> tableView = new TableView<>();
        tableView.setItems(data);
        tableView.setPlaceholder(new javafx.scene.control.Label("No data available"));

        // Programme column
        TableColumn<LocalVsInternationalRow, String> programmeCol = new TableColumn<>("Programme");
        programmeCol.setCellValueFactory(new PropertyValueFactory<>("programme"));
        programmeCol.setPrefWidth(120);
        programmeCol.setStyle("-fx-font-weight: bold;");

        // Applications column with sub-columns
        TableColumn<LocalVsInternationalRow, Number> appLCol = createSubColumn("L", "lApplications");
        TableColumn<LocalVsInternationalRow, Number> appICol = createSubColumn("I", "iApplications");
        TableColumn<LocalVsInternationalRow, Number> appCol = createMetricColumn("Applications", appLCol, appICol);

        // Offers column with sub-columns
        TableColumn<LocalVsInternationalRow, Number> offLCol = createSubColumn("L", "lOffers");
        TableColumn<LocalVsInternationalRow, Number> offICol = createSubColumn("I", "iOffers");
        TableColumn<LocalVsInternationalRow, Number> offCol = createMetricColumn("Offers", offLCol, offICol);

        // CA column with sub-columns
        TableColumn<LocalVsInternationalRow, Number> caLCol = createSubColumn("L", "lCA");
        TableColumn<LocalVsInternationalRow, Number> caICol = createSubColumn("I", "iCA");
        TableColumn<LocalVsInternationalRow, Number> caCol = createMetricColumn("CA", caLCol, caICol);

        // UA column with sub-columns
        TableColumn<LocalVsInternationalRow, Number> uaLCol = createSubColumn("L", "lUA");
        TableColumn<LocalVsInternationalRow, Number> uaICol = createSubColumn("I", "iUA");
        TableColumn<LocalVsInternationalRow, Number> uaCol = createMetricColumn("UA", uaLCol, uaICol);

        // Total Acceptances column with sub-columns
        TableColumn<LocalVsInternationalRow, Number> totalLCol = new TableColumn<>("L");
        totalLCol.setCellValueFactory(cellData -> {
            LocalVsInternationalRow row = cellData.getValue();
            return new SimpleIntegerProperty(row.getTotalAcceptancesL());
        });
        totalLCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<LocalVsInternationalRow, Number> totalICol = new TableColumn<>("I");
        totalICol.setCellValueFactory(cellData -> {
            LocalVsInternationalRow row = cellData.getValue();
            return new SimpleIntegerProperty(row.getTotalAcceptancesI());
        });
        totalICol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<LocalVsInternationalRow, Number> totalCol = createMetricColumn("Total Acceptances", totalLCol, totalICol);

        // Add all columns
        tableView.getColumns().addAll(programmeCol, appCol, offCol, caCol, uaCol, totalCol);

        // Row styling for header rows
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(LocalVsInternationalRow item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.isIsHeader()) {
                    setStyle("-fx-background-color: #e9ecef; -fx-font-weight: bold; -fx-font-size: 13px;");
                } else if (getIndex() % 2 == 0) {
                    setStyle("-fx-background-color: white;");
                } else {
                    setStyle("-fx-background-color: #f8f9fa;");
                }
            }
        });

        return tableView;
    }

    private TableColumn<LocalVsInternationalRow, Number> createSubColumn(String label, String property) {
        TableColumn<LocalVsInternationalRow, Number> col = new TableColumn<>(label);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        col.setPrefWidth(60);
        col.setStyle("-fx-alignment: CENTER-RIGHT;");
        return col;
    }

    private TableColumn<LocalVsInternationalRow, Number> createMetricColumn(
            String label,
            TableColumn<LocalVsInternationalRow, Number> lCol,
            TableColumn<LocalVsInternationalRow, Number> iCol) {

        TableColumn<LocalVsInternationalRow, Number> col = new TableColumn<>(label);
        col.getColumns().addAll(lCol, iCol);
        col.setStyle("-fx-alignment: CENTER;");
        return col;
    }

    // ================================================================
    // PROGRAMME INTERESTS TABLE
    // ================================================================

    /**
     * Build a TableView for Programme Interests data
     */
    public TableView<ProgrammeInterestRow> buildPITableView(
            ObservableList<ProgrammeInterestRow> data,
            List<String> decisionCodes,
            String tableTitle) {

        TableView<ProgrammeInterestRow> tableView = new TableView<>();
        tableView.setItems(data);
        tableView.setPlaceholder(new javafx.scene.control.Label("No data available"));

        // Programme column
        TableColumn<ProgrammeInterestRow, String> programmeCol = new TableColumn<>("Programme");
        programmeCol.setCellValueFactory(new PropertyValueFactory<>("programme"));
        programmeCol.setPrefWidth(120);
        programmeCol.setStyle("-fx-font-weight: bold;");

        tableView.getColumns().add(programmeCol);

        // Dynamic decision columns
        for (String decision : decisionCodes) {
            TableColumn<ProgrammeInterestRow, Number> col = new TableColumn<>(decision);
            col.setCellValueFactory(cellData -> {
                ProgrammeInterestRow row = cellData.getValue();
                return new SimpleIntegerProperty(row.getCountForDecision(decision));
            });
            col.setPrefWidth(60);
            col.setStyle("-fx-alignment: CENTER-RIGHT;");
            tableView.getColumns().add(col);
        }

        // Total column
        TableColumn<ProgrammeInterestRow, Number> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(new PropertyValueFactory<>("total"));
        totalCol.setPrefWidth(60);
        totalCol.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
        tableView.getColumns().add(totalCol);

        // Row styling for header rows
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(ProgrammeInterestRow item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.isIsHeader()) {
                    setStyle("-fx-background-color: #e9ecef; -fx-font-weight: bold; -fx-font-size: 13px;");
                } else if (getIndex() % 2 == 0) {
                    setStyle("-fx-background-color: white;");
                } else {
                    setStyle("-fx-background-color: #f8f9fa;");
                }
            }
        });

        return tableView;
    }

    // ================================================================
    // APPLY STYLES TO TABLE
    // ================================================================

    public <T> void applyTableStyles(TableView<T> tableView) {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setStyle("-fx-border-color: #dee2e6; -fx-border-radius: 4px;");

        // Apply alternating row colors
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (getIndex() % 2 == 0) {
                    setStyle("-fx-background-color: white;");
                } else {
                    setStyle("-fx-background-color: #f8f9fa;");
                }
            }
        });
    }
}