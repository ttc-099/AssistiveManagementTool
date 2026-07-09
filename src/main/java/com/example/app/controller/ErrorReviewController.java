package com.example.app.controller;

import com.example.app.service.GeographicValidationService.ValidationError;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;

public class ErrorReviewController {

    @FXML private TableView<ValidationError> errorTable;
    @FXML private TableColumn<ValidationError, String> headerCol;
    @FXML private TableColumn<ValidationError, String> valueCol;
    @FXML private TableColumn<ValidationError, String> suggestionCol;

    private List<ValidationError> errors;
    private boolean accepted = false;

    @FXML
    public void initialize() {
        headerCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().header()));
        valueCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().value()));
        suggestionCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().suggestion()));
        
        errorTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    public void setErrors(List<ValidationError> errors) {
        this.errors = errors;
        errorTable.setItems(FXCollections.observableArrayList(errors));
        errorTable.getSelectionModel().selectAll();
    }

    public boolean isAccepted() {
        return accepted;
    }

    public List<ValidationError> getSelectedCorrections() {
        return errorTable.getSelectionModel().getSelectedItems();
    }

    @FXML
    private void handleAccept() {
        accepted = true;
        closeStage();
    }

    @FXML
    private void handleCancel() {
        accepted = false;
        closeStage();
    }

    private void closeStage() {
        Stage stage = (Stage) errorTable.getScene().getWindow();
        stage.close();
    }
}
