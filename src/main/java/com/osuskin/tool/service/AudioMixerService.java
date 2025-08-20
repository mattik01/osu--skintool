package com.osuskin.tool.service;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AudioMixerService {
    private static final Logger logger = LoggerFactory.getLogger(AudioMixerService.class);
    
    private MediaPlayer originalAudioPlayer;
    private MediaPlayer hitsoundsPlayer;
    private SkinElementLoader elementLoader;
    
    private boolean isPlaying = false;
    private boolean isLooping = true;
    
    private double masterVolume = 0.5;
    private double originalMixLevel = 0.5;
    private double hitsoundMixLevel = 0.5;
    
    private String currentSampleName = null;
    private Path samplesDirectory;
    
    public static class BeatmapSample {
        public final String name;
        public final Path directory;
        public final Path audioFile;
        public final Path hitsoundsFile;
        public final Path combinedFile;
        public final Path arrangementFile;
        
        public BeatmapSample(String name, Path directory) {
            this.name = name;
            this.directory = directory;
            this.audioFile = directory.resolve("audio.mp3");
            this.hitsoundsFile = directory.resolve("hitsounds.mp3");
            this.combinedFile = directory.resolve("combined.mp3");
            this.arrangementFile = directory.resolve("arrangement.json");
        }
        
        public boolean isValid() {
            return audioFile.toFile().exists() && hitsoundsFile.toFile().exists();
        }
    }
    
    public AudioMixerService() {
        // Use absolute path to samples directory
        String currentDir = System.getProperty("user.dir");
        this.samplesDirectory = Paths.get(currentDir, "beatmap-hitsound-extractor", "samples");
        logger.info("AudioMixerService initialized with samples directory: {}", samplesDirectory);
    }
    
    public void setElementLoader(SkinElementLoader elementLoader) {
        this.elementLoader = elementLoader;
    }
    
    public List<BeatmapSample> getAvailableSamples() {
        List<BeatmapSample> samples = new ArrayList<>();
        
        File samplesDir = samplesDirectory.toFile();
        logger.info("Looking for samples in: {}", samplesDir.getAbsolutePath());
        
        if (!samplesDir.exists() || !samplesDir.isDirectory()) {
            logger.warn("Samples directory does not exist: {}", samplesDirectory);
            return samples;
        }
        
        File[] directories = samplesDir.listFiles(File::isDirectory);
        if (directories != null) {
            logger.info("Found {} directories in samples folder", directories.length);
            for (File dir : directories) {
                BeatmapSample sample = new BeatmapSample(dir.getName(), dir.toPath());
                if (sample.isValid()) {
                    samples.add(sample);
                    logger.debug("Added valid sample: {}", sample.name);
                } else {
                    logger.debug("Skipped invalid sample: {} (audio exists: {}, hitsounds exists: {})", 
                               sample.name, sample.audioFile.toFile().exists(), sample.hitsoundsFile.toFile().exists());
                }
            }
        }
        
        samples.sort(Comparator.comparing(s -> s.name));
        logger.info("Total valid samples found: {}", samples.size());
        return samples;
    }
    
    public void loadSample(String sampleName) {
        logger.info("Loading sample: {}", sampleName);
        stop();
        
        BeatmapSample sample = getAvailableSamples().stream()
            .filter(s -> s.name.equals(sampleName))
            .findFirst()
            .orElse(null);
        
        if (sample == null || !sample.isValid()) {
            logger.error("Invalid sample: {}", sampleName);
            return;
        }
        
        currentSampleName = sampleName;
        
        try {
            logger.info("Loading audio file: {}", sample.audioFile);
            logger.info("Audio file exists: {}", sample.audioFile.toFile().exists());
            logger.info("Loading hitsounds file: {}", sample.hitsoundsFile);
            logger.info("Hitsounds file exists: {}", sample.hitsoundsFile.toFile().exists());
            
            if (!sample.audioFile.toFile().exists() || !sample.hitsoundsFile.toFile().exists()) {
                logger.error("Sample files do not exist for: {}", sampleName);
                return;
            }
            
            String audioUri = sample.audioFile.toUri().toString();
            String hitsoundsUri = sample.hitsoundsFile.toUri().toString();
            logger.info("Audio URI: {}", audioUri);
            logger.info("Hitsounds URI: {}", hitsoundsUri);
            
            try {
                Media originalAudio = new Media(audioUri);
                originalAudioPlayer = new MediaPlayer(originalAudio);
                originalAudioPlayer.setCycleCount(isLooping ? MediaPlayer.INDEFINITE : 1);
                originalAudioPlayer.setVolume(calculateVolume(true));
                logger.info("Original audio player created successfully");
            } catch (Exception e) {
                logger.error("Failed to create original audio player for URI: {}", audioUri, e);
                logger.warn("JavaFX audio not available - this is common in WSL environments");
                logger.warn("Audio mixer feature requires a native Linux/Windows/Mac environment with proper audio support");
                // In WSL, JavaFX often can't access audio properly
                // You could implement a fallback using system commands here if needed
                return;
            }
            
            try {
                Media hitsounds = new Media(hitsoundsUri);
                hitsoundsPlayer = new MediaPlayer(hitsounds);
                hitsoundsPlayer.setCycleCount(isLooping ? MediaPlayer.INDEFINITE : 1);
                hitsoundsPlayer.setVolume(calculateVolume(false));
                logger.info("Hitsounds player created successfully");
            } catch (Exception e) {
                logger.error("Failed to create hitsounds player for URI: {}", hitsoundsUri, e);
                if (originalAudioPlayer != null) {
                    originalAudioPlayer.dispose();
                    originalAudioPlayer = null;
                }
                return;
            }
            
            originalAudioPlayer.setOnReady(() -> {
                logger.info("Original audio ready, duration: {} seconds", originalAudioPlayer.getTotalDuration().toSeconds());
            });
            
            hitsoundsPlayer.setOnReady(() -> {
                logger.info("Hitsounds ready, duration: {} seconds", hitsoundsPlayer.getTotalDuration().toSeconds());
            });
            
            originalAudioPlayer.setOnError(() -> {
                logger.error("Error playing original audio: {}", originalAudioPlayer.getError());
            });
            
            hitsoundsPlayer.setOnError(() -> {
                logger.error("Error playing hitsounds: {}", hitsoundsPlayer.getError());
            });
            
            originalAudioPlayer.setOnEndOfMedia(() -> {
                if (!isLooping) {
                    stop();
                }
            });
            
            logger.info("Sample loaded successfully: {}", sampleName);
            
        } catch (Exception e) {
            logger.error("Failed to load sample: {}", sampleName, e);
        }
    }
    
    public void play() {
        logger.info("Play called, players exist: original={}, hitsounds={}", 
                   originalAudioPlayer != null, hitsoundsPlayer != null);
        if (originalAudioPlayer != null && hitsoundsPlayer != null) {
            logger.info("Starting playback with volumes: original={}, hitsounds={}", 
                       originalAudioPlayer.getVolume(), hitsoundsPlayer.getVolume());
            originalAudioPlayer.play();
            hitsoundsPlayer.play();
            isPlaying = true;
        } else {
            logger.warn("Cannot play - players not initialized");
        }
    }
    
    public void pause() {
        if (originalAudioPlayer != null) {
            originalAudioPlayer.pause();
        }
        if (hitsoundsPlayer != null) {
            hitsoundsPlayer.pause();
        }
        isPlaying = false;
    }
    
    public void stop() {
        if (originalAudioPlayer != null) {
            originalAudioPlayer.stop();
            originalAudioPlayer.dispose();
            originalAudioPlayer = null;
        }
        if (hitsoundsPlayer != null) {
            hitsoundsPlayer.stop();
            hitsoundsPlayer.dispose();
            hitsoundsPlayer = null;
        }
        isPlaying = false;
    }
    
    public void togglePlayPause() {
        if (isPlaying) {
            pause();
        } else {
            play();
        }
    }
    
    public void setLooping(boolean looping) {
        this.isLooping = looping;
        if (originalAudioPlayer != null) {
            originalAudioPlayer.setCycleCount(looping ? MediaPlayer.INDEFINITE : 1);
        }
        if (hitsoundsPlayer != null) {
            hitsoundsPlayer.setCycleCount(looping ? MediaPlayer.INDEFINITE : 1);
        }
    }
    
    public void setMasterVolume(double volume) {
        this.masterVolume = Math.max(0.0, Math.min(1.0, volume));
        updateVolumes();
    }
    
    public void setOriginalMixLevel(double level) {
        this.originalMixLevel = Math.max(0.0, Math.min(1.0, level));
        updateVolumes();
    }
    
    public void setHitsoundMixLevel(double level) {
        this.hitsoundMixLevel = Math.max(0.0, Math.min(1.0, level));
        updateVolumes();
    }
    
    private void updateVolumes() {
        if (originalAudioPlayer != null) {
            originalAudioPlayer.setVolume(calculateVolume(true));
        }
        if (hitsoundsPlayer != null) {
            hitsoundsPlayer.setVolume(calculateVolume(false));
        }
    }
    
    private double calculateVolume(boolean isOriginal) {
        double mixLevel = isOriginal ? originalMixLevel : hitsoundMixLevel;
        return masterVolume * mixLevel;
    }
    
    public CompletableFuture<Map<String, Media>> loadSkinHitsounds() {
        if (elementLoader == null) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }
        
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Media> hitsounds = new HashMap<>();
            
            String[] hitsoundNames = {
                "normal-hitnormal", "normal-hitclap", "normal-hitwhistle", "normal-hitfinish",
                "soft-hitnormal", "soft-hitclap", "soft-hitwhistle", "soft-hitfinish",
                "drum-hitnormal", "drum-hitclap", "drum-hitwhistle", "drum-hitfinish"
            };
            
            for (String name : hitsoundNames) {
                Media sound = elementLoader.loadAudio(name);
                if (sound != null) {
                    hitsounds.put(name, sound);
                }
            }
            
            return hitsounds;
        });
    }
    
    public boolean isPlaying() {
        return isPlaying;
    }
    
    public boolean isLooping() {
        return isLooping;
    }
    
    public double getMasterVolume() {
        return masterVolume;
    }
    
    public double getOriginalMixLevel() {
        return originalMixLevel;
    }
    
    public double getHitsoundMixLevel() {
        return hitsoundMixLevel;
    }
    
    public String getCurrentSampleName() {
        return currentSampleName;
    }
}