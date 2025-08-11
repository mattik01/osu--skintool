package com.osuskin.tool.controller;

import com.osuskin.tool.model.Skin;
import com.osuskin.tool.model.SkinElementRegistry;
import com.osuskin.tool.service.SkinElementLoader;
import com.osuskin.tool.view.GameplayAnimator;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for the skin preview window.
 * Handles audio playback and visual animation of skin elements.
 */
public class SkinPreviewController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(SkinPreviewController.class);
    
    // FXML Controls
    @FXML private Label lblSkinName;
    @FXML private Button btnClose;
    
    // Audio Controls
    @FXML private Slider volumeSlider;
    @FXML private Label lblVolume;
    @FXML private Button btnPlayHitsounds;
    @FXML private Button btnPlayMisc;
    @FXML private Button btnStopAudio;
    @FXML private Label lblNowPlaying;
    
    // Gameplay Preview
    @FXML private Rectangle gameplayBackground;
    @FXML private Canvas gameplayCanvas;
    @FXML private Canvas overlayCanvas;
    
    // Animation Controls
    @FXML private Button btnPlay;
    @FXML private Button btnPause;
    @FXML private Button btnReset;
    @FXML private CheckBox chkLoop;
    @FXML private Slider speedSlider;
    @FXML private Label lblSpeed;
    
    // Info
    @FXML private Label lblElementCount;
    @FXML private Label lblMissingElements;
    @FXML private Label lblAnimationStatus;
    
    // Services and state
    private Skin currentSkin;
    private SkinElementLoader elementLoader;
    private GameplayAnimator gameplayAnimator;
    private MediaPlayer currentAudioPlayer;
    private List<MediaPlayer> hitsoundPlayers = new ArrayList<>();
    private AnimationTimer animationTimer;
    private boolean isAnimating = false;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Setup volume slider
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double volume = newVal.doubleValue();
            lblVolume.setText(String.format("%.0f%%", volume));
            updateAudioVolume(volume / 100.0);
        });
        
        // Setup speed slider
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double speed = newVal.doubleValue();
            lblSpeed.setText(String.format("%.1fx", speed));
            if (gameplayAnimator != null) {
                gameplayAnimator.setPlaybackSpeed(speed);
            }
        });
        
        // Initialize canvases
        gameplayCanvas.widthProperty().addListener((obs, oldVal, newVal) -> redrawCanvas());
        gameplayCanvas.heightProperty().addListener((obs, oldVal, newVal) -> redrawCanvas());
        
        // Bind canvas sizes to parent
        gameplayBackground.widthProperty().bind(gameplayCanvas.widthProperty());
        gameplayBackground.heightProperty().bind(gameplayCanvas.heightProperty());
    }
    
    public void setSkin(Skin skin) {
        this.currentSkin = skin;
        lblSkinName.setText(skin.getName() + " - Preview");
        
        // Initialize element loader
        Path skinPath = skin.getDirectoryPathAsPath();
        elementLoader = new SkinElementLoader(skinPath);
        
        // Initialize gameplay animator
        gameplayAnimator = new GameplayAnimator(gameplayCanvas, overlayCanvas, elementLoader);
        
        // Load skin elements
        loadSkinElements();
        
        // Update info
        updateElementInfo();
    }
    
    private void loadSkinElements() {
        CompletableFuture.runAsync(() -> {
            // Preload essential elements for preview
            preloadGameplayElements();
            preloadAudioElements();
            
            Platform.runLater(() -> {
                lblAnimationStatus.setText("Elements loaded");
                gameplayAnimator.initialize();
            });
        });
    }
    
    private void preloadGameplayElements() {
        // Preload hit circle elements
        elementLoader.loadImage("hitcircle");
        elementLoader.loadImage("hitcircleoverlay");
        elementLoader.loadImage("approachcircle");
        
        // Preload slider elements
        elementLoader.loadAnimation("sliderb");
        elementLoader.loadImage("sliderfollowcircle");
        elementLoader.loadImage("reversearrow");
        
        // Preload cursor
        elementLoader.loadImage("cursor");
        elementLoader.loadImage("cursortrail");
        
        // Preload hit bursts
        elementLoader.loadAnimation("hit300");
        elementLoader.loadAnimation("hit100");
        elementLoader.loadAnimation("hit50");
        
        // Preload numbers
        for (int i = 0; i <= 9; i++) {
            elementLoader.loadImage("default-" + i);
        }
    }
    
    private void preloadAudioElements() {
        // Preload common hitsounds
        elementLoader.loadAudio("normal-hitnormal");
        elementLoader.loadAudio("normal-hitclap");
        elementLoader.loadAudio("normal-hitwhistle");
        elementLoader.loadAudio("normal-hitfinish");
        
        elementLoader.loadAudio("soft-hitnormal");
        elementLoader.loadAudio("soft-hitclap");
        
        // Preload misc sounds
        elementLoader.loadAudio("applause");
        elementLoader.loadAudio("failsound");
        elementLoader.loadAudio("sectionpass");
        elementLoader.loadAudio("combobreak");
    }
    
    @FXML
    private void onPlayHitsounds() {
        stopCurrentAudio();
        lblNowPlaying.setText("Hitsounds Arrangement");
        
        // Create a sequence of hitsounds
        String[] hitsoundSequence = {
            "normal-hitnormal",
            "normal-hitclap",
            "normal-hitwhistle",
            "normal-hitfinish",
            "soft-hitnormal",
            "soft-hitclap",
            "drum-hitnormal",
            "drum-hitfinish"
        };
        
        playAudioSequence(hitsoundSequence, 200); // 200ms between sounds
    }
    
    @FXML
    private void onPlayMiscSounds() {
        stopCurrentAudio();
        lblNowPlaying.setText("Misc Sounds");
        
        // Play a sequence of misc sounds
        String[] miscSequence = {
            "sectionpass",
            "combobreak",
            "applause",
            "failsound"
        };
        
        playAudioSequence(miscSequence, 800); // 800ms between sounds
    }
    
    private void playAudioSequence(String[] soundNames, int delayMs) {
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
                                lblNowPlaying.setText("Nothing");
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
    
    @FXML
    private void onStopAudio() {
        stopCurrentAudio();
        lblNowPlaying.setText("Nothing");
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
    
    @FXML
    private void onPlayAnimation() {
        if (!isAnimating) {
            startAnimation();
        }
    }
    
    @FXML
    private void onPauseAnimation() {
        if (isAnimating) {
            pauseAnimation();
        }
    }
    
    @FXML
    private void onResetAnimation() {
        resetAnimation();
    }
    
    private void startAnimation() {
        if (gameplayAnimator == null) return;
        
        isAnimating = true;
        lblAnimationStatus.setText("Playing");
        btnPlay.setDisable(true);
        btnPause.setDisable(false);
        
        if (animationTimer == null) {
            animationTimer = new AnimationTimer() {
                private long lastUpdate = 0;
                
                @Override
                public void handle(long now) {
                    if (lastUpdate == 0) {
                        lastUpdate = now;
                    }
                    
                    double deltaTime = (now - lastUpdate) / 1_000_000_000.0; // Convert to seconds
                    lastUpdate = now;
                    
                    gameplayAnimator.update(deltaTime);
                    gameplayAnimator.render();
                    
                    // Check if animation completed
                    if (!chkLoop.isSelected() && gameplayAnimator.isComplete()) {
                        pauseAnimation();
                    }
                }
            };
        }
        
        animationTimer.start();
    }
    
    private void pauseAnimation() {
        isAnimating = false;
        lblAnimationStatus.setText("Paused");
        btnPlay.setDisable(false);
        btnPause.setDisable(true);
        
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }
    
    private void resetAnimation() {
        pauseAnimation();
        
        if (gameplayAnimator != null) {
            gameplayAnimator.reset();
            gameplayAnimator.render();
        }
        
        lblAnimationStatus.setText("Ready");
    }
    
    private void redrawCanvas() {
        if (gameplayAnimator != null) {
            gameplayAnimator.render();
        }
    }
    
    private void updateElementInfo() {
        if (elementLoader == null) return;
        
        SkinElementLoader.SkinElementStats stats = elementLoader.getElementStats();
        
        int totalElements = stats.elementsByCategory.values().stream()
            .mapToInt(Integer::intValue).sum();
        lblElementCount.setText("Elements: " + totalElements);
        
        int missingRequired = stats.totalRequiredElements - stats.presentRequiredElements;
        lblMissingElements.setText("Missing: " + missingRequired);
        
        if (missingRequired > 0) {
            lblMissingElements.setTextFill(Color.ORANGE);
        } else {
            lblMissingElements.setTextFill(Color.GREEN);
        }
    }
    
    @FXML
    private void onClose() {
        // Clean up
        stopCurrentAudio();
        if (animationTimer != null) {
            animationTimer.stop();
        }
        
        // Close window
        Stage stage = (Stage) btnClose.getScene().getWindow();
        stage.close();
    }
}