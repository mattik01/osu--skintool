package com.osuskin.tool.service;

import com.osuskin.tool.model.Arrangement;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Improved hitsound renderer that uses proper audio mixing like the Python implementation.
 * This renderer pre-mixes all hitsounds into a single audio stream for perfect synchronization.
 */
public class ImprovedHitsoundRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ImprovedHitsoundRenderer.class);
    
    // Volume multipliers matching Python implementation
    private static final double INDIVIDUAL_SOUND_MULTIPLIER = 2.0;
    private static final double FINAL_MIX_MULTIPLIER = 3.0;
    
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final AudioMixer audioMixer = new AudioMixer();
    private final Map<String, String> hitsoundFileCache = new ConcurrentHashMap<>();
    
    private SkinElementLoader elementLoader;
    private Path currentSkinPath;
    
    // Cache for pre-rendered arrangements
    private final Map<String, byte[]> renderedArrangementCache = new ConcurrentHashMap<>();
    
    public ImprovedHitsoundRenderer() {
        loadDefaultHitsoundPaths();
    }
    
    public void setElementLoader(SkinElementLoader elementLoader) {
        this.elementLoader = elementLoader;
    }
    
    public void setSkinPath(Path skinPath) {
        if (!Objects.equals(this.currentSkinPath, skinPath)) {
            logger.info("[HITSOUND-RENDERER] Skin path changed from {} to {}", 
                this.currentSkinPath, skinPath);
            this.currentSkinPath = skinPath;
            // Clear all caches when skin changes to force re-rendering
            clearCache();
            mapSkinHitsounds();
        } else {
            logger.debug("[HITSOUND-RENDERER] Skin path unchanged: {}", skinPath);
        }
    }
    
    /**
     * Load default hitsound file paths from resources.
     */
    private void loadDefaultHitsoundPaths() {
        String[] hitsoundNames = {
            "normal-hitnormal", "normal-hitclap", "normal-hitwhistle", "normal-hitfinish",
            "soft-hitnormal", "soft-hitclap", "soft-hitwhistle", "soft-hitfinish",
            "drum-hitnormal", "drum-hitclap", "drum-hitwhistle", "drum-hitfinish",
            "normal-slidertick", "soft-slidertick", "drum-slidertick",
            "normal-sliderslide", "soft-sliderslide", "drum-sliderslide"
        };
        
        logger.info("[HITSOUND-RENDERER] Loading default hitsounds...");
        int loadedCount = 0;
        
        for (String name : hitsoundNames) {
            // Check for default resources
            String[] extensions = {".wav", ".mp3", ".ogg"};
            for (String ext : extensions) {
                String resourcePath = "/default-hitsounds/" + name + ext;
                URL resource = getClass().getResource(resourcePath);
                if (resource != null) {
                    try {
                        // Convert to file path for AudioMixer
                        File tempFile = extractResourceToTemp(resourcePath, name + ext);
                        // Store with "default-" prefix to distinguish from skin hitsounds
                        hitsoundFileCache.put("default-" + name, tempFile.getAbsolutePath());
                        logger.info("[HITSOUND-RENDERER] Loaded default hitsound: {} -> {}", 
                            name, tempFile.getAbsolutePath());
                        loadedCount++;
                        break;
                    } catch (Exception e) {
                        logger.warn("[HITSOUND-RENDERER] Failed to extract default hitsound: {}", name, e);
                    }
                }
            }
        }
        
        logger.info("[HITSOUND-RENDERER] Loaded {} default hitsounds", loadedCount);
    }
    
    /**
     * Map skin hitsounds to file paths.
     */
    private void mapSkinHitsounds() {
        if (currentSkinPath == null || !Files.exists(currentSkinPath)) {
            logger.warn("[HITSOUND-RENDERER] Cannot map hitsounds - skin path is null or doesn't exist: {}", currentSkinPath);
            return;
        }
        
        logger.info("[HITSOUND-RENDERER] Mapping hitsounds for skin: {}", currentSkinPath);
        
        // Clear skin-specific entries first (keep defaults)
        Map<String, String> defaultsOnly = new HashMap<>();
        hitsoundFileCache.forEach((key, value) -> {
            if (value.contains("temp") || value.contains("default")) {
                defaultsOnly.put(key, value);
            }
        });
        hitsoundFileCache.clear();
        hitsoundFileCache.putAll(defaultsOnly);
        
        int mappedCount = 0;
        try {
            List<Path> files = Files.list(currentSkinPath)
                .filter(Files::isRegularFile)
                .toList();
                
            for (Path path : files) {
                String fileName = path.getFileName().toString().toLowerCase();
                if (!fileName.contains(".")) continue;
                
                String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
                
                // Check if it's a hitsound file
                if (isHitsoundFile(nameWithoutExt)) {
                    String oldValue = hitsoundFileCache.put(nameWithoutExt, path.toAbsolutePath().toString());
                    if (oldValue != null) {
                        logger.info("[HITSOUND-RENDERER] Replaced hitsound '{}': {} -> {}", 
                            nameWithoutExt, oldValue, path);
                    } else {
                        logger.info("[HITSOUND-RENDERER] Mapped skin hitsound: {} -> {}", 
                            nameWithoutExt, path);
                    }
                    mappedCount++;
                }
            }
            
            logger.info("[HITSOUND-RENDERER] Mapped {} hitsounds from skin. Total cache size: {}", 
                mappedCount, hitsoundFileCache.size());
            
            // Log what's missing
            String[] expectedHitsounds = {
                "normal-hitnormal", "normal-hitclap", "normal-hitwhistle", "normal-hitfinish",
                "soft-hitnormal", "soft-hitclap", "soft-hitwhistle", "soft-hitfinish"
            };
            
            for (String expected : expectedHitsounds) {
                if (!hitsoundFileCache.containsKey(expected)) {
                    logger.warn("[HITSOUND-RENDERER] Missing hitsound '{}' - will use default or silence", expected);
                }
            }
            
        } catch (IOException e) {
            logger.error("[HITSOUND-RENDERER] Failed to map skin hitsounds", e);
        }
    }
    
    /**
     * Check if a filename is a hitsound.
     */
    private boolean isHitsoundFile(String name) {
        return name.matches("(normal|soft|drum)-(hitnormal|hitclap|hitwhistle|hitfinish|slidertick|sliderslide)\\d*");
    }
    
    /**
     * Extract resource to temporary file for AudioMixer access.
     */
    private File extractResourceToTemp(String resourcePath, String fileName) throws IOException {
        File tempFile = File.createTempFile("hitsound_", "_" + fileName);
        tempFile.deleteOnExit();
        
        try (InputStream in = getClass().getResourceAsStream(resourcePath);
             OutputStream out = new FileOutputStream(tempFile)) {
            if (in != null) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
        
        return tempFile;
    }
    
    /**
     * Render hitsounds from arrangement into a mixed audio stream.
     * Returns the mixed audio data ready for playback.
     */
    public CompletableFuture<MixedAudio> renderArrangement(Arrangement arrangement) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check cache first
                String cacheKey = getCacheKey(arrangement);
                if (renderedArrangementCache.containsKey(cacheKey)) {
                    logger.info("Using cached render for arrangement");
                    return new MixedAudio(renderedArrangementCache.get(cacheKey), 10000);
                }
                
                // Collect all scheduled sounds
                List<AudioMixer.ScheduledSound> scheduledSounds = new ArrayList<>();
                
                for (Arrangement.HitObject hitObject : arrangement.getHitObjects()) {
                    int baseTime = hitObject.getTime();
                    
                    for (Arrangement.HitObject.Sound sound : hitObject.getSounds()) {
                        String soundFile = getHitsoundFile(
                            sound.getSound(),
                            sound.getSampleset(),
                            sound.getSampleIndex()
                        );
                        
                        if (soundFile != null) {
                            int timing = baseTime + sound.getTimeOffset();
                            float volume = sound.getVolume() / 100.0f;
                            
                            // Check if it's a continuous sound
                            if ("sliderslide".equals(sound.getSound()) && sound.getDuration() != null) {
                                // Create looped sound
                                scheduledSounds.add(new AudioMixer.ScheduledSound(
                                    soundFile, timing, sound.getDuration(), volume, true
                                ));
                            } else {
                                // Create one-shot sound
                                scheduledSounds.add(new AudioMixer.ScheduledSound(
                                    soundFile, timing, volume
                                ));
                            }
                        }
                    }
                }
                
                logger.info("Mixing {} hitsounds for arrangement", scheduledSounds.size());
                
                // Mix all sounds into a single audio stream
                int durationMs = 10000; // 10 seconds
                byte[] mixedAudio = audioMixer.mixSounds(scheduledSounds, durationMs);
                
                // Cache the result
                renderedArrangementCache.put(cacheKey, mixedAudio);
                
                return new MixedAudio(mixedAudio, durationMs);
                
            } catch (Exception e) {
                logger.error("Failed to render arrangement", e);
                return new MixedAudio(new byte[0], 0);
            }
        }, executorService);
    }
    
    /**
     * Get the actual sound file path with fallback chain.
     */
    private String getHitsoundFile(String soundType, String sampleset, int sampleIndex) {
        // Map sound names to file prefixes
        String prefix = mapSoundToPrefix(soundType);
        if (prefix == null) return null;
        
        // Build possible file names
        List<String> candidates = new ArrayList<>();
        
        // Try with custom sample index first
        if (sampleIndex > 0) {
            candidates.add(sampleset + "-" + prefix + sampleIndex);
        }
        
        // Standard name
        candidates.add(sampleset + "-" + prefix);
        
        // Fallback to other samplesets
        if (!"normal".equals(sampleset)) {
            candidates.add("normal-" + prefix);
        }
        if (!"soft".equals(sampleset)) {
            candidates.add("soft-" + prefix);
        }
        if (!"drum".equals(sampleset)) {
            candidates.add("drum-" + prefix);
        }
        
        // Find first available file
        for (String candidate : candidates) {
            String filePath = hitsoundFileCache.get(candidate);
            if (filePath != null && new File(filePath).exists()) {
                return filePath;
            }
        }
        
        logger.debug("No hitsound file found for: {} {} {}", soundType, sampleset, sampleIndex);
        return null;
    }
    
    /**
     * Map sound type to file prefix.
     */
    private String mapSoundToPrefix(String soundType) {
        switch (soundType) {
            case "hitnormal": return "hitnormal";
            case "whistle": return "hitwhistle";
            case "finish": return "hitfinish";
            case "clap": return "hitclap";
            case "slidertick": return "slidertick";
            case "sliderslide": return "sliderslide";
            default: return null;
        }
    }
    
    /**
     * Generate cache key for arrangement.
     */
    private String getCacheKey(Arrangement arrangement) {
        // Include skin path in cache key to ensure re-rendering when skin changes
        String skinId = currentSkinPath != null ? currentSkinPath.toString() : "default";
        return skinId + "_" + arrangement.getBeatmapFolder() + "_" + arrangement.getDifficultyName();
    }
    
    /**
     * Clear all caches.
     */
    public void clearCache() {
        logger.info("[HITSOUND-RENDERER] Clearing caches - Rendered arrangements: {}, AudioMixer cache size: {}", 
            renderedArrangementCache.size(), audioMixer.getCacheSize());
        
        // Clear rendered arrangements to force re-mixing with new skin
        renderedArrangementCache.clear();
        // Don't clear hitsoundFileCache here as it will be rebuilt by mapSkinHitsounds
        audioMixer.clearCache();
        // Also clear OGG decoder cache to free up disk space
        OggDecoder.clearCache();
        
        logger.info("[HITSOUND-RENDERER] Caches cleared successfully");
    }
    
    /**
     * Shutdown executor service.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
    
    /**
     * Container for mixed audio data.
     */
    public static class MixedAudio {
        private final byte[] audioData;
        private final int durationMs;
        
        public MixedAudio(byte[] audioData, int durationMs) {
            this.audioData = audioData;
            this.durationMs = durationMs;
        }
        
        /**
         * Create a new Clip for playback.
         * Creates a fresh clip instance each time to support looping.
         */
        public Clip createClip() throws LineUnavailableException, IOException {
            if (audioData.length > 0) {
                AudioFormat format = new AudioFormat(44100.0f, 16, 2, true, false);
                DataLine.Info info = new DataLine.Info(Clip.class, format);
                Clip newClip = (Clip) AudioSystem.getLine(info);
                
                ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                AudioInputStream audioIn = new AudioInputStream(bais, format, 
                    audioData.length / format.getFrameSize());
                
                newClip.open(audioIn);
                return newClip;
            }
            return null;
        }
        
        /**
         * Save to WAV file.
         */
        public void saveToWav(String outputPath) throws IOException {
            AudioFormat format = new AudioFormat(44100.0f, 16, 2, true, false);
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            AudioInputStream audioIn = new AudioInputStream(bais, format, 
                audioData.length / format.getFrameSize());
            
            AudioSystem.write(audioIn, AudioFileFormat.Type.WAVE, new File(outputPath));
        }
        
        public byte[] getAudioData() { return audioData; }
        public int getDurationMs() { return durationMs; }
    }
}