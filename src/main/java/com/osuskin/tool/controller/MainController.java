package com.osuskin.tool.controller;

import com.osuskin.tool.model.Configuration;
import com.osuskin.tool.model.Skin;
import com.osuskin.tool.service.SkinScannerService;
import com.osuskin.tool.util.ConfigurationManager;
import com.osuskin.tool.util.OsuPathDetector;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Predicate;

public class MainController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    
    // Menu items
    @FXML private MenuItem menuSelectDirectory;
    @FXML private MenuItem menuRefreshSkins;
    @FXML private MenuItem menuExportConfig;
    @FXML private MenuItem menuImportConfig;
    @FXML private MenuItem menuExit;
    @FXML private CheckMenuItem menuShowFavorites;
    @FXML private MenuItem menuSettings;
    @FXML private MenuItem menuOpenSkinContainer;
    @FXML private MenuItem menuClearCache;
    @FXML private MenuItem menuAbout;
    
    // Toolbar components
    @FXML private Button btnSelectDirectory;
    @FXML private Button btnRefresh;
    @FXML private TextField txtSearch;
    @FXML private Button btnClearSearch;
    @FXML private ToggleButton btnFavorites;
    
    // Main content
    @FXML private ListView<Skin> listSkins;
    @FXML private Label lblSkinCount;
    @FXML private ComboBox<String> cmbSortBy;
    @FXML private Button btnGridView;
    
    // Details panel
    @FXML private VBox skinDetailsPane;
    @FXML private VBox detailsContainer;
    @FXML private Label lblNoSelection;
    
    // Status bar
    @FXML private Label lblStatus;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblDirectory;
    
    // Services and data
    private ConfigurationManager configurationManager;
    private SkinScannerService skinScannerService;
    private ObservableList<Skin> allSkins;
    private FilteredList<Skin> filteredSkins;
    private SortedList<Skin> sortedSkins;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing MainController");
        
        // Initialize collections
        allSkins = FXCollections.observableArrayList();
        filteredSkins = new FilteredList<>(allSkins);
        sortedSkins = new SortedList<>(filteredSkins);
        
        // Set up ListView
        listSkins.setItems(sortedSkins);
        listSkins.setCellFactory(listView -> new SkinListCell());
        
        // Set up search functionality
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            updateFilter();
        });
        
        // Set up favorites toggle
        btnFavorites.selectedProperty().addListener((observable, oldValue, newValue) -> {
            menuShowFavorites.setSelected(newValue);
            updateFilter();
        });
        
        menuShowFavorites.selectedProperty().addListener((observable, oldValue, newValue) -> {
            btnFavorites.setSelected(newValue);
            updateFilter();
        });
        
        // Set up sort options
        cmbSortBy.setItems(FXCollections.observableArrayList(
            "Name (A-Z)", "Name (Z-A)", "Date Modified", "File Count", "Size", "Favorites First"
        ));
        cmbSortBy.setValue("Name (A-Z)");
        cmbSortBy.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateSort();
        });
        
        // Initially disable controls that require a directory
        updateControlsState(false);
        
        logger.info("MainController initialization completed");
    }
    
    public void setConfigurationManager(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
        this.skinScannerService = new SkinScannerService(configurationManager);
        
        // Update UI based on configuration
        Configuration config = configurationManager.getConfiguration();
        if (config.isConfigured()) {
            lblDirectory.setText(config.getOsuSkinsDirectory());
            updateControlsState(true);
            
            if (config.isAutoScanOnStartup()) {
                Platform.runLater(this::startSkinScan);
            }
        } else {
            // Show helpful message with suggested default path
            Path suggestedPath = OsuPathDetector.getDefaultOsuSkinsPath();
            if (suggestedPath != null) {
                lblDirectory.setText("No directory selected (Suggested: " + suggestedPath + ")");
            } else {
                lblDirectory.setText("No directory selected");
            }
            
            // Show directory selection dialog on first run
            Platform.runLater(this::onSelectDirectory);
        }
    }
    
    @FXML
    private void onSelectDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select osu! Skins Directory");
        
        // Set initial directory based on priority:
        // 1. Previously configured directory
        // 2. Default osu! skins path
        // 3. Parent of default osu! skins path
        Configuration config = configurationManager.getConfiguration();
        Path initialDir = null;
        
        if (config.getOsuSkinsDirectoryPath() != null && Files.exists(config.getOsuSkinsDirectoryPath())) {
            initialDir = config.getOsuSkinsDirectoryPath();
        } else {
            // Try to suggest default osu! skins path
            Path defaultOsuPath = OsuPathDetector.getDefaultOsuSkinsPath();
            if (defaultOsuPath != null) {
                if (Files.exists(defaultOsuPath)) {
                    initialDir = defaultOsuPath;
                } else {
                    // If the skins directory doesn't exist, try the parent osu! directory
                    Path parentDir = defaultOsuPath.getParent();
                    if (parentDir != null && Files.exists(parentDir)) {
                        initialDir = parentDir;
                    }
                }
            }
        }
        
        if (initialDir != null) {
            directoryChooser.setInitialDirectory(initialDir.toFile());
            logger.info("Set initial directory for chooser: {}", initialDir);
        }
        
        Stage stage = (Stage) btnSelectDirectory.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);
        
        if (selectedDirectory != null) {
            Path selectedPath = selectedDirectory.toPath();
            logger.info("Selected directory: {}", selectedPath);
            
            // Validate if this looks like a valid osu! skins directory
            if (!OsuPathDetector.isValidOsuSkinsDirectory(selectedPath)) {
                Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                confirmAlert.setTitle("Directory Validation");
                confirmAlert.setHeaderText("The selected directory doesn't appear to contain osu! skins.");
                confirmAlert.setContentText("Are you sure you want to use this directory?\n\n" +
                    "Expected: A directory containing osu! skin folders with files like skin.ini, hitcircle.png, etc.\n" +
                    "Selected: " + selectedPath);
                
                if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.CANCEL) {
                    return; // User cancelled
                }
            }
            
            configurationManager.setOsuSkinsDirectory(selectedPath);
            configurationManager.saveConfiguration();
            
            lblDirectory.setText(selectedPath.toString());
            updateControlsState(true);
            
            // Start scanning
            startSkinScan();
        }
    }
    
    @FXML
    private void onRefreshSkins() {
        startSkinScan();
    }
    
    @FXML
    private void onClearSearch() {
        txtSearch.clear();
    }
    
    @FXML
    private void onToggleFavorites() {
        updateFilter();
    }
    
    @FXML
    private void onToggleView() {
        // TODO: Implement grid view toggle
        logger.info("Grid view toggle requested (not yet implemented)");
    }
    
    @FXML
    private void onSkinSelected(MouseEvent event) {
        Skin selectedSkin = listSkins.getSelectionModel().getSelectedItem();
        if (selectedSkin != null) {
            displaySkinDetails(selectedSkin);
            
            // Double-click to open preview
            if (event.getClickCount() == 2) {
                openSkinPreview(selectedSkin);
            }
        }
    }
    
    @FXML
    private void onSettings() {
        // TODO: Implement settings dialog
        logger.info("Settings dialog requested (not yet implemented)");
    }
    
    @FXML
    private void onOpenSkinContainer() {
        Configuration config = configurationManager.getConfiguration();
        Path skinContainerPath = config.getSkinContainerPathAsPath();
        
        if (skinContainerPath != null) {
            try {
                // Create directory if it doesn't exist
                if (!Files.exists(skinContainerPath)) {
                    Files.createDirectories(skinContainerPath);
                }
                
                // Open in file explorer
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    Runtime.getRuntime().exec("explorer.exe " + skinContainerPath.toString());
                } else {
                    Runtime.getRuntime().exec("xdg-open " + skinContainerPath.toString());
                }
            } catch (Exception e) {
                logger.error("Failed to open skin container directory", e);
                showError("Failed to open Skin Container", "Could not open the Skin Container directory: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void onClearCache() {
        // TODO: Implement cache clearing
        logger.info("Clear cache requested (not yet implemented)");
    }
    
    @FXML
    private void onExportConfig() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Configuration");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        fileChooser.setInitialFileName("osu-skintool-config.json");
        
        Stage stage = (Stage) btnSelectDirectory.getScene().getWindow();
        File selectedFile = fileChooser.showSaveDialog(stage);
        
        if (selectedFile != null) {
            try {
                configurationManager.exportConfiguration(selectedFile.toPath());
                showInfo("Export Successful", "Configuration exported successfully to: " + selectedFile.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Failed to export configuration", e);
                showError("Export Failed", "Failed to export configuration: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void onImportConfig() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Configuration");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        
        Stage stage = (Stage) btnSelectDirectory.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        
        if (selectedFile != null) {
            try {
                configurationManager.importConfiguration(selectedFile.toPath());
                showInfo("Import Successful", "Configuration imported successfully. Please restart the application.");
            } catch (Exception e) {
                logger.error("Failed to import configuration", e);
                showError("Import Failed", "Failed to import configuration: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void onAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About osu! Skin Selection Tool");
        alert.setHeaderText("osu! Skin Selection Tool v1.0.0");
        alert.setContentText("A comprehensive tool for managing and previewing osu! skins.\n\n" +
                            "Built with JavaFX and love for the osu! community.");
        alert.showAndWait();
    }
    
    @FXML
    private void onExit() {
        Platform.exit();
    }
    
    private void startSkinScan() {
        Configuration config = configurationManager.getConfiguration();
        if (!config.isConfigured()) {
            return;
        }
        
        lblStatus.setText("Scanning skins...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1); // Indeterminate progress
        
        Task<List<Skin>> scanTask = skinScannerService.createScanTask();
        
        scanTask.setOnSucceeded(event -> {
            List<Skin> scannedSkins = scanTask.getValue();
            allSkins.setAll(scannedSkins);
            updateSkinCount();
            lblStatus.setText("Scan completed. Found " + scannedSkins.size() + " skins.");
            progressBar.setVisible(false);
            logger.info("Skin scan completed. Found {} skins", scannedSkins.size());
        });
        
        scanTask.setOnFailed(event -> {
            Throwable exception = scanTask.getException();
            logger.error("Skin scan failed", exception);
            lblStatus.setText("Scan failed: " + exception.getMessage());
            progressBar.setVisible(false);
            showError("Scan Failed", "Failed to scan skins: " + exception.getMessage());
        });
        
        Thread scanThread = new Thread(scanTask);
        scanThread.setDaemon(true);
        scanThread.start();
    }
    
    private void updateControlsState(boolean hasDirectory) {
        btnRefresh.setDisable(!hasDirectory);
        menuRefreshSkins.setDisable(!hasDirectory);
        menuOpenSkinContainer.setDisable(!hasDirectory);
    }
    
    private void updateFilter() {
        String searchText = txtSearch.getText();
        boolean showFavoritesOnly = btnFavorites.isSelected();
        
        Predicate<Skin> filter = skin -> {
            // Search filter
            if (searchText != null && !searchText.trim().isEmpty()) {
                String lowerCaseFilter = searchText.toLowerCase();
                if (!skin.getName().toLowerCase().contains(lowerCaseFilter) &&
                    (skin.getAuthor() == null || !skin.getAuthor().toLowerCase().contains(lowerCaseFilter))) {
                    return false;
                }
            }
            
            // Favorites filter
            if (showFavoritesOnly && !skin.isFavorite()) {
                return false;
            }
            
            return true;
        };
        
        filteredSkins.setPredicate(filter);
        updateSkinCount();
    }
    
    private void updateSort() {
        String sortBy = cmbSortBy.getValue();
        if (sortBy == null) return;
        
        Comparator<Skin> comparator = switch (sortBy) {
            case "Name (A-Z)" -> Comparator.comparing(Skin::getName, String.CASE_INSENSITIVE_ORDER);
            case "Name (Z-A)" -> Comparator.comparing(Skin::getName, String.CASE_INSENSITIVE_ORDER).reversed();
            case "Date Modified" -> Comparator.comparing(Skin::getLastModified, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
            case "File Count" -> Comparator.comparing(Skin::getFileCount).reversed();
            case "Size" -> Comparator.comparing(Skin::getTotalSize).reversed();
            case "Favorites First" -> Comparator.comparing(Skin::isFavorite).reversed()
                    .thenComparing(Skin::getName, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(Skin::getName, String.CASE_INSENSITIVE_ORDER);
        };
        
        sortedSkins.setComparator(comparator);
    }
    
    private void updateSkinCount() {
        lblSkinCount.setText(String.valueOf(filteredSkins.size()));
    }
    
    private void displaySkinDetails(Skin skin) {
        // Clear existing details
        detailsContainer.getChildren().clear();
        
        // Add skin information
        addDetailLabel("Name", skin.getName());
        if (skin.getAuthor() != null) {
            addDetailLabel("Author", skin.getAuthor());
        }
        if (skin.getVersion() != null) {
            addDetailLabel("Version", skin.getVersion());
        }
        addDetailLabel("Elements", skin.getElementCount() + " items");
        addDetailLabel("Images", skin.getImageElementCount() + " files");
        addDetailLabel("Audio", skin.getAudioElementCount() + " files");
        addDetailLabel("Total Size", skin.getFormattedTotalSize());
        if (skin.getLastModified() != null) {
            addDetailLabel("Modified", skin.getLastModified().toString());
        }
        
        // Add preview button
        Button btnPreview = new Button("Open Preview");
        btnPreview.setOnAction(e -> openSkinPreview(skin));
        detailsContainer.getChildren().add(btnPreview);
    }
    
    private void addDetailLabel(String title, String value) {
        Label titleLabel = new Label(title + ":");
        titleLabel.setStyle("-fx-font-weight: bold;");
        Label valueLabel = new Label(value);
        valueLabel.setWrapText(true);
        
        detailsContainer.getChildren().addAll(titleLabel, valueLabel);
    }
    
    private void openSkinPreview(Skin skin) {
        // TODO: Implement preview window
        logger.info("Opening preview for skin: {}", skin.getName());
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    // Custom ListCell for displaying skins
    private static class SkinListCell extends ListCell<Skin> {
        @Override
        protected void updateItem(Skin skin, boolean empty) {
            super.updateItem(skin, empty);
            
            if (empty || skin == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(skin.getDisplayInfo());
                // TODO: Add thumbnail image
            }
        }
    }
}