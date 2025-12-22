package com.isoburn.controller;

import com.isoburn.model.BurnProgress;
import com.isoburn.model.BurnResult;
import com.isoburn.model.RemovableDrive;
import com.isoburn.service.DriveDetectionService;
import com.isoburn.service.IsoBurnService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML private TextField isoPathField;
    @FXML private Button browseButton;
    @FXML private ComboBox<RemovableDrive> driveComboBox;
    @FXML private Button refreshButton;
    @FXML private CheckBox bootableCheckBox;
    @FXML private CheckBox handleLargeWimCheckBox;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private Label percentLabel;
    @FXML private Button startButton;
    @FXML private Button cancelButton;
    @FXML private TextArea logArea;

    private final DriveDetectionService driveDetectionService;
    private final IsoBurnService isoBurnService;

    private File selectedIsoFile;
    private Task<BurnResult> burnTask;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public MainController(DriveDetectionService driveDetectionService, IsoBurnService isoBurnService) {
        this.driveDetectionService = driveDetectionService;
        this.isoBurnService = isoBurnService;
    }

    @FXML
    public void initialize() {
        appendLog("isoBURN initialized");
        handleRefreshDrives();
    }

    @FXML
    public void handleBrowseIso() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select ISO File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("ISO Files", "*.iso", "*.ISO")
        );

        File file = fileChooser.showOpenDialog(isoPathField.getScene().getWindow());
        if (file != null) {
            selectedIsoFile = file;
            isoPathField.setText(file.getAbsolutePath());
            appendLog("Selected ISO: " + file.getName());
        }
    }

    @FXML
    public void handleRefreshDrives() {
        appendLog("Scanning for removable drives...");

        Task<List<RemovableDrive>> detectTask = new Task<>() {
            @Override
            protected List<RemovableDrive> call() {
                return driveDetectionService.detectRemovableDrives();
            }
        };

        detectTask.setOnSucceeded(event -> {
            List<RemovableDrive> drives = detectTask.getValue();
            driveComboBox.setItems(FXCollections.observableArrayList(drives));

            if (drives.isEmpty()) {
                appendLog("No removable drives found");
            } else {
                appendLog("Found " + drives.size() + " removable drive(s)");
                driveComboBox.getSelectionModel().selectFirst();
            }
        });

        detectTask.setOnFailed(event -> {
            appendLog("ERROR: Failed to detect drives: " + detectTask.getException().getMessage());
        });

        new Thread(detectTask).start();
    }

    @FXML
    public void handleStart() {
        if (selectedIsoFile == null) {
            showAlert(Alert.AlertType.WARNING, "No ISO Selected",
                "Please select an ISO file first.");
            return;
        }

        RemovableDrive selectedDrive = driveComboBox.getValue();
        if (selectedDrive == null) {
            showAlert(Alert.AlertType.WARNING, "No Drive Selected",
                "Please select a target USB drive.");
            return;
        }

        if (!showConfirmation(selectedDrive)) {
            return;
        }

        startBurn(selectedIsoFile, selectedDrive);
    }

    private boolean showConfirmation(RemovableDrive drive) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Format");
        alert.setHeaderText("WARNING: All data will be erased!");
        alert.setContentText(String.format(
            "You are about to format the following drive:\n\n" +
            "Device: %s\n" +
            "Name: %s\n" +
            "Size: %.1f GB\n\n" +
            "ALL DATA ON THIS DRIVE WILL BE PERMANENTLY DELETED.\n\n" +
            "Are you sure you want to continue?",
            drive.getDeviceIdentifier(),
            drive.getName() != null ? drive.getName() : "Untitled",
            drive.getSizeBytes() / 1_000_000_000.0
        ));

        ButtonType formatButton = new ButtonType("Format Drive", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(formatButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == formatButton;
    }

    private void startBurn(File isoFile, RemovableDrive drive) {
        setUIBurning(true);
        progressBar.setProgress(0);
        percentLabel.setText("0%");

        boolean bootable = bootableCheckBox.isSelected();
        boolean handleLargeWim = handleLargeWimCheckBox.isSelected();

        appendLog("Starting burn operation...");
        appendLog("ISO: " + isoFile.getName());
        appendLog("Target: " + drive.getDisplayName());
        appendLog("Options: " + (bootable ? "Bootable (UEFI)" : "Data only") +
                  (handleLargeWim ? ", Handle large WIM" : ""));

        burnTask = new Task<>() {
            @Override
            protected BurnResult call() {
                return isoBurnService.burn(isoFile, drive, bootable, handleLargeWim, progress -> {
                    Platform.runLater(() -> onProgressUpdate(progress));
                });
            }
        };

        burnTask.setOnSucceeded(event -> {
            BurnResult result = burnTask.getValue();
            handleBurnComplete(result);
        });

        burnTask.setOnFailed(event -> {
            Throwable error = burnTask.getException();
            log.error("Burn task failed", error);
            handleBurnComplete(BurnResult.failure("Task failed", error.getMessage()));
        });

        burnTask.setOnCancelled(event -> {
            handleBurnComplete(BurnResult.cancelled());
        });

        new Thread(burnTask).start();
    }

    private void onProgressUpdate(BurnProgress progress) {
        statusLabel.setText(progress.getPhase().getDescription());

        // Always update progress bar for phases that report percentage
        if (progress.getPhase() == BurnProgress.Phase.COPYING ||
            progress.getPhase() == BurnProgress.Phase.SPLITTING_WIM) {
            progressBar.setProgress(progress.getPercentage() / 100.0);
            percentLabel.setText(String.format("%.0f%%", progress.getPercentage()));
        }

        if (progress.getMessage() != null && !progress.getMessage().isBlank()) {
            appendLog(progress.getMessage());
        }
    }

    private void handleBurnComplete(BurnResult result) {
        setUIBurning(false);

        if (result.isSuccess()) {
            progressBar.setProgress(1.0);
            percentLabel.setText("100%");
            statusLabel.setText("Complete!");
            appendLog("SUCCESS: " + result.getMessage());

            if (result.getDurationMillis() > 0) {
                long seconds = result.getDurationMillis() / 1000;
                appendLog(String.format("Duration: %d minutes, %d seconds", seconds / 60, seconds % 60));
            }

            showAlert(Alert.AlertType.INFORMATION, "Success",
                "ISO burned successfully!\n\nYou can safely remove the USB drive.");
        } else {
            progressBar.setProgress(0);
            statusLabel.setText("Failed");
            appendLog("FAILED: " + result.getMessage());

            if (result.getErrorDetails() != null) {
                appendLog("Details: " + result.getErrorDetails());
            }

            if (!result.getMessage().contains("cancelled")) {
                showAlert(Alert.AlertType.ERROR, "Burn Failed",
                    result.getMessage() + "\n\n" +
                    (result.getErrorDetails() != null ? result.getErrorDetails() : ""));
            }
        }

        handleRefreshDrives();
    }

    @FXML
    public void handleCancel() {
        if (burnTask != null && burnTask.isRunning()) {
            appendLog("Cancelling operation...");
            isoBurnService.cancel();
            burnTask.cancel();
        }
    }

    private void setUIBurning(boolean burning) {
        startButton.setDisable(burning);
        cancelButton.setDisable(!burning);
        driveComboBox.setDisable(burning);
        browseButton.setDisable(burning);
        refreshButton.setDisable(burning);
        isoPathField.setDisable(burning);
        bootableCheckBox.setDisable(burning);
        handleLargeWimCheckBox.setDisable(burning);
    }

    private void appendLog(String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String logLine = "[" + timestamp + "] " + message + "\n";

        if (Platform.isFxApplicationThread()) {
            logArea.appendText(logLine);
        } else {
            Platform.runLater(() -> logArea.appendText(logLine));
        }

        log.info(message);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void handleWebsiteClick() {
        openUrl("https://robertschmidt.dev");
    }

    @FXML
    public void handleCoffeeClick() {
        openUrl("https://buymeacoffee.com/robbschmidt");
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            log.error("Failed to open URL: " + url, e);
        }
    }
}
