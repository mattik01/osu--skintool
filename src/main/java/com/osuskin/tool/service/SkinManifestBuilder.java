package com.osuskin.tool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a complete manifest of skin elements with exact paths and precomputed animations.
 */
public class SkinManifestBuilder {
    private static final Logger logger = LoggerFactory.getLogger(SkinManifestBuilder.class);
    
    // File extensions to scan
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("wav", "ogg", "mp3");
    
    // Maximum frames to load for animations
    private static final int MAX_ANIMATION_FRAMES = 10;
    
    // Critical elements that should be loaded first
    private static final Set<String> CRITICAL_PREFIXES = Set.of(
        "default-", "score-", "combo-", 
        "hitcircle", "cursor", "approachcircle",
        "hit0", "hit50", "hit100", "hit300"
    );
    
    // Required elements that must exist (use fallback if missing)
    private static final Set<String> REQUIRED_ELEMENTS = Set.of(
        // Numbers
        "default-0", "default-1", "default-2", "default-3", "default-4",
        "default-5", "default-6", "default-7", "default-8", "default-9",
        // Hit circles
        "hitcircle", "hitcircleoverlay", "approachcircle",
        // Cursor
        "cursor", "cursortrail",
        // Sliders
        "sliderb", "sliderfollowcircle",
        // Hit bursts (at least base versions)
        "hit0", "hit50", "hit100", "hit300"
    );
    
    /**
     * Build a complete manifest for a skin directory.
     */
    public static SkinElementManifest buildManifest(Path skinDirectory) {
        return buildManifest(skinDirectory, null);
    }
    
    /**
     * Build a complete manifest for a skin directory with progress reporting.
     */
    public static SkinElementManifest buildManifest(Path skinDirectory, 
            java.util.function.Consumer<SkinElementLoader.ManifestProgress> progressCallback) {
        if (skinDirectory == null || !Files.exists(skinDirectory)) {
            logger.warn("Cannot build manifest for non-existent directory: {}", skinDirectory);
            return null;
        }
        
        logger.info("Building manifest for: {}", skinDirectory);
        long startTime = System.currentTimeMillis();
        
        SkinElementManifest manifest = new SkinElementManifest(skinDirectory.toString());
        
        try {
            // Report progress: Starting scan
            if (progressCallback != null) {
                progressCallback.accept(new SkinElementLoader.ManifestProgress(
                    false, true, 10, "Scanning directory..."));
            }
            
            // Get directory metadata
            long dirModTime = skinDirectory.toFile().lastModified();
            
            // Scan all files in directory
            Map<String, Path> allFiles = scanDirectory(skinDirectory);
            manifest.setMetadata(dirModTime, allFiles.size());
            
            // Report progress: Processing elements
            if (progressCallback != null) {
                progressCallback.accept(new SkinElementLoader.ManifestProgress(
                    false, true, 40, "Processing " + allFiles.size() + " files..."));
            }
            
            // Process regular elements
            processRegularElements(allFiles, manifest);
            
            // Report progress: Processing animations
            if (progressCallback != null) {
                progressCallback.accept(new SkinElementLoader.ManifestProgress(
                    false, true, 70, "Processing animations..."));
            }
            
            // Process animations with smart frame selection
            processAnimations(allFiles, manifest);
            
            // Report progress: Finalizing
            if (progressCallback != null) {
                progressCallback.accept(new SkinElementLoader.ManifestProgress(
                    false, true, 90, "Finalizing manifest..."));
            }
            
            // Mark required elements that need fallback
            markFallbackElements(manifest);
            
            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Manifest built in {}ms: {} elements, {} animations", 
                elapsed, manifest.getTotalElementCount(), manifest.animationFrames.size());
            
            // Report progress: Complete
            if (progressCallback != null) {
                progressCallback.accept(new SkinElementLoader.ManifestProgress(
                    false, false, 100, 
                    String.format("Manifest built (%d elements)", manifest.getTotalElementCount())));
            }
            
        } catch (Exception e) {
            logger.error("Failed to build manifest for: " + skinDirectory, e);
            return null;
        }
        
        return manifest;
    }
    
    /**
     * Scan directory and build a map of lowercase names to actual paths.
     */
    private static Map<String, Path> scanDirectory(Path directory) throws IOException {
        Map<String, Path> fileMap = new HashMap<>();
        
        Files.walk(directory, 1) // Only scan immediate directory
            .filter(Files::isRegularFile)
            .forEach(path -> {
                String fileName = path.getFileName().toString();
                String nameLower = fileName.toLowerCase();
                
                // Remove extension for key
                int dotIndex = nameLower.lastIndexOf('.');
                if (dotIndex > 0) {
                    String extension = nameLower.substring(dotIndex + 1);
                    if (IMAGE_EXTENSIONS.contains(extension) || AUDIO_EXTENSIONS.contains(extension)) {
                        String baseName = fileName.substring(0, dotIndex);
                        String baseNameLower = baseName.toLowerCase();
                        
                        // Store with lowercase key for case-insensitive lookup
                        // But keep the actual path for loading
                        fileMap.put(baseNameLower, path);
                    }
                }
            });
        
        return fileMap;
    }
    
    /**
     * Process regular (non-animated) elements.
     */
    private static void processRegularElements(Map<String, Path> files, SkinElementManifest manifest) {
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String elementName = entry.getKey();
            Path filePath = entry.getValue();
            
            // Skip numbered animation frames (we'll handle these separately)
            if (isAnimationFrame(elementName)) {
                continue;
            }
            
            // Determine if this is a critical element
            boolean isCritical = isCriticalElement(elementName);
            
            // Add to manifest with exact path
            manifest.addElement(elementName, filePath.toUri().toString(), isCritical);
        }
    }
    
    /**
     * Process animations and precompute frame selection.
     */
    private static void processAnimations(Map<String, Path> files, SkinElementManifest manifest) {
        // Group files by animation base name
        Map<String, List<AnimationFrame>> animations = new HashMap<>();
        
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String elementName = entry.getKey();
            AnimationFrame frame = parseAnimationFrame(elementName, entry.getValue());
            if (frame != null) {
                animations.computeIfAbsent(frame.baseName, k -> new ArrayList<>()).add(frame);
            }
        }
        
        // Process each animation
        for (Map.Entry<String, List<AnimationFrame>> entry : animations.entrySet()) {
            String baseName = entry.getKey();
            List<AnimationFrame> frames = entry.getValue();
            
            // Sort frames by number
            frames.sort(Comparator.comparingInt(f -> f.frameNumber));
            
            // Select frames to load
            List<String> selectedFramePaths = selectAnimationFrames(baseName, frames);
            
            if (!selectedFramePaths.isEmpty()) {
                manifest.addAnimation(baseName, selectedFramePaths);
                
                // Also add individual frames to element map for direct access
                for (String framePath : selectedFramePaths) {
                    String frameElementName = extractElementNameFromPath(framePath);
                    boolean isCritical = isCriticalElement(baseName);
                    manifest.addElement(frameElementName, framePath, isCritical);
                }
            }
        }
    }
    
    /**
     * Smart frame selection for animations.
     * IMPORTANT: Always use what the skin provides, even if it's just 1 frame.
     */
    private static List<String> selectAnimationFrames(String baseName, List<AnimationFrame> frames) {
        if (frames.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> selectedPaths = new ArrayList<>();
        
        // If skin only has 1 frame for an "animation", that's fine - use it
        if (frames.size() == 1) {
            selectedPaths.add(frames.get(0).path.toUri().toString());
            logger.debug("{} has single frame animation", baseName);
            return selectedPaths;
        }
        
        // Special handling for scorebar-colour (can have 300+ frames)
        if (baseName.equals("scorebar-colour") && frames.size() > MAX_ANIMATION_FRAMES) {
            // Sample evenly across the range
            double interval = (double)(frames.size() - 1) / (MAX_ANIMATION_FRAMES - 1);
            for (int i = 0; i < MAX_ANIMATION_FRAMES; i++) {
                int frameIndex = (int)(i * interval);
                selectedPaths.add(frames.get(frameIndex).path.toUri().toString());
            }
            logger.debug("Sampled {} frames from {} total for {}", 
                MAX_ANIMATION_FRAMES, frames.size(), baseName);
        } else if (frames.size() <= MAX_ANIMATION_FRAMES) {
            // Load all frames if within limit
            for (AnimationFrame frame : frames) {
                selectedPaths.add(frame.path.toUri().toString());
            }
        } else {
            // Sample for other animations too
            double interval = (double)(frames.size() - 1) / (MAX_ANIMATION_FRAMES - 1);
            for (int i = 0; i < MAX_ANIMATION_FRAMES; i++) {
                int frameIndex = (int)(i * interval);
                selectedPaths.add(frames.get(frameIndex).path.toUri().toString());
            }
        }
        
        return selectedPaths;
    }
    
    /**
     * Check if an element name represents an animation frame.
     */
    private static boolean isAnimationFrame(String elementName) {
        // Check for patterns like "element-0", "element-1", "element0", "element1"
        return elementName.matches(".*-\\d+$") || 
               elementName.matches(".*\\d+$") && !elementName.matches(".*hit\\d+$"); // Exclude hit50, hit100, etc.
    }
    
    /**
     * Parse animation frame information from element name.
     */
    private static AnimationFrame parseAnimationFrame(String elementName, Path path) {
        // Try pattern "element-N"
        int dashIndex = elementName.lastIndexOf('-');
        if (dashIndex > 0) {
            String baseName = elementName.substring(0, dashIndex);
            String numberPart = elementName.substring(dashIndex + 1);
            try {
                int frameNumber = Integer.parseInt(numberPart);
                return new AnimationFrame(baseName, frameNumber, path);
            } catch (NumberFormatException e) {
                // Not a frame
            }
        }
        
        // Try pattern "elementN" (extract trailing number)
        for (int i = elementName.length() - 1; i >= 0; i--) {
            if (!Character.isDigit(elementName.charAt(i))) {
                if (i < elementName.length() - 1) {
                    String baseName = elementName.substring(0, i + 1);
                    String numberPart = elementName.substring(i + 1);
                    
                    // Skip if base name is like "hit" (hit50, hit100, etc.)
                    if (baseName.equals("hit")) {
                        return null;
                    }
                    
                    try {
                        int frameNumber = Integer.parseInt(numberPart);
                        return new AnimationFrame(baseName, frameNumber, path);
                    } catch (NumberFormatException e) {
                        // Not a frame
                    }
                }
                break;
            }
        }
        
        return null;
    }
    
    /**
     * Check if an element is critical (should be loaded first).
     */
    private static boolean isCriticalElement(String elementName) {
        for (String prefix : CRITICAL_PREFIXES) {
            if (elementName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Extract element name from a file path URI.
     */
    private static String extractElementNameFromPath(String pathUri) {
        try {
            Path path = Paths.get(new java.net.URI(pathUri));
            String fileName = path.getFileName().toString();
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                return fileName.substring(0, dotIndex).toLowerCase();
            }
            return fileName.toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Helper class to represent an animation frame.
     */
    private static class AnimationFrame {
        final String baseName;
        final int frameNumber;
        final Path path;
        
        AnimationFrame(String baseName, int frameNumber, Path path) {
            this.baseName = baseName;
            this.frameNumber = frameNumber;
            this.path = path;
        }
    }
    
    /**
     * Mark required elements that are missing and need fallback.
     */
    private static void markFallbackElements(SkinElementManifest manifest) {
        for (String requiredElement : REQUIRED_ELEMENTS) {
            if (!manifest.containsElement(requiredElement)) {
                // Element is missing, mark it for fallback
                manifest.addFallbackElement(requiredElement);
                logger.debug("Marking {} for fallback (not found in skin)", requiredElement);
            }
        }
    }
}