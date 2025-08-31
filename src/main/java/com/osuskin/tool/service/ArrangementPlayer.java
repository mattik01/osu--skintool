package com.osuskin.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osuskin.tool.model.Arrangement;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

public class ArrangementPlayer {
    private static final Logger logger = LoggerFactory.getLogger(ArrangementPlayer.class);
    
    private final ImprovedHitsoundRenderer renderer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    private MediaPlayer backgroundAudioPlayer;
    private Clip hitsoundClip;  // Pre-mixed hitsound clip
    private ImprovedHitsoundRenderer.MixedAudio currentMixedAudio;  // Store for restarting
    private Media currentBackgroundAudio;  // Store for restarting
    private List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();
    
    private double audioVolume = 0.5;  // Background audio volume (0-1)
    private double hitsoundVolume = 0.8;  // Hitsound volume (0-1)
    private boolean isPlaying = false;
    private boolean isLooping = true;
    
    // Cache for loaded arrangements
    private final Map<String, Arrangement> arrangementCache = new HashMap<>();
    private String currentArrangementName;
    
    public ArrangementPlayer(ImprovedHitsoundRenderer renderer) {
        this.renderer = renderer;
    }
    
    /**
     * Load available arrangements from resources
     */
    public List<String> getAvailableArrangements() {
        List<String> arrangements = new ArrayList<>();
        arrangements.add("Airman");
        arrangements.add("Bad_Apple");
        arrangements.add("Big_Black");
        arrangements.add("Blue_Zenith");
        arrangements.add("Cold_As_Ice");
        arrangements.add("Dark_Flight");
        arrangements.add("DJ_MAG_top_rank");
        arrangements.add("Everything_Freeze");
        arrangements.add("Flamewall");
        arrangements.add("FREEDOM_DiVE");
        arrangements.add("Futari_Dake");
        arrangements.add("Get_Jinxed");
        arrangements.add("God-ish");
        arrangements.add("Hey_Kids");
        arrangements.add("ILY");
        arrangements.add("MEGALOVANIA");
        arrangements.add("Night_Knights");
        arrangements.add("No_title");
        arrangements.add("Renatus");
        arrangements.add("Rise_Above");
        arrangements.add("Snow_Drive");
        arrangements.add("Stellar");
        arrangements.add("Sukisuki");
        arrangements.add("understand_me");
        arrangements.add("Yoru_Naku");
        return arrangements;
    }
    
    /**
     * Play an arrangement by name
     */
    public CompletableFuture<Void> playArrangement(String arrangementName) {
        return CompletableFuture.runAsync(() -> {
            try {
                stop(); // Stop any current playback
                
                currentArrangementName = arrangementName;
                
                // Load arrangement JSON
                Arrangement arrangement = loadArrangement(arrangementName);
                
                // Load background audio
                String audioPath = "/arrangements/" + arrangementName + ".mp3";
                InputStream audioStream = getClass().getResourceAsStream(audioPath);
                if (audioStream == null) {
                    logger.error("Audio file not found: {}", audioPath);
                    return;
                }
                
                Media backgroundAudio = new Media(getClass().getResource(audioPath).toExternalForm());
                
                // Render hitsounds into pre-mixed audio
                renderer.renderArrangement(arrangement).thenAccept(mixedAudio -> {
                    Platform.runLater(() -> {
                        playArrangementWithMixedAudio(backgroundAudio, mixedAudio);
                    });
                }).exceptionally(ex -> {
                    logger.error("Failed to render arrangement", ex);
                    return null;
                });
                
            } catch (Exception e) {
                logger.error("Failed to play arrangement: {}", arrangementName, e);
            }
        });
    }
    
    /**
     * Load arrangement from JSON resource
     */
    private Arrangement loadArrangement(String arrangementName) throws IOException {
        if (arrangementCache.containsKey(arrangementName)) {
            return arrangementCache.get(arrangementName);
        }
        
        String jsonPath = "/arrangements/" + arrangementName + ".json";
        InputStream jsonStream = getClass().getResourceAsStream(jsonPath);
        if (jsonStream == null) {
            throw new IOException("Arrangement not found: " + jsonPath);
        }
        
        Arrangement arrangement = objectMapper.readValue(jsonStream, Arrangement.class);
        arrangementCache.put(arrangementName, arrangement);
        return arrangement;
    }
    
    /**
     * Play background audio with pre-mixed hitsounds
     */
    private void playArrangementWithMixedAudio(Media backgroundAudio, 
                                                ImprovedHitsoundRenderer.MixedAudio mixedAudio) {
        try {
            // Store for looping
            currentBackgroundAudio = backgroundAudio;
            currentMixedAudio = mixedAudio;
            
            // Setup background audio player
            backgroundAudioPlayer = new MediaPlayer(backgroundAudio);
            backgroundAudioPlayer.setVolume(audioVolume);
            // Don't use MediaPlayer's built-in looping - we'll handle it manually for sync
            backgroundAudioPlayer.setCycleCount(1);
            
            // Create clip for pre-mixed hitsounds
            hitsoundClip = mixedAudio.createClip();
            
            // Set hitsound volume
            if (hitsoundClip != null) {
                FloatControl volumeControl = (FloatControl) hitsoundClip.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = (float) (Math.log(hitsoundVolume) / Math.log(10.0) * 20.0);
                volumeControl.setValue(Math.max(volumeControl.getMinimum(), Math.min(dB, volumeControl.getMaximum())));
                
                // Don't use Clip's looping either - manual sync is better
                hitsoundClip.loop(0);  // Play once
            }
            
            backgroundAudioPlayer.setOnReady(() -> {
                logger.info("Starting synchronized playback with pre-mixed hitsounds");
                
                // Start both audio streams simultaneously
                if (hitsoundClip != null) {
                    hitsoundClip.start();
                }
                backgroundAudioPlayer.play();
                isPlaying = true;
            });
            
            backgroundAudioPlayer.setOnEndOfMedia(() -> {
                if (isLooping) {
                    // Manually restart both tracks for perfect sync
                    restartPlayback();
                } else {
                    stop();
                }
            });
            
            backgroundAudioPlayer.setOnError(() -> {
                logger.error("Error playing background audio: {}", backgroundAudioPlayer.getError());
                stop();
            });
            
        } catch (Exception e) {
            logger.error("Failed to setup mixed audio playback", e);
            stop();
        }
    }
    
    /**
     * Restart playback for looping with perfect sync
     */
    private void restartPlayback() {
        Platform.runLater(() -> {
            try {
                // Stop current playback
                if (backgroundAudioPlayer != null) {
                    backgroundAudioPlayer.stop();
                    backgroundAudioPlayer.dispose();
                }
                if (hitsoundClip != null) {
                    hitsoundClip.stop();
                    hitsoundClip.close();
                }
                
                // Recreate and start both streams together
                if (currentBackgroundAudio != null && currentMixedAudio != null) {
                    playArrangementWithMixedAudio(currentBackgroundAudio, currentMixedAudio);
                }
            } catch (Exception e) {
                logger.error("Failed to restart playback for loop", e);
                stop();
            }
        });
    }
    
    
    /**
     * Stop all playback
     */
    public void stop() {
        isPlaying = false;
        
        // Cancel scheduled tasks
        clearScheduledTasks();
        
        // Stop background audio
        if (backgroundAudioPlayer != null) {
            backgroundAudioPlayer.stop();
            backgroundAudioPlayer.dispose();
            backgroundAudioPlayer = null;
        }
        
        // Stop hitsound clip
        if (hitsoundClip != null) {
            hitsoundClip.stop();
            hitsoundClip.close();
            hitsoundClip = null;
        }
    }
    
    /**
     * Pause playback
     */
    public void pause() {
        if (backgroundAudioPlayer != null) {
            backgroundAudioPlayer.pause();
        }
        isPlaying = false;
        
        // Pause hitsound clip
        if (hitsoundClip != null && hitsoundClip.isRunning()) {
            hitsoundClip.stop();
        }
    }
    
    /**
     * Resume playback
     */
    public void resume() {
        if (backgroundAudioPlayer != null) {
            backgroundAudioPlayer.play();
        }
        isPlaying = true;
        
        // Resume hitsound clip
        if (hitsoundClip != null && !hitsoundClip.isRunning()) {
            hitsoundClip.start();
        }
    }
    
    /**
     * Toggle play/pause (deprecated - use stop() and playArrangement() instead)
     */
    public void togglePlayPause() {
        // This method is deprecated
        // The UI now always stops and restarts from beginning
        if (isPlaying) {
            stop();
        }
    }
    
    /**
     * Clear scheduled tasks
     */
    private void clearScheduledTasks() {
        for (ScheduledFuture<?> task : scheduledTasks) {
            task.cancel(true);
        }
        scheduledTasks.clear();
    }
    
    // Getters and setters
    public void setAudioVolume(double volume) {
        this.audioVolume = Math.max(0.0, Math.min(1.0, volume)); // Full range 0-100%
        if (backgroundAudioPlayer != null) {
            backgroundAudioPlayer.setVolume(audioVolume);
        }
    }
    
    public void setHitsoundVolume(double volume) {
        this.hitsoundVolume = Math.max(0.0, Math.min(1.0, volume));
        // Update volume for active hitsound clip
        if (hitsoundClip != null && hitsoundClip.isOpen()) {
            try {
                FloatControl volumeControl = (FloatControl) hitsoundClip.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = (float) (Math.log(hitsoundVolume) / Math.log(10.0) * 20.0);
                volumeControl.setValue(Math.max(volumeControl.getMinimum(), Math.min(dB, volumeControl.getMaximum())));
            } catch (Exception e) {
                logger.debug("Could not adjust hitsound volume", e);
            }
        }
    }
    
    public void setLooping(boolean looping) {
        this.isLooping = looping;
        if (backgroundAudioPlayer != null) {
            backgroundAudioPlayer.setCycleCount(looping ? MediaPlayer.INDEFINITE : 1);
        }
    }
    
    public boolean isPlaying() {
        return isPlaying;
    }
    
    public boolean isLooping() {
        return isLooping;
    }
    
    public double getAudioVolume() {
        return audioVolume;
    }
    
    public double getHitsoundVolume() {
        return hitsoundVolume;
    }
    
    /**
     * Shutdown executor services
     */
    public void shutdown() {
        stop();
        scheduler.shutdown();
        renderer.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}