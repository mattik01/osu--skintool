package com.osuskin.tool.controller;

import com.osuskin.tool.controller.SkinPreviewController;
import com.osuskin.tool.model.Configuration;
import com.osuskin.tool.model.ElementGroup;
import com.osuskin.tool.model.Skin;
import com.osuskin.tool.model.SkinContainer;
import com.osuskin.tool.service.SkinScannerService;
import com.osuskin.tool.service.SkinContainerService;
import com.osuskin.tool.service.SkinElementLoader;
import com.osuskin.tool.view.SimpleGameplayRenderer;
import com.osuskin.tool.view.gameplay.GameplayRenderer;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.animation.AnimationTimer;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.concurrent.CompletableFuture;

public class MainController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    
    // Menu items
    @FXML private MenuItem menuSelectDirectory;
    @FXML private MenuItem menuExit;
    @FXML private MenuItem menuOpenSkinContainer;
    @FXML private MenuItem menuAbout;
    
    // Toolbar components
    @FXML private Button btnRefresh;
    @FXML private TextField txtSearch;
    
    // Main content
    @FXML private ListView<Skin> listSkins;
    @FXML private Label lblSkinCount;
    @FXML private Label lblCompressedCount;
    @FXML private Button btnExtract;
    @FXML private ComboBox<String> cmbSortBy;
    
    // Details panel
    @FXML private VBox skinDetailsPane;
    @FXML private Label lblSkinName;
    @FXML private Label lblElementInfo;
    @FXML private VBox previewContainer;
    @FXML private VBox placeholderContainer;
    
    // Audio Controls
    @FXML private Slider volumeSlider;
    @FXML private Label lblVolume;
    @FXML private Button btnPlayHitsounds;
    @FXML private Button btnPlayMisc;
    @FXML private Button btnStopAudio;
    @FXML private Label lblNowPlaying;
    
    // Gameplay Preview
    @FXML private Canvas gameplayCanvas;
    
    // Selection Tab Components
    @FXML private VBox selectionSection;
    @FXML private Label currentSkinLabel;
    @FXML private Button selectAllButton;
    @FXML private Button circlesSelectButton;
    @FXML private Button cursorSelectButton;
    @FXML private Button uiSelectButton;
    @FXML private Button restSelectButton;
    @FXML private Button hitsoundsSelectButton;
    @FXML private Button restAudioSelectButton;
    @FXML private VBox containerContent;
    @FXML private Label containerEmptyLabel;
    @FXML private Label elementCountLabel;
    @FXML private Button clearContainerButton;
    @FXML private Button exportButton;
    
    // Status bar
    @FXML private Label lblStatus;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblDirectory;
    
    // Services and data
    private ConfigurationManager configurationManager;
    private SkinScannerService skinScannerService;
    private SkinContainerService skinContainerService;
    private ObservableList<Skin> allSkins;
    private FilteredList<Skin> filteredSkins;
    private SortedList<Skin> sortedSkins;
    
    // Preview components
    private SkinElementLoader elementLoader;
    private SimpleGameplayRenderer simpleRenderer;
    private GameplayRenderer enhancedRenderer;
    private boolean useEnhancedRenderer = true; // Toggle for renderer type
    private MediaPlayer currentAudioPlayer;
    private List<MediaPlayer> hitsoundPlayers = new ArrayList<>();
    private AnimationTimer animationTimer;
    private boolean isAnimating = false;
    private Skin currentPreviewSkin;
    
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
        
        // Set up sort options
        cmbSortBy.setItems(FXCollections.observableArrayList(
            "Name (A-Z)", "Name (Z-A)", "File Count", "Size"
        ));
        cmbSortBy.setValue("Name (A-Z)");
        cmbSortBy.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateSort();
        });
        
        // Initially disable controls that require a directory
        updateControlsState(false);
        
        // Setup preview controls
        setupPreviewControls();
        
        // Setup canvas resize listener
        setupCanvasResizeListener();
        
        // Initially hide preview controls
        hidePreviewControls();
        
        // Initialize skin container service
        skinContainerService = new SkinContainerService();
        
        logger.info("MainController initialization completed");
    }
    
    public void setConfigurationManager(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
        this.skinScannerService = new SkinScannerService(configurationManager);
        this.skinContainerService = new SkinContainerService();
        
        // Update UI based on configuration
        Configuration config = configurationManager.getConfiguration();
        
        // Set persistent state for skin container service
        skinContainerService.setPersistentState(config.getSkinContainerState());
        
        if (config.isConfigured()) {
            lblDirectory.setText(config.getOsuSkinsDirectory());
            updateControlsState(true);
            
            // Restore skin container state
            String containerPath = getSkinContainerPath();
            if (containerPath != null && !config.getSkinContainerState().isEmpty()) {
                skinContainerService.restoreFromPersistentState(
                    config.getSkinContainerState(),
                    config.getOsuSkinsDirectory(),
                    containerPath
                );
                updateContainerUI();
            }
            
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
        
        Stage stage = (Stage) btnRefresh.getScene().getWindow();
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
    private void onSkinSelected(MouseEvent event) {
        Skin selectedSkin = listSkins.getSelectionModel().getSelectedItem();
        if (selectedSkin != null) {
            displaySkinPreview(selectedSkin);
            updateSelectionUI();  // Update Selection tab UI
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
        
        Stage stage = (Stage) btnRefresh.getScene().getWindow();
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
        
        Stage stage = (Stage) btnRefresh.getScene().getWindow();
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
        progressBar.progressProperty().unbind(); // Unbind any existing binding
        progressBar.setProgress(-1); // Indeterminate progress
        
        Task<List<Skin>> scanTask = skinScannerService.createScanTask();
        
        scanTask.setOnSucceeded(event -> {
            List<Skin> scannedSkins = scanTask.getValue();
            allSkins.setAll(scannedSkins);
            updateSkinCount();
            
            // Restore container state after scan if it was lost
            Configuration currentConfig = configurationManager.getConfiguration();
            if (!currentConfig.getSkinContainerState().isEmpty() && skinContainerService.getTotalElementCount() == 0) {
                String containerPath = getSkinContainerPath();
                if (containerPath != null) {
                    skinContainerService.restoreFromPersistentState(
                        currentConfig.getSkinContainerState(),
                        currentConfig.getOsuSkinsDirectory(),
                        containerPath
                    );
                }
            }
            
            updateContainerUI();  // Update container UI after scan
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
        menuOpenSkinContainer.setDisable(!hasDirectory);
    }
    
    private void updateFilter() {
        String searchText = txtSearch.getText();
        
        Predicate<Skin> filter = skin -> {
            // Search filter
            if (searchText != null && !searchText.trim().isEmpty()) {
                String lowerCaseFilter = searchText.toLowerCase();
                if (!skin.getName().toLowerCase().contains(lowerCaseFilter) &&
                    (skin.getAuthor() == null || !skin.getAuthor().toLowerCase().contains(lowerCaseFilter))) {
                    return false;
                }
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
            case "File Count" -> Comparator.comparing(Skin::getFileCount).reversed();
            case "Size" -> Comparator.comparing(Skin::getTotalSize).reversed();
            default -> Comparator.comparing(Skin::getName, String.CASE_INSENSITIVE_ORDER);
        };
        
        sortedSkins.setComparator(comparator);
    }
    
    private void updateSkinCount() {
        lblSkinCount.setText(String.valueOf(filteredSkins.size()));
        
        // Update compressed count and extract button
        int compressedCount = skinScannerService.getCompressedSkinCount();
        if (compressedCount > 0) {
            lblCompressedCount.setText("(" + compressedCount + " compressed)");
            lblCompressedCount.setVisible(true);
            btnExtract.setVisible(true);
        } else {
            lblCompressedCount.setVisible(false);
            btnExtract.setVisible(false);
        }
    }
    
    private void setupPreviewControls() {
        // Setup volume slider
        if (volumeSlider != null) {
            volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                double volume = newVal.doubleValue();
                lblVolume.setText(String.format("%.0f%%", volume));
                updateAudioVolume(volume / 100.0);
            });
        }
    }
    
    private void setupCanvasResizeListener() {
        if (gameplayCanvas != null) {
            // Listen for canvas size changes
            gameplayCanvas.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (enhancedRenderer != null) {
                    enhancedRenderer.onCanvasResize();
                }
            });
            
            gameplayCanvas.heightProperty().addListener((obs, oldVal, newVal) -> {
                if (enhancedRenderer != null) {
                    enhancedRenderer.onCanvasResize();
                }
            });
        }
    }
    
    private void hidePreviewControls() {
        // Show placeholder, hide canvas
        if (placeholderContainer != null) {
            placeholderContainer.setVisible(true);
        }
        if (gameplayCanvas != null) {
            gameplayCanvas.setVisible(false);
        }
        // Hide audio controls
        if (volumeSlider != null && volumeSlider.getParent() != null) {
            volumeSlider.getParent().getParent().setVisible(false);
        }
    }
    
    private void showPreviewControls() {
        // Hide placeholder, show canvas
        if (placeholderContainer != null) {
            placeholderContainer.setVisible(false);
        }
        if (gameplayCanvas != null) {
            gameplayCanvas.setVisible(true);
        }
        // Show audio controls
        if (volumeSlider != null && volumeSlider.getParent() != null) {
            volumeSlider.getParent().getParent().setVisible(true);
        }
    }
    
    private void displaySkinPreview(Skin skin) {
        currentPreviewSkin = skin;
        
        // Update skin name
        lblSkinName.setText(skin.getName());
        
        // Show preview controls
        showPreviewControls();
        
        // Stop any current animations/audio
        stopCurrentPreview();
        
        // Initialize element loader for this skin
        Path skinPath = skin.getDirectoryPathAsPath();
        elementLoader = new SkinElementLoader(skinPath);
        elementLoader.setCurrentSkin(skin);  // Pass skin for combo colors
        
        // Initialize appropriate renderer based on setting
        if (useEnhancedRenderer) {
            enhancedRenderer = new GameplayRenderer(gameplayCanvas, elementLoader);
        } else {
            simpleRenderer = new SimpleGameplayRenderer(gameplayCanvas, elementLoader);
        }
        
        // Update element info immediately
        updateElementInfo();
        
        // Initialize and start autoplay animation without preloading
        Platform.runLater(() -> {
            if (useEnhancedRenderer) {
                enhancedRenderer.initialize();
            } else {
                simpleRenderer.initialize();
            }
            startAutoplayAnimation();
        });
    }
    
    private void updateElementInfo() {
        if (elementLoader == null) return;
        
        SkinElementLoader.SkinElementStats stats = elementLoader.getElementStats();
        
        int totalElements = stats.elementsByCategory.values().stream()
            .mapToInt(Integer::intValue).sum();
        
        int missingRequired = stats.totalRequiredElements - stats.presentRequiredElements;
        
        lblElementInfo.setText(String.format("Elements: %d | Missing: %d", totalElements, missingRequired));
    }
    
    @FXML
    private void onPlayHitsounds() {
        stopCurrentAudio();
        lblNowPlaying.setText("Playing: Hitsounds");
        
        String[] hitsoundSequence = {
            "normal-hitnormal", "normal-hitclap", "normal-hitwhistle", "normal-hitfinish",
            "soft-hitnormal", "soft-hitclap"
        };
        
        playAudioSequence(hitsoundSequence, 200);
    }
    
    @FXML
    private void onPlayMiscSounds() {
        stopCurrentAudio();
        lblNowPlaying.setText("Playing: Misc Sounds");
        
        String[] miscSequence = {
            "sectionpass", "combobreak", "applause", "failsound"
        };
        
        playAudioSequence(miscSequence, 500);
    }
    
    @FXML
    private void onStopAudio() {
        stopCurrentAudio();
        lblNowPlaying.setText("Nothing playing");
    }
    
    private void playAudioSequence(String[] soundNames, int delayMs) {
        if (elementLoader == null) return;
        
        CompletableFuture.runAsync(() -> {
            for (String soundName : soundNames) {
                Media sound = elementLoader.loadAudio(soundName);
                if (sound != null) {
                    Platform.runLater(() -> {
                        MediaPlayer player = new MediaPlayer(sound);
                        player.setVolume(volumeSlider.getValue() / 100.0);
                        player.play();
                        hitsoundPlayers.add(player);
                        
                        player.setOnEndOfMedia(() -> {
                            hitsoundPlayers.remove(player);
                            if (hitsoundPlayers.isEmpty()) {
                                lblNowPlaying.setText("Nothing playing");
                            }
                        });
                    });
                    
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    logger.debug("Sound not found: {}", soundName);
                }
            }
        });
    }
    
    private void stopCurrentAudio() {
        if (currentAudioPlayer != null) {
            currentAudioPlayer.stop();
            currentAudioPlayer = null;
        }
        
        for (MediaPlayer player : hitsoundPlayers) {
            player.stop();
        }
        hitsoundPlayers.clear();
    }
    
    private void updateAudioVolume(double volume) {
        if (currentAudioPlayer != null) {
            currentAudioPlayer.setVolume(volume);
        }
        
        for (MediaPlayer player : hitsoundPlayers) {
            player.setVolume(volume);
        }
    }
    
    private void startAutoplayAnimation() {
        if (useEnhancedRenderer && enhancedRenderer == null) return;
        if (!useEnhancedRenderer && simpleRenderer == null) return;
        
        isAnimating = true;
        
        if (animationTimer != null) {
            animationTimer.stop();
        }
        
        animationTimer = new AnimationTimer() {
            private long lastUpdate = 0;
            
            @Override
            public void handle(long now) {
                if (lastUpdate == 0) {
                    lastUpdate = now;
                }
                
                double deltaTime = (now - lastUpdate) / 1_000_000_000.0;
                lastUpdate = now;
                
                if (useEnhancedRenderer) {
                    enhancedRenderer.update(deltaTime);
                    enhancedRenderer.render();
                } else {
                    simpleRenderer.update(deltaTime);
                    simpleRenderer.render();
                }
            }
        };
        
        animationTimer.start();
    }
    
    private void stopCurrentPreview() {
        stopCurrentAudio();
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
        isAnimating = false;
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
    
    private void showAlert(String title, String message) {
        showAlert(Alert.AlertType.WARNING, title, message);
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    
    @FXML
    private void onExtractCompressed() {
        if (skinScannerService == null) {
            showAlert("Error", "Scanner service not available");
            return;
        }
        
        List<Path> compressedFiles = skinScannerService.getExtractableCompressedSkinFiles();
        if (compressedFiles.isEmpty()) {
            showAlert("Info", "No extractable compressed skin files found.\nAll compressed files have already been extracted.");
            return;
        }
        
        // Show progress and extract files
        lblStatus.setText("Extracting compressed skins...");
        progressBar.setVisible(true);
        progressBar.progressProperty().unbind(); // Unbind any existing binding
        progressBar.setProgress(-1); // Indeterminate progress
        
        Task<Void> extractTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                int extracted = 0;
                int total = compressedFiles.size();
                
                for (int i = 0; i < total; i++) {
                    Path compressedFile = compressedFiles.get(i);
                    updateProgress(i, total);
                    
                    try {
                        if (skinScannerService.extractCompressedSkin(compressedFile)) {
                            extracted++;
                            logger.info("Successfully extracted: {}", compressedFile.getFileName());
                        } else {
                            logger.warn("Failed to extract: {}", compressedFile.getFileName());
                        }
                    } catch (Exception e) {
                        logger.error("Error extracting: {}", compressedFile.getFileName(), e);
                    }
                }
                
                final int finalExtracted = extracted;
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    lblStatus.setText("Ready");
                    
                    if (finalExtracted > 0) {
                        showInfo("Success", 
                               String.format("Successfully extracted %d of %d compressed skin files", 
                                           finalExtracted, total));
                        
                        // Refresh the skin list to show newly extracted skins
                        startSkinScan();
                    } else {
                        showAlert("Warning", "No files were extracted successfully");
                    }
                });
                
                return null;
            }
        };
        
        progressBar.progressProperty().bind(extractTask.progressProperty());
        
        Thread extractThread = new Thread(extractTask);
        extractThread.setDaemon(true);
        extractThread.start();
    }
    
    // Selection Tab Handler Methods
    @FXML
    private void handleCirclesSelect() {
        selectElementGroup(ElementGroup.CIRCLES);
    }
    
    @FXML
    private void handleCursorSelect() {
        selectElementGroup(ElementGroup.CURSOR);
    }
    
    @FXML
    private void handleUISelect() {
        selectElementGroup(ElementGroup.UI);
    }
    
    @FXML
    private void handleRestSelect() {
        selectElementGroup(ElementGroup.REST);
    }
    
    @FXML
    private void handleHitSoundsSelect() {
        selectElementGroup(ElementGroup.HITSOUNDS);
    }
    
    @FXML
    private void handleRestAudioSelect() {
        selectElementGroup(ElementGroup.REST_AUDIO);
    }
    
    @FXML
    private void handleSelectAll() {
        Skin selectedSkin = listSkins.getSelectionModel().getSelectedItem();
        if (selectedSkin == null || selectedSkin.isSpecial()) {
            showAlert(Alert.AlertType.WARNING, "No Skin Selected", "Please select a skin first.");
            return;
        }
        
        String containerPath = getSkinContainerPath();
        skinContainerService.selectAllGroups(selectedSkin, containerPath);
        
        // Save configuration after changes
        if (configurationManager != null) {
            configurationManager.saveConfiguration();
        }
        
        updateSelectionUI();
        updateContainerUI();
        refreshSkinContainer();
    }
    
    @FXML
    private void handleClearContainer() {
        String containerPath = getSkinContainerPath();
        skinContainerService.clearContainer(containerPath);
        
        // Save configuration after clearing
        if (configurationManager != null) {
            configurationManager.saveConfiguration();
        }
        
        updateSelectionUI();
        updateContainerUI();
        refreshSkinContainer();
    }
    
    @FXML
    private void handleExportSkin() {
        if (skinContainerService.getTotalElementCount() == 0) {
            showAlert(Alert.AlertType.WARNING, "Empty Container", "No elements selected to export.");
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog("Mixed Skin");
        dialog.setTitle("Export Skin");
        dialog.setHeaderText("Enter name for the new skin:");
        dialog.setContentText("Skin name:");
        
        dialog.showAndWait().ifPresent(skinName -> {
            if (skinName.trim().isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Invalid Name", "Skin name cannot be empty.");
                return;
            }
            
            try {
                String targetDirectory = configurationManager.getConfiguration().getOsuSkinsDirectory();
                skinContainerService.exportSkin(targetDirectory, skinName);
                
                showAlert(Alert.AlertType.INFORMATION, "Export Successful", 
                    "Skin exported successfully as: " + skinName);
                
                // Don't clear container after export - keep the selections
                // Just refresh the skin list to show the new skin
                startSkinScan();
                
            } catch (IOException e) {
                logger.error("Failed to export skin", e);
                showAlert(Alert.AlertType.ERROR, "Export Failed", 
                    "Failed to export skin: " + e.getMessage());
            }
        });
    }
    
    private void selectElementGroup(ElementGroup group) {
        Skin selectedSkin = listSkins.getSelectionModel().getSelectedItem();
        if (selectedSkin == null || selectedSkin.isSpecial()) {
            showAlert(Alert.AlertType.WARNING, "No Skin Selected", "Please select a skin first.");
            return;
        }
        
        String containerPath = getSkinContainerPath();
        
        if (skinContainerService.isGroupSelected(group, selectedSkin.getName())) {
            skinContainerService.deselectGroup(group, containerPath);
        } else {
            skinContainerService.selectGroup(group, selectedSkin, containerPath);
        }
        
        // Save configuration after changes
        if (configurationManager != null) {
            configurationManager.saveConfiguration();
        }
        
        updateSelectionUI();
        updateContainerUI();
        refreshSkinContainer();
    }
    
    private void updateSelectionUI() {
        Skin selectedSkin = listSkins.getSelectionModel().getSelectedItem();
        
        // Hide selection section if Skin Container is selected
        if (selectedSkin != null && selectedSkin.isSpecial()) {
            selectionSection.setVisible(false);
            selectionSection.setManaged(false);
            return;
        } else {
            selectionSection.setVisible(true);
            selectionSection.setManaged(true);
        }
        
        if (selectedSkin != null && !selectedSkin.isSpecial()) {
            currentSkinLabel.setText(selectedSkin.getName());
            selectAllButton.setDisable(false);
            
            // Update individual group buttons
            updateGroupButton(circlesSelectButton, ElementGroup.CIRCLES, selectedSkin.getName());
            updateGroupButton(cursorSelectButton, ElementGroup.CURSOR, selectedSkin.getName());
            updateGroupButton(uiSelectButton, ElementGroup.UI, selectedSkin.getName());
            updateGroupButton(restSelectButton, ElementGroup.REST, selectedSkin.getName());
            updateGroupButton(hitsoundsSelectButton, ElementGroup.HITSOUNDS, selectedSkin.getName());
            updateGroupButton(restAudioSelectButton, ElementGroup.REST_AUDIO, selectedSkin.getName());
        } else {
            currentSkinLabel.setText("No skin selected");
            selectAllButton.setDisable(true);
            
            // Reset all buttons
            resetGroupButton(circlesSelectButton);
            resetGroupButton(cursorSelectButton);
            resetGroupButton(uiSelectButton);
            resetGroupButton(restSelectButton);
            resetGroupButton(hitsoundsSelectButton);
            resetGroupButton(restAudioSelectButton);
        }
    }
    
    private void updateGroupButton(Button button, ElementGroup group, String skinName) {
        if (skinContainerService.isGroupSelected(group, skinName)) {
            button.setText("✓");
            button.setStyle("-fx-background-color: #90EE90; -fx-font-weight: bold;");
        } else {
            button.setText("Select");
            button.setStyle("");
        }
    }
    
    private void resetGroupButton(Button button) {
        button.setText("Select");
        button.setStyle("");
        button.setDisable(false);
    }
    
    private void updateContainerUI() {
        if (containerContent == null || containerEmptyLabel == null) {
            return;  // UI not initialized yet
        }
        
        containerContent.getChildren().clear();
        
        int totalElements = skinContainerService.getTotalElementCount();
        
        if (totalElements == 0) {
            // Check if the Skin Container directory has files even if not tracked
            String containerPath = getSkinContainerPath();
            boolean hasUntrackedFiles = false;
            if (containerPath != null) {
                Path containerPathObj = Paths.get(containerPath);
                if (Files.exists(containerPathObj)) {
                    try {
                        hasUntrackedFiles = Files.list(containerPathObj)
                            .filter(Files::isRegularFile)
                            .findFirst()
                            .isPresent();
                    } catch (IOException e) {
                        logger.error("Failed to check untracked files", e);
                    }
                }
            }
            
            if (hasUntrackedFiles) {
                containerEmptyLabel.setText("Container has untracked files");
            } else {
                containerEmptyLabel.setText("Empty");
            }
            containerEmptyLabel.setVisible(true);
            containerContent.getChildren().add(containerEmptyLabel);
            clearContainerButton.setDisable(!hasUntrackedFiles);
            exportButton.setDisable(true);
            elementCountLabel.setText("Total: 0 elements");
        } else {
            containerEmptyLabel.setVisible(false);
            
            // Add group info for each selected group
            for (ElementGroup group : ElementGroup.values()) {
                SkinContainer.ContainerGroupInfo info = skinContainerService.getGroupInfo(group);
                if (info.hasElements()) {
                    HBox groupBox = new HBox(10);
                    groupBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    
                    Label groupLabel = new Label(group.getDisplayName() + ":");
                    groupLabel.setStyle("-fx-font-weight: bold; -fx-min-width: 100px;");
                    
                    Label sourceLabel = new Label(info.getSourceSkinName());
                    sourceLabel.setStyle("-fx-text-fill: #666666;");
                    
                    Button removeButton = new Button("Remove");
                    removeButton.setOnAction(e -> {
                        String containerPath = getSkinContainerPath();
                        skinContainerService.deselectGroup(group, containerPath);
                        
                        // Save configuration after changes
                        if (configurationManager != null) {
                            configurationManager.saveConfiguration();
                        }
                        
                        updateSelectionUI();
                        updateContainerUI();
                        refreshSkinContainer();
                    });
                    
                    javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                    
                    groupBox.getChildren().addAll(groupLabel, sourceLabel, spacer, removeButton);
                    containerContent.getChildren().add(groupBox);
                }
            }
            
            clearContainerButton.setDisable(false);
            exportButton.setDisable(false);
            elementCountLabel.setText("Total: " + totalElements + " elements");
        }
    }
    
    private String getSkinContainerPath() {
        if (configurationManager == null) return null;
        String skinsDir = configurationManager.getConfiguration().getOsuSkinsDirectory();
        if (skinsDir == null) return null;
        return Paths.get(skinsDir, "Skin Container").toString();
    }
    
    private void refreshSkinContainer() {
        // Find and refresh the Skin Container in the list
        String containerPath = getSkinContainerPath();
        if (containerPath == null) return;
        
        Path containerPathObj = Paths.get(containerPath);
        
        // Check if container has any files
        boolean hasFiles = false;
        if (Files.exists(containerPathObj)) {
            try {
                hasFiles = Files.list(containerPathObj)
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .isPresent();
            } catch (IOException e) {
                logger.error("Failed to check container files", e);
            }
        }
        
        // Find Skin Container in list
        Skin containerSkin = null;
        for (Skin skin : allSkins) {
            if (skin.getName().equals("Skin Container")) {
                containerSkin = skin;
                break;
            }
        }
        
        if (hasFiles || skinContainerService.getTotalElementCount() > 0) {
            // Container has content - ensure it exists in the list
            if (containerSkin == null && Files.exists(containerPathObj)) {
                // Add Skin Container to the list if not present
                containerSkin = new Skin("Skin Container", containerPathObj);
                containerSkin.setSpecial(true);
                allSkins.add(0, containerSkin);  // Add at beginning of list
            }
            
            if (containerSkin != null) {
                // Rescan the container
                try {
                    containerSkin.getElements().clear();
                    skinScannerService.scanSkin(containerPathObj);
                    
                    // Update preview if selected
                    Skin selectedSkin = listSkins.getSelectionModel().getSelectedItem();
                    if (selectedSkin != null && selectedSkin.getName().equals("Skin Container")) {
                        displaySkinPreview(selectedSkin);
                    }
                    
                    logger.debug("Refreshed Skin Container with {} files", containerSkin.getElements().size());
                } catch (Exception e) {
                    logger.error("Failed to refresh Skin Container", e);
                }
            }
        } else {
            // Container is empty - remove from list if present
            if (containerSkin != null) {
                allSkins.remove(containerSkin);
                logger.debug("Removed empty Skin Container from list");
            }
        }
    }
    
    // Custom ListCell for displaying skins
    private static class SkinListCell extends ListCell<Skin> {
        @Override
        protected void updateItem(Skin skin, boolean empty) {
            super.updateItem(skin, empty);
            
            if (empty || skin == null) {
                setText(null);
                setGraphic(null);
                setStyle("");  // Reset style
            } else {
                setText(skin.getDisplayInfo());
                
                // Apply special styling for Skin Container
                if (skin.isSpecial()) {
                    setStyle("-fx-font-weight: bold; -fx-background-color: #f0f8ff; -fx-text-fill: #2c3e50;");
                } else {
                    setStyle("");  // Reset to default style
                }
            }
        }
    }
}