package com.osuskin.tool.service;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hybrid hitsound service that uses the Python script to generate hitsounds.
 * This guarantees perfect matching with the Python implementation.
 */
public class HybridHitsoundService {
    private static final Logger logger = LoggerFactory.getLogger(HybridHitsoundService.class);
    
    private final Path pythonScript;
    private final Path cacheDirectory;
    private final Path defaultSkinsPath;
    private final ConcurrentHashMap<String, Path> hitsoundCache = new ConcurrentHashMap<>();
    
    public HybridHitsoundService() {
        String currentDir = System.getProperty("user.dir");
        this.pythonScript = Paths.get(currentDir, "beatmap-hitsound-extractor", "generate_hitsounds.py");
        this.cacheDirectory = Paths.get(currentDir, "beatmap-hitsound-extractor", "hybrid-cache");
        this.defaultSkinsPath = Paths.get(currentDir, "src", "main", "resources", "default-skin");
        
        // Create cache directory if it doesn't exist
        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException e) {
            logger.error("Failed to create cache directory", e);
        }
    }
    
    /**
     * Generate hitsounds using the Python script.
     * 
     * @param arrangementPath Path to arrangement.json
     * @param skinPath Path to the current skin folder
     * @param sampleName Name of the sample for caching
     * @return Path to the generated MP3 file
     */
    public CompletableFuture<Path> generateHitsounds(Path arrangementPath, String skinPath, String sampleName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create cache key based on skin and sample
                Path skinDir = Paths.get(skinPath);
                String skinName = skinDir.getFileName().toString();
                String cacheKey = skinName + "_" + sampleName + "_hybrid";
                
                // Check cache first
                Path cachedPath = hitsoundCache.get(cacheKey);
                if (cachedPath != null && Files.exists(cachedPath)) {
                    logger.debug("Using cached hybrid hitsounds for {} with skin {}", sampleName, skinName);
                    return cachedPath;
                }
                
                // Output file path
                Path outputFile = cacheDirectory.resolve(cacheKey + ".mp3");
                
                // Check if Python script exists
                if (!Files.exists(pythonScript)) {
                    logger.error("Python script not found: {}", pythonScript);
                    return null;
                }
                
                logger.info("Generating hybrid hitsounds for {} with skin {}", sampleName, skinName);
                
                // Build command
                ProcessBuilder pb = new ProcessBuilder(
                    "python",
                    pythonScript.toString(),
                    arrangementPath.toString(),
                    skinPath,
                    defaultSkinsPath.toString(),
                    outputFile.toString()
                );
                
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                // Read output for debugging
                String output = new String(process.getInputStream().readAllBytes());
                logger.debug("Python output: {}", output);
                
                int exitCode = process.waitFor();
                
                if (exitCode == 0 && Files.exists(outputFile)) {
                    logger.info("Successfully generated hybrid hitsounds: {}", outputFile);
                    hitsoundCache.put(cacheKey, outputFile);
                    return outputFile;
                } else {
                    logger.error("Failed to generate hybrid hitsounds. Exit code: {}", exitCode);
                    if (!output.isEmpty()) {
                        logger.error("Python error output: {}", output);
                    }
                    return null;
                }
                
            } catch (Exception e) {
                logger.error("Error generating hybrid hitsounds", e);
                return null;
            }
        });
    }
    
    /**
     * Create a MediaPlayer from a generated hitsounds file.
     */
    public MediaPlayer createPlayer(Path hitsoundsPath, double volume) {
        if (hitsoundsPath == null || !Files.exists(hitsoundsPath)) {
            logger.error("Hitsounds file not found: {}", hitsoundsPath);
            return null;
        }
        
        try {
            Media media = new Media(hitsoundsPath.toUri().toString());
            MediaPlayer player = new MediaPlayer(media);
            player.setVolume(volume);
            return player;
        } catch (Exception e) {
            logger.error("Failed to create MediaPlayer for: {}", hitsoundsPath, e);
            return null;
        }
    }
    
    /**
     * Clear the cache.
     */
    public void clearCache() {
        hitsoundCache.clear();
        try {
            if (Files.exists(cacheDirectory)) {
                Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("_hybrid.mp3"))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            logger.warn("Failed to delete cached file: {}", p);
                        }
                    });
            }
        } catch (IOException e) {
            logger.error("Error clearing cache", e);
        }
    }
}