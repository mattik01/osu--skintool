package com.osuskin.tool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Caches skin manifests to disk for fast loading.
 */
public class ManifestCache {
    private static final Logger logger = LoggerFactory.getLogger(ManifestCache.class);
    
    // Cache directory
    private static final Path CACHE_DIR = getCacheDirectory();
    private static final String CACHE_FILE_EXTENSION = ".manifest";
    
    static {
        // Ensure cache directory exists
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException e) {
            logger.error("Failed to create manifest cache directory", e);
        }
    }
    
    /**
     * Load a cached manifest for a skin directory.
     */
    public static SkinElementManifest loadManifest(Path skinDirectory) {
        if (skinDirectory == null) return null;
        
        try {
            Path cacheFile = getCacheFile(skinDirectory);
            if (!Files.exists(cacheFile)) {
                return null;
            }
            
            // Check if cache file is stale
            long skinModTime = skinDirectory.toFile().lastModified();
            long cacheModTime = cacheFile.toFile().lastModified();
            
            if (skinModTime > cacheModTime) {
                logger.debug("Cache file is stale for: {}", skinDirectory);
                return null;
            }
            
            // Load manifest from cache
            try (ObjectInputStream ois = new ObjectInputStream(
                    new BufferedInputStream(Files.newInputStream(cacheFile)))) {
                
                SkinElementManifest manifest = (SkinElementManifest) ois.readObject();
                
                // Validate it's for the correct skin
                if (!skinDirectory.toString().equals(manifest.getSkinPath())) {
                    logger.warn("Manifest path mismatch: expected {}, got {}", 
                        skinDirectory, manifest.getSkinPath());
                    return null;
                }
                
                logger.debug("Loaded cached manifest for: {}", skinDirectory);
                return manifest;
                
            } catch (ClassNotFoundException | InvalidClassException e) {
                // Manifest format changed, need to rebuild
                logger.debug("Manifest format incompatible, will rebuild: {}", e.getMessage());
                Files.deleteIfExists(cacheFile);
                return null;
            }
            
        } catch (IOException e) {
            logger.debug("Failed to load cached manifest for: {}", skinDirectory, e);
            return null;
        }
    }
    
    /**
     * Save a manifest to cache.
     */
    public static void saveManifest(Path skinDirectory, SkinElementManifest manifest) {
        if (skinDirectory == null || manifest == null) return;
        
        try {
            Path cacheFile = getCacheFile(skinDirectory);
            
            // Write to temporary file first
            Path tempFile = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
            
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                
                oos.writeObject(manifest);
                oos.flush();
            }
            
            // Atomic move to final location
            Files.move(tempFile, cacheFile, 
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            
            logger.debug("Saved manifest to cache for: {}", skinDirectory);
            
        } catch (IOException e) {
            logger.error("Failed to save manifest to cache for: {}", skinDirectory, e);
        }
    }
    
    /**
     * Invalidate cached manifest for a skin directory.
     */
    public static void invalidateManifest(Path skinDirectory) {
        if (skinDirectory == null) return;
        
        try {
            Path cacheFile = getCacheFile(skinDirectory);
            if (Files.exists(cacheFile)) {
                Files.delete(cacheFile);
                logger.debug("Invalidated cached manifest for: {}", skinDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to invalidate cached manifest for: {}", skinDirectory, e);
        }
    }
    
    /**
     * Clear all cached manifests.
     */
    public static void clearAllCaches() {
        try {
            Files.walk(CACHE_DIR)
                .filter(path -> path.toString().endsWith(CACHE_FILE_EXTENSION))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.debug("Failed to delete cache file: {}", path, e);
                    }
                });
            logger.info("Cleared all manifest caches");
        } catch (IOException e) {
            logger.error("Failed to clear manifest caches", e);
        }
    }
    
    /**
     * Get the cache file path for a skin directory.
     */
    private static Path getCacheFile(Path skinDirectory) {
        // Create a unique filename based on the skin path
        String skinPath = skinDirectory.toAbsolutePath().toString();
        String hash = hashString(skinPath);
        return CACHE_DIR.resolve(hash + CACHE_FILE_EXTENSION);
    }
    
    /**
     * Hash a string to create a unique filename.
     */
    private static String hashString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            // Use base64 URL-safe encoding for filename
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
                .substring(0, 16); // Use first 16 chars for reasonable length
        } catch (Exception e) {
            // Fallback to simple hash
            return String.valueOf(input.hashCode());
        }
    }
    
    /**
     * Get the cache directory based on platform.
     */
    private static Path getCacheDirectory() {
        String userHome = System.getProperty("user.home");
        String appName = "osu-skintool";
        
        String os = System.getProperty("os.name").toLowerCase();
        Path cacheDir;
        
        if (os.contains("win")) {
            // Windows: %LOCALAPPDATA%\osu-skintool\cache
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                cacheDir = Paths.get(localAppData, appName, "cache", "manifests");
            } else {
                cacheDir = Paths.get(userHome, "AppData", "Local", appName, "cache", "manifests");
            }
        } else if (os.contains("mac")) {
            // macOS: ~/Library/Caches/osu-skintool
            cacheDir = Paths.get(userHome, "Library", "Caches", appName, "manifests");
        } else {
            // Linux/Unix: ~/.cache/osu-skintool
            cacheDir = Paths.get(userHome, ".cache", appName, "manifests");
        }
        
        return cacheDir;
    }
    
    /**
     * Get cache statistics.
     */
    public static String getStats() {
        try {
            long count = Files.walk(CACHE_DIR)
                .filter(path -> path.toString().endsWith(CACHE_FILE_EXTENSION))
                .count();
            
            long totalSize = Files.walk(CACHE_DIR)
                .filter(path -> path.toString().endsWith(CACHE_FILE_EXTENSION))
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
            
            return String.format("ManifestCache[files=%d, size=%.2fMB]", 
                count, totalSize / (1024.0 * 1024.0));
        } catch (IOException e) {
            return "ManifestCache[unavailable]";
        }
    }
}