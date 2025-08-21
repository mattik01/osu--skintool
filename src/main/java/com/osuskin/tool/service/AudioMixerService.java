package com.osuskin.tool.service;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
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
    private MediaPlayer hybridHitsoundPlayer;  // For hybrid approach
    private List<HitsoundGeneratorService.TimedHitsound> currentHitsounds = new ArrayList<>();
    private List<MediaPlayer> activeHitsoundPlayers = new ArrayList<>();
    private Timeline hitsoundTimeline;
    
    private SkinElementLoader elementLoader;
    private HitsoundGeneratorService hitsoundGenerator;
    private HybridHitsoundService hybridHitsoundService;
    
    private boolean isPlaying = false;
    private boolean isLooping = true;
    private boolean useHybridApproach = false;
    
    private double audioVolume = 0.1;  // Audio volume (0-1, but maxes at 20% in UI)
    private double hitsoundVolume = 0.8;  // Hitsound volume (0-1, full range)
    
    private String currentSampleName = null;
    private Path samplesDirectory;
    private long playbackStartTime = 0;
    
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
        this.hitsoundGenerator = new HitsoundGeneratorService();
        this.hybridHitsoundService = new HybridHitsoundService();
        
        // Check environment variable for which approach to use
        String hitsoundMode = System.getenv("HITSOUND_MODE");
        if ("hybrid".equalsIgnoreCase(hitsoundMode)) {
            useHybridApproach = true;
            logger.info("Using HYBRID approach (Python script) for hitsound generation");
        } else {
            useHybridApproach = false;
            logger.info("Using PURE JAVA approach for hitsound generation");
        }
        
        logger.info("AudioMixerService initialized with samples directory: {}", samplesDirectory);
    }
    
    public void setElementLoader(SkinElementLoader elementLoader) {
        this.elementLoader = elementLoader;
        this.hitsoundGenerator.setElementLoader(elementLoader);
    }
    
    /**
     * Called when the skin changes - reload hitsounds with new skin
     */
    public void onSkinChanged() {
        if (currentSampleName != null && elementLoader != null && elementLoader.getCurrentSkin() != null) {
            String skinPath = elementLoader.getCurrentSkin().getDirectoryPath();
            logger.info("Skin changed, reloading hitsounds from: {}", skinPath);
            
            // Clear skin hitsounds cache
            hitsoundGenerator.clearCache();
            
            // Reload the current sample with new skin hitsounds
            String sampleToReload = currentSampleName;
            loadSample(sampleToReload);
        }
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
        logger.info("Loading sample: {} (mode: {})", sampleName, useHybridApproach ? "HYBRID" : "PURE JAVA");
        stop();
        
        BeatmapSample sample = getAvailableSamples().stream()
            .filter(s -> s.name.equals(sampleName))
            .findFirst()
            .orElse(null);
        
        if (sample == null || !sample.arrangementFile.toFile().exists()) {
            logger.error("Invalid sample or missing arrangement file: {}", sampleName);
            return;
        }
        
        currentSampleName = sampleName;
        
        try {
            // Load audio file
            if (!sample.audioFile.toFile().exists()) {
                logger.error("Audio file does not exist for: {}", sampleName);
                return;
            }
            
            String audioUri = sample.audioFile.toUri().toString();
            logger.info("Loading audio file: {}", sample.audioFile);
            
            try {
                Media originalAudio = new Media(audioUri);
                originalAudioPlayer = new MediaPlayer(originalAudio);
                originalAudioPlayer.setCycleCount(isLooping ? MediaPlayer.INDEFINITE : 1);
                originalAudioPlayer.setVolume(audioVolume);
                logger.info("Original audio player created with volume: {}", audioVolume);
            } catch (Exception e) {
                logger.error("Failed to create original audio player for URI: {}", audioUri, e);
                logger.warn("JavaFX audio not available - this is common in WSL environments");
                return;
            }
            
            if (useHybridApproach) {
                // HYBRID APPROACH: Use Python script to generate hitsounds
                String skinPath = (elementLoader != null && elementLoader.getCurrentSkin() != null) 
                    ? elementLoader.getCurrentSkin().getDirectoryPath()
                    : "";
                
                if (skinPath.isEmpty()) {
                    skinPath = System.getProperty("user.dir") + "/src/main/resources/default-skin";
                }
                
                logger.info("Generating hitsounds with Python script from skin: {}", skinPath);
                Path generatedHitsounds = hybridHitsoundService
                    .generateHitsounds(sample.arrangementFile, skinPath, sampleName)
                    .join();
                
                if (generatedHitsounds != null) {
                    hybridHitsoundPlayer = hybridHitsoundService.createPlayer(generatedHitsounds, hitsoundVolume);
                    logger.info("Hybrid hitsound player created");
                } else {
                    logger.error("Failed to generate hybrid hitsounds");
                }
                
            } else {
                // PURE JAVA APPROACH: Generate hitsounds dynamically
                if (elementLoader != null && elementLoader.getCurrentSkin() != null) {
                    String skinPath = elementLoader.getCurrentSkin().getDirectoryPath();
                    logger.info("Loading hitsounds from skin: {}", skinPath);
                    
                    // Load skin hitsounds (async but we'll wait)
                    hitsoundGenerator.loadSkinHitsounds(skinPath).join();
                }
                
                // Generate hitsounds from arrangement
                logger.info("Generating hitsounds from arrangement: {}", sample.arrangementFile);
                currentHitsounds = hitsoundGenerator.generateHitsoundsFromArrangement(sample.arrangementFile).join();
                logger.info("Generated {} hitsounds", currentHitsounds.size());
            }
            
            originalAudioPlayer.setOnReady(() -> {
                logger.info("Original audio ready, duration: {} seconds", originalAudioPlayer.getTotalDuration().toSeconds());
            });
            
            originalAudioPlayer.setOnError(() -> {
                logger.error("Error playing original audio: {}", originalAudioPlayer.getError());
            });
            
            originalAudioPlayer.setOnEndOfMedia(() -> {
                if (!isLooping) {
                    stop();
                } else {
                    // Reset hitsounds for loop
                    resetHitsounds();
                }
            });
            
            logger.info("Sample loaded successfully: {}", sampleName);
            
        } catch (Exception e) {
            logger.error("Failed to load sample: {}", sampleName, e);
        }
    }
    
    private void resetHitsounds() {
        // Stop any active hitsound players (Pure Java approach)
        for (MediaPlayer player : activeHitsoundPlayers) {
            player.stop();
            player.dispose();
        }
        activeHitsoundPlayers.clear();
        
        if (hitsoundTimeline != null) {
            hitsoundTimeline.stop();
            hitsoundTimeline = null;
        }
        
        // Stop hybrid player if exists
        if (hybridHitsoundPlayer != null) {
            hybridHitsoundPlayer.stop();
            hybridHitsoundPlayer.seek(Duration.ZERO);
        }
    }
    
    public void play() {
        logger.info("Play called, original audio exists: {}", originalAudioPlayer != null);
        
        if (originalAudioPlayer == null) {
            logger.warn("Cannot play - audio player not initialized");
            return;
        }
        
        // Reset hitsounds before starting
        resetHitsounds();
        
        // Start audio playback
        originalAudioPlayer.play();
        isPlaying = true;
        
        if (useHybridApproach) {
            // HYBRID: Play the pre-generated hitsounds MP3
            if (hybridHitsoundPlayer != null) {
                hybridHitsoundPlayer.play();
                logger.info("Playing hybrid hitsounds");
            }
        } else {
            // PURE JAVA: Start hitsound playback timeline
            if (!currentHitsounds.isEmpty()) {
                playbackStartTime = System.currentTimeMillis();
                startHitsoundPlayback();
                logger.info("Playback started with {} hitsounds", currentHitsounds.size());
            }
        }
    }
    
    private void startHitsoundPlayback() {
        // Create a timeline to trigger hitsounds at the right times
        hitsoundTimeline = new Timeline();
        
        for (HitsoundGeneratorService.TimedHitsound timedHitsound : currentHitsounds) {
            KeyFrame keyFrame = new KeyFrame(
                Duration.millis(timedHitsound.timeMs),
                e -> playHitsound(timedHitsound)
            );
            hitsoundTimeline.getKeyFrames().add(keyFrame);
        }
        
        // Set cycle count based on looping
        hitsoundTimeline.setCycleCount(isLooping ? Timeline.INDEFINITE : 1);
        hitsoundTimeline.play();
    }
    
    private void playHitsound(HitsoundGeneratorService.TimedHitsound timedHitsound) {
        try {
            MediaPlayer player = timedHitsound.createPlayer(hitsoundVolume);
            activeHitsoundPlayers.add(player);
            
            // Clean up finished players periodically
            player.setOnEndOfMedia(() -> {
                activeHitsoundPlayers.remove(player);
                player.dispose();
            });
            
            player.play();
        } catch (Exception e) {
            logger.error("Failed to play hitsound: {}", timedHitsound.name, e);
        }
    }
    
    public void pause() {
        if (originalAudioPlayer != null) {
            originalAudioPlayer.pause();
        }
        
        if (useHybridApproach) {
            if (hybridHitsoundPlayer != null) {
                hybridHitsoundPlayer.pause();
            }
        } else {
            if (hitsoundTimeline != null) {
                hitsoundTimeline.pause();
            }
        }
        
        isPlaying = false;
    }
    
    public void stop() {
        if (originalAudioPlayer != null) {
            originalAudioPlayer.stop();
            originalAudioPlayer.dispose();
            originalAudioPlayer = null;
        }
        
        if (hybridHitsoundPlayer != null) {
            hybridHitsoundPlayer.stop();
            hybridHitsoundPlayer.dispose();
            hybridHitsoundPlayer = null;
        }
        
        resetHitsounds();
        currentHitsounds.clear();
        isPlaying = false;
    }
    
    public void reset() {
        if (originalAudioPlayer != null) {
            originalAudioPlayer.stop();
            originalAudioPlayer.seek(Duration.ZERO);
        }
        
        resetHitsounds();
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
        if (hitsoundTimeline != null) {
            hitsoundTimeline.setCycleCount(looping ? Timeline.INDEFINITE : 1);
        }
    }
    
    public void setAudioVolume(double volume) {
        // Audio volume maxes out at 0.2 (20%)
        this.audioVolume = Math.max(0.0, Math.min(0.2, volume * 0.2));
        if (originalAudioPlayer != null) {
            originalAudioPlayer.setVolume(audioVolume);
        }
    }
    
    public void setHitsoundVolume(double volume) {
        // Hitsound volume (0-100%), no limiter applied
        this.hitsoundVolume = Math.max(0.0, Math.min(1.0, volume));
        
        if (useHybridApproach) {
            // Update hybrid player volume
            if (hybridHitsoundPlayer != null) {
                hybridHitsoundPlayer.setVolume(hitsoundVolume);
            }
        } else {
            // Update volume for any currently playing hitsounds
            for (MediaPlayer player : activeHitsoundPlayers) {
                player.setVolume(hitsoundVolume);
            }
        }
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
    
    public double getAudioVolume() {
        return audioVolume;
    }
    
    public double getHitsoundVolume() {
        return hitsoundVolume;
    }
    
    public String getCurrentSampleName() {
        return currentSampleName;
    }
}