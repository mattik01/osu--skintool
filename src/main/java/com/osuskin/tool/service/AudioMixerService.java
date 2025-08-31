package com.osuskin.tool.service;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.osuskin.tool.model.Configuration;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AudioMixerService {
    private static final Logger logger = LoggerFactory.getLogger(AudioMixerService.class);
    
    private MediaPlayer audioPlayer;
    private List<MediaPlayer> activeHitsoundPlayers = new ArrayList<>();
    
    private SkinElementLoader elementLoader;
    
    private boolean isPlaying = false;
    private boolean isLooping = true;
    
    private double audioVolume = 0.5;  // Audio volume (0-1)
    private double hitsoundVolume = 0.6;  // Balanced hitsound volume for audible but controlled layering
    
    public AudioMixerService() {
        logger.info("AudioMixerService initialized");
    }
    
    public void initializeFromConfiguration(Configuration config) {
        if (config != null) {
            this.audioVolume = config.getMusicVolume();
            this.hitsoundVolume = config.getEffectsVolume();
            logger.info("Audio volumes initialized from configuration - Music: {}, Effects: {}", 
                       audioVolume, hitsoundVolume);
        }
    }
    
    public void setElementLoader(SkinElementLoader elementLoader) {
        this.elementLoader = elementLoader;
    }
    
    /**
     * Called when the skin changes - reload hitsounds with new skin
     */
    public void onSkinChanged() {
        logger.info("[SKIN-CHANGE] Skin changed, clearing audio cache and stopping all playback");
        stop();
        logger.info("[SKIN-CHANGE] Ready for new skin's hitsounds");
    }
    
    public void playAudioPreview(Media audio) {
        stop();
        
        if (audio == null) {
            logger.warn("Cannot play null audio");
            return;
        }
        
        try {
            audioPlayer = new MediaPlayer(audio);
            audioPlayer.setCycleCount(isLooping ? MediaPlayer.INDEFINITE : 1);
            audioPlayer.setVolume(audioVolume);
            
            audioPlayer.setOnReady(() -> {
                logger.info("Audio ready, duration: {} seconds", audioPlayer.getTotalDuration().toSeconds());
            });
            
            audioPlayer.setOnError(() -> {
                logger.error("Error playing audio: {}", audioPlayer.getError());
            });
            
            audioPlayer.setOnEndOfMedia(() -> {
                if (!isLooping) {
                    stop();
                }
            });
            
            audioPlayer.play();
            isPlaying = true;
            logger.info("Audio playback started");
            
        } catch (Exception e) {
            logger.error("Failed to play audio", e);
        }
    }
    
    public void playHitsound(String hitsoundName) {
        logger.info("[HITSOUND-PLAY] Request to play: '{}'", hitsoundName);
        
        if (elementLoader == null) {
            logger.error("[HITSOUND-PLAY] Cannot play '{}' - element loader not set", hitsoundName);
            return;
        }
        
        try {
            Media sound = elementLoader.loadAudio(hitsoundName);
            if (sound != null) {
                MediaPlayer player = new MediaPlayer(sound);
                player.setVolume(hitsoundVolume);
                activeHitsoundPlayers.add(player);
                
                player.setOnEndOfMedia(() -> {
                    activeHitsoundPlayers.remove(player);
                    player.dispose();
                });
                
                player.play();
                logger.info("[HITSOUND-PLAY] ✓ Playing '{}' (source: {})", 
                    hitsoundName, sound.getSource());
            } else {
                logger.error("[HITSOUND-PLAY] ✗ Failed to load '{}' - sound is null", hitsoundName);
            }
        } catch (Exception e) {
            logger.error("[HITSOUND-PLAY] Exception playing '{}': {}", 
                hitsoundName, e.getMessage(), e);
        }
    }
    
    public void pause() {
        if (audioPlayer != null) {
            audioPlayer.pause();
        }
        isPlaying = false;
    }
    
    public void stop() {
        if (audioPlayer != null) {
            audioPlayer.stop();
            audioPlayer.dispose();
            audioPlayer = null;
        }
        
        for (MediaPlayer player : activeHitsoundPlayers) {
            player.stop();
            player.dispose();
        }
        activeHitsoundPlayers.clear();
        
        isPlaying = false;
    }
    
    public void reset() {
        if (audioPlayer != null) {
            audioPlayer.stop();
            audioPlayer.seek(Duration.ZERO);
        }
        isPlaying = false;
    }
    
    public void togglePlayPause() {
        if (isPlaying) {
            pause();
        } else if (audioPlayer != null) {
            audioPlayer.play();
            isPlaying = true;
        }
    }
    
    public void setLooping(boolean looping) {
        this.isLooping = looping;
        if (audioPlayer != null) {
            audioPlayer.setCycleCount(looping ? MediaPlayer.INDEFINITE : 1);
        }
    }
    
    public void setAudioVolume(double volume) {
        // Audio volume (0-100%)
        this.audioVolume = Math.max(0.0, Math.min(1.0, volume));
        if (audioPlayer != null) {
            audioPlayer.setVolume(audioVolume);
        }
    }
    
    public void setHitsoundVolume(double volume) {
        // Hitsound volume (0-100%)
        this.hitsoundVolume = Math.max(0.0, Math.min(1.0, volume));
        
        for (MediaPlayer player : activeHitsoundPlayers) {
            player.setVolume(hitsoundVolume);
        }
    }
    
    public CompletableFuture<Map<String, Media>> loadSkinHitsounds() {
        logger.info("[HITSOUND-PRELOAD] Starting bulk hitsound preload");
        
        if (elementLoader == null) {
            logger.warn("[HITSOUND-PRELOAD] No element loader - returning empty map");
            return CompletableFuture.completedFuture(new HashMap<>());
        }
        
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Media> hitsounds = new HashMap<>();
            
            String[] hitsoundNames = {
                "normal-hitnormal", "normal-hitclap", "normal-hitwhistle", "normal-hitfinish",
                "soft-hitnormal", "soft-hitclap", "soft-hitwhistle", "soft-hitfinish",
                "drum-hitnormal", "drum-hitclap", "drum-hitwhistle", "drum-hitfinish"
            };
            
            logger.info("[HITSOUND-PRELOAD] Loading {} hitsound types", hitsoundNames.length);
            
            for (String name : hitsoundNames) {
                Media sound = elementLoader.loadAudio(name);
                if (sound != null) {
                    hitsounds.put(name, sound);
                    logger.info("[HITSOUND-PRELOAD] ✓ Loaded: '{}'", name);
                } else {
                    logger.warn("[HITSOUND-PRELOAD] ✗ Missing: '{}'", name);
                }
            }
            
            logger.info("[HITSOUND-PRELOAD] Completed - loaded {}/{} hitsounds", 
                hitsounds.size(), hitsoundNames.length);
            
            return hitsounds;
        });
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
}