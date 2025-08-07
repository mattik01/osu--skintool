package com.osuskin.tool.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for detecting default osu! installation and skin paths
 */
public class OsuPathDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(OsuPathDetector.class);
    
    /**
     * Gets the most likely default osu! skins directory path for the current OS
     * @return Path to the default osu! skins directory, or null if not found
     */
    public static Path getDefaultOsuSkinsPath() {
        List<Path> possiblePaths = getPossibleOsuSkinsPaths();
        
        for (Path path : possiblePaths) {
            if (Files.exists(path) && Files.isDirectory(path)) {
                logger.info("Found existing osu! skins directory: {}", path);
                return path;
            }
        }
        
        // If no existing directory found, return the most likely default
        Path defaultPath = possiblePaths.isEmpty() ? null : possiblePaths.get(0);
        logger.info("No existing osu! skins directory found, suggesting default: {}", defaultPath);
        return defaultPath;
    }
    
    /**
     * Gets all possible osu! skins directory paths for the current OS
     * @return List of possible paths, ordered by likelihood
     */
    public static List<Path> getPossibleOsuSkinsPaths() {
        List<Path> paths = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        
        if (os.contains("win")) {
            // Windows paths
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                paths.add(Paths.get(localAppData, "osu!", "Skins"));
            }
            
            // Fallback to user profile
            paths.add(Paths.get(userHome, "AppData", "Local", "osu!", "Skins"));
            
            // Common installation directories
            paths.add(Paths.get("C:", "Users", System.getProperty("user.name"), "AppData", "Local", "osu!", "Skins"));
            paths.add(Paths.get("C:", "osu!", "Skins"));
            paths.add(Paths.get("D:", "osu!", "Skins"));
            
        } else if (os.contains("mac")) {
            // macOS paths
            paths.add(Paths.get(userHome, "Library", "Application Support", "osu!", "Skins"));
            paths.add(Paths.get(userHome, ".local", "share", "osu!", "Skins"));
            
        } else {
            // Linux and other Unix-like systems
            paths.add(Paths.get(userHome, ".local", "share", "osu!", "Skins"));
            paths.add(Paths.get(userHome, ".osu", "Skins"));
            paths.add(Paths.get(userHome, "osu!", "Skins"));
        }
        
        logger.debug("Generated {} possible osu! skins paths for OS: {}", paths.size(), os);
        return paths;
    }
    
    /**
     * Gets the default osu! installation directory for the current OS
     * @return Path to likely osu! installation directory, or null if not determinable
     */
    public static Path getDefaultOsuInstallPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                return Paths.get(localAppData, "osu!");
            }
            return Paths.get(userHome, "AppData", "Local", "osu!");
            
        } else if (os.contains("mac")) {
            return Paths.get(userHome, "Library", "Application Support", "osu!");
            
        } else {
            return Paths.get(userHome, ".local", "share", "osu!");
        }
    }
    
    /**
     * Checks if a given path looks like a valid osu! skins directory
     * @param path Path to check
     * @return true if the path appears to be a valid osu! skins directory
     */
    public static boolean isValidOsuSkinsDirectory(Path path) {
        if (path == null || !Files.exists(path) || !Files.isDirectory(path)) {
            return false;
        }
        
        try {
            // Check if it contains typical osu! skin folders or files
            return Files.list(path)
                    .anyMatch(subPath -> Files.isDirectory(subPath) && 
                             isLikelyOsuSkinFolder(subPath));
        } catch (Exception e) {
            logger.warn("Error checking if path is valid osu! skins directory: {}", path, e);
            return false;
        }
    }
    
    /**
     * Checks if a directory looks like an osu! skin folder
     * @param path Path to check
     * @return true if the path appears to be an osu! skin folder
     */
    private static boolean isLikelyOsuSkinFolder(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        
        try {
            // Check for common osu! skin files
            return Files.list(path)
                    .map(p -> p.getFileName().toString().toLowerCase())
                    .anyMatch(filename -> 
                        filename.equals("skin.ini") ||
                        filename.startsWith("hitcircle") ||
                        filename.startsWith("cursor") ||
                        filename.startsWith("default-") ||
                        filename.contains("hit") ||
                        filename.contains("score")
                    );
        } catch (Exception e) {
            return false;
        }
    }
}