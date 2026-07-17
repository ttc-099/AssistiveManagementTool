package com.example.app.controller;

import com.example.app.service.addTools.countries.CountryAnalyserService.CourseBreakdown;
import com.example.app.service.addTools.countries.CountryAnalyserService.CourseCount;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class CountryDrillDownController {

    @FXML private Label titleLabel;
    @FXML private Label summaryLabel;
    @FXML private Label totalStudentsLabel;
    @FXML private TableView<CourseCount> courseTable;
    @FXML private TableColumn<CourseCount, String> rankCol;
    @FXML private TableColumn<CourseCount, String> courseCodeCol;
    @FXML private TableColumn<CourseCount, String> courseNameCol;
    @FXML private TableColumn<CourseCount, String> intakeCol;
    @FXML private TableColumn<CourseCount, String> countCol;

    private CourseBreakdown breakdown;

    @FXML
    public void initialize() {
        rankCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(String.valueOf(courseTable.getItems().indexOf(cellData.getValue()) + 1)));
        courseCodeCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getCourseCode()));
        courseNameCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getCourseName() != null ? cellData.getValue().getCourseName() : ""));

        // ===== FIX: Get intake from the CourseCount object, not from a single display string =====
        intakeCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getIntake()));

        countCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(String.valueOf(cellData.getValue().getCount())));

        courseTable.setPlaceholder(new Label("No course data available for this country."));
    }

    public void setBreakdown(CourseBreakdown breakdown) {
        this.breakdown = breakdown;

        titleLabel.setText("🌍 " + breakdown.getCountry() + " - " + breakdown.getSheetName());
        summaryLabel.setText("Students from " + breakdown.getCountry() + " in " + breakdown.getSheetName());

        ObservableList<CourseCount> courseData = FXCollections.observableArrayList();
        courseData.addAll(breakdown.getCourseCounts());
        courseTable.setItems(courseData);

        totalStudentsLabel.setText("Total Students: " + breakdown.getTotalStudents());

        // Hide Intake column for UGPG data (no Intake column in UGPG sheet)
        if (!breakdown.getSheetName().equals("FY data")) {
            intakeCol.setVisible(false);
        } else {
            intakeCol.setVisible(true);
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) titleLabel.getScene().getWindow();
        stage.close();
    }
}