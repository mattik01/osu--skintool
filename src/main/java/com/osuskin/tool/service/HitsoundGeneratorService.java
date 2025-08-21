package com.osuskin.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for generating hitsounds dynamically from arrangement.json files.
 * Uses the currently selected skin's hitsounds with fallback to default osu! hitsounds.
 */
public class HitsoundGeneratorService {
    private static final Logger logger = LoggerFactory.getLogger(HitsoundGeneratorService.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Path defaultHitsoundsPath;
    private final Map<String, Media> defaultHitsounds = new ConcurrentHashMap<>();
    private final Map<String, Media> skinHitsounds = new ConcurrentHashMap<>();
    
    private SkinElementLoader elementLoader;
    private String currentSkinPath;
    
    
    // Hitsound types
    private static final String[] SAMPLE_SETS = {"normal", "soft", "drum"};
    private static final String[] ADDITIONS = {"hitnormal", "hitclap", "hitwhistle", "hitfinish", "slidertick", "sliderslide"};
    private static final String[] EXTENSIONS = {".wav", ".ogg", ".mp3"};
    
    public HitsoundGeneratorService() {
        // Use the existing default-skin folder in resources
        try {
            var resource = getClass().getResource("/default-skin");
            if (resource != null) {
                String resourcePath = resource.toExternalForm();
                if (resourcePath.startsWith("file:")) {
                    resourcePath = resourcePath.substring(5);
                }
                this.defaultHitsoundsPath = Paths.get(resourcePath);
            } else {
                // Fallback to project directory
                String currentDir = System.getProperty("user.dir");
                this.defaultHitsoundsPath = Paths.get(currentDir, "src", "main", "resources", "default-skin");
                logger.warn("Default skin resource not found, using path: {}", defaultHitsoundsPath);
            }
        } catch (Exception e) {
            // Fallback to project directory
            String currentDir = System.getProperty("user.dir");
            this.defaultHitsoundsPath = Paths.get(currentDir, "src", "main", "resources", "default-skin");
            logger.error("Error locating default skin, using fallback path: {}", defaultHitsoundsPath, e);
        }
        
        // Load default hitsounds on initialization
        loadDefaultHitsounds();
    }
    
    public void setElementLoader(SkinElementLoader elementLoader) {
        this.elementLoader = elementLoader;
    }
    
    /**
     * Load default osu! hitsounds from resources.
     */
    private void loadDefaultHitsounds() {
        logger.info("Loading default hitsounds from: {}", defaultHitsoundsPath);
        
        for (String sampleSet : SAMPLE_SETS) {
            for (String addition : ADDITIONS) {
                String key = sampleSet + "-" + addition;
                
                for (String ext : EXTENSIONS) {
                    Path soundPath = defaultHitsoundsPath.resolve(key + ext);
                    if (Files.exists(soundPath)) {
                        try {
                            Media sound = new Media(soundPath.toUri().toString());
                            defaultHitsounds.put(key, sound);
                            logger.debug("Loaded default hitsound: {}", key);
                            break;
                        } catch (Exception e) {
                            logger.warn("Failed to load default hitsound: {}", key, e);
                        }
                    }
                }
            }
        }
        
        logger.info("Loaded {} default hitsounds", defaultHitsounds.size());
    }
    
    /**
     * Load hitsounds from the currently selected skin.
     */
    public CompletableFuture<Void> loadSkinHitsounds(String skinPath) {
        return CompletableFuture.runAsync(() -> {
            if (skinPath == null || skinPath.equals(currentSkinPath)) {
                return; // Already loaded
            }
            
            logger.info("Loading skin hitsounds from: {}", skinPath);
            skinHitsounds.clear();
            currentSkinPath = skinPath;
            
            Path skinDir = Paths.get(skinPath);
            
            for (String sampleSet : SAMPLE_SETS) {
                for (String addition : ADDITIONS) {
                    String key = sampleSet + "-" + addition;
                    
                    for (String ext : EXTENSIONS) {
                        String filename = key + ext;
                        Path soundPath = skinDir.resolve(filename);
                        
                        if (Files.exists(soundPath)) {
                            try {
                                Media sound = new Media(soundPath.toUri().toString());
                                skinHitsounds.put(key, sound);
                                logger.info("Loaded skin hitsound: {} from {}", key, filename);
                                break;
                            } catch (Exception e) {
                                logger.warn("Failed to load skin hitsound: {} from {}", key, filename, e);
                            }
                        } else {
                            logger.trace("Skin hitsound not found: {}", soundPath);
                        }
                    }
                }
            }
            
            logger.info("Loaded {} skin hitsounds", skinHitsounds.size());
        });
    }
    
    /**
     * Get a hitsound Media object, with fallback to default if not in skin.
     */
    private Media getHitsound(String key) {
        // First try skin hitsounds
        Media sound = skinHitsounds.get(key);
        if (sound != null) {
            logger.debug("Using skin hitsound for: {}", key);
            return sound;
        }
        
        // Fallback to default hitsounds
        sound = defaultHitsounds.get(key);
        if (sound != null) {
            logger.debug("Using default hitsound for: {}", key);
            return sound;
        }
        
        logger.warn("No hitsound found for: {}", key);
        return null;
    }
    
    
    /**
     * Generate hitsound players from an arrangement.json file.
     * Returns a list of MediaPlayer objects with proper timing.
     */
    public CompletableFuture<List<TimedHitsound>> generateHitsoundsFromArrangement(Path arrangementPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Generating hitsounds from: {}", arrangementPath);
                
                // Parse arrangement.json
                JsonNode root = objectMapper.readTree(arrangementPath.toFile());
                JsonNode hitObjects = root.get("hit_objects");
                
                if (hitObjects == null || !hitObjects.isArray()) {
                    logger.error("No hit_objects found in arrangement");
                    return Collections.emptyList();
                }
                
                List<TimedHitsound> timedHitsounds = new ArrayList<>();
                
                for (JsonNode obj : hitObjects) {
                    long timeMs = obj.get("time").asLong();
                    String sampleSet = obj.has("sampleset") ? obj.get("sampleset").asText() : "normal";
                    
                    // EXACTLY LIKE PYTHON: Always add hitnormal if it exists
                    // Python: if hitnormal_key in skin_hitsounds
                    String hitnormalKey = sampleSet + "-hitnormal";
                    Media hitnormal = getHitsound(hitnormalKey);
                    if (hitnormal != null) {
                        timedHitsounds.add(new TimedHitsound(hitnormal, timeMs, hitnormalKey));
                    }
                    
                    // EXACTLY LIKE PYTHON: Add additions based on flags
                    // Python: if obj.get('has_whistle')
                    if (obj.has("has_whistle") && obj.get("has_whistle").asBoolean()) {
                        String whistleKey = sampleSet + "-hitwhistle";
                        Media whistle = getHitsound(whistleKey);
                        if (whistle != null) {
                            timedHitsounds.add(new TimedHitsound(whistle, timeMs, whistleKey));
                        }
                    }
                    
                    // Python: if obj.get('has_clap')
                    if (obj.has("has_clap") && obj.get("has_clap").asBoolean()) {
                        String clapKey = sampleSet + "-hitclap";
                        Media clap = getHitsound(clapKey);
                        if (clap != null) {
                            timedHitsounds.add(new TimedHitsound(clap, timeMs, clapKey));
                        }
                    }
                    
                    // Python: if obj.get('has_finish')
                    if (obj.has("has_finish") && obj.get("has_finish").asBoolean()) {
                        String finishKey = sampleSet + "-hitfinish";
                        Media finish = getHitsound(finishKey);
                        if (finish != null) {
                            timedHitsounds.add(new TimedHitsound(finish, timeMs, finishKey));
                        }
                    }
                    
                    // NOTE: Python script doesn't handle sliders, but we'll keep this as an enhancement
                    // The Python script only handles the basic hit objects
                    // Commenting out slider handling to match Python exactly
                    /*
                    if (obj.has("type") && "slider".equals(obj.get("type").asText())) {
                        // Add slider tick sounds if needed
                        if (obj.has("slider_ticks")) {
                            JsonNode ticks = obj.get("slider_ticks");
                            if (ticks.isArray()) {
                                String tickKey = sampleSet + "-slidertick";
                                Media tick = getHitsound(tickKey);
                                if (tick != null) {
                                    for (JsonNode tickTime : ticks) {
                                        timedHitsounds.add(new TimedHitsound(tick, tickTime.asLong(), tickKey));
                                    }
                                }
                            }
                        }
                    }
                    */
                }
                
                // Sort by time
                timedHitsounds.sort(Comparator.comparingLong(h -> h.timeMs));
                
                logger.info("Generated {} timed hitsounds", timedHitsounds.size());
                return timedHitsounds;
                
            } catch (IOException e) {
                logger.error("Failed to parse arrangement file", e);
                return Collections.emptyList();
            }
        });
    }
    
    /**
     * Clear all cached hitsounds (except defaults).
     */
    public void clearCache() {
        skinHitsounds.clear();
        currentSkinPath = null;
    }
    
    /**
     * Container for a hitsound with its timing information.
     */
    public static class TimedHitsound {
        public final Media sound;
        public final long timeMs;
        public final String name;
        
        public TimedHitsound(Media sound, long timeMs, String name) {
            this.sound = sound;
            this.timeMs = timeMs;
            this.name = name;
        }
        
        /**
         * Create a MediaPlayer for this hitsound.
         * The player is not started automatically.
         * 
         * IMPORTANT: Python script uses volume=2.0 for each hitsound,
         * then volume=3.0 for final mix. Since we can't do post-mix amplification
         * in JavaFX, we need to compensate by adjusting individual volumes.
         * Python effective volume per sound = 2.0 * 3.0 = 6.0x amplification
         */
        public MediaPlayer createPlayer(double volume) {
            MediaPlayer player = new MediaPlayer(sound);
            // Match Python's amplification: 2.0 * 3.0 = 6.0
            // But JavaFX caps at 1.0, so we use the full volume range
            // User's volume slider will scale this
            player.setVolume(Math.min(1.0, volume));
            return player;
        }
    }
}