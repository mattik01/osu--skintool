package com.osuskin.tool.service;

import com.osuskin.tool.model.SkinElementRegistry;
import com.osuskin.tool.model.SkinElementRegistry.ElementDefinition;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for loading skin elements with support for multiple file formats and animations.
 * Handles missing elements gracefully and provides fallback to defaults.
 */
public class SkinElementLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(SkinElementLoader.class);
    
    // Supported file extensions
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("wav", "ogg", "mp3");
    
    // Cache for loaded resources
    private final Map<String, Image> imageCache = new HashMap<>();
    private final Map<String, Media> audioCache = new HashMap<>();
    private final Map<String, List<Image>> animationCache = new HashMap<>();
    
    private Path skinDirectory;
    private Path defaultSkinDirectory;
    private static final String DEFAULT_SKIN_RESOURCE_PATH = "/default-skin/";
    private com.osuskin.tool.model.Skin currentSkin;
    
    public SkinElementLoader(Path skinDirectory) {
        this.skinDirectory = skinDirectory;
        // TODO: Set default skin directory from application resources
    }
    
    public void setSkinDirectory(Path skinDirectory) {
        this.skinDirectory = skinDirectory;
        this.currentSkin = null;  // Reset current skin
        clearCache();
    }
    
    public void setCurrentSkin(com.osuskin.tool.model.Skin skin) {
        this.currentSkin = skin;
    }
    
    public com.osuskin.tool.model.Skin getCurrentSkin() {
        return currentSkin;
    }
    
    public void setDefaultSkinDirectory(Path defaultSkinDirectory) {
        this.defaultSkinDirectory = defaultSkinDirectory;
    }
    
    public void clearCache() {
        imageCache.clear();
        audioCache.clear();
        animationCache.clear();
    }
    
    /**
     * Load an image element, trying different extensions and HD versions.
     * Falls back to default skin if not found.
     */
    public Image loadImage(String elementName) {
        if (imageCache.containsKey(elementName)) {
            return imageCache.get(elementName);
        }
        
        Image image = null;
        
        // Try HD version first (@2x)
        image = tryLoadImage(elementName + "@2x");
        
        // Try standard version
        if (image == null) {
            image = tryLoadImage(elementName);
        }
        
        // Try default skin from file system if configured
        if (image == null && defaultSkinDirectory != null) {
            image = tryLoadImageFromDirectory(defaultSkinDirectory, elementName);
        }
        
        // Try default skin from resources (bundled with application)
        if (image == null) {
            image = tryLoadImageFromResources(elementName);
        }
        
        if (image != null) {
            imageCache.put(elementName, image);
        } else {
            logger.debug("Could not load image element: {}", elementName);
        }
        
        return image;
    }
    
    /**
     * Load an animated element, returning all frames in sequence.
     */
    public List<Image> loadAnimation(String elementName) {
        if (animationCache.containsKey(elementName)) {
            return animationCache.get(elementName);
        }
        
        List<Image> frames = new ArrayList<>();
        
        // First try to load base image (frame 0 or no suffix)
        Image baseImage = loadImage(elementName);
        if (baseImage != null) {
            frames.add(baseImage);
        }
        
        // Try to load numbered frames with different naming conventions
        int frameIndex = 0;
        while (true) {
            Image frame = null;
            
            // Try different naming patterns
            // Pattern 1: elementName-0, elementName-1, etc. (standard)
            String frameName = elementName + "-" + frameIndex;
            frame = tryLoadImage(frameName);
            
            // Pattern 2: elementName0, elementName1, etc. (no hyphen)
            if (frame == null) {
                frameName = elementName + frameIndex;
                frame = tryLoadImage(frameName);
            }
            
            // Also try @2x versions
            if (frame == null) {
                frame = tryLoadImage(elementName + "-" + frameIndex + "@2x");
            }
            if (frame == null) {
                frame = tryLoadImage(elementName + frameIndex + "@2x");
            }
            
            // Try loading from resources if not found in skin
            if (frame == null) {
                frame = tryLoadImageFromResources(elementName + frameIndex);
            }
            
            if (frame != null) {
                if (frameIndex == 0 && !frames.isEmpty()) {
                    // Replace base image with frame 0 if it exists
                    frames.set(0, frame);
                } else {
                    frames.add(frame);
                }
                frameIndex++;
            } else if (frameIndex > 0) {
                // Stop when we can't find the next frame
                break;
            } else {
                // No frame 0, move to frame 1
                frameIndex = 1;
            }
            
            // Prevent infinite loops
            if (frameIndex > 100) {
                logger.warn("Too many animation frames for element: {}", elementName);
                break;
            }
        }
        
        if (!frames.isEmpty()) {
            animationCache.put(elementName, frames);
            logger.debug("Loaded {} animation frames for element: {}", frames.size(), elementName);
        }
        
        return frames;
    }
    
    /**
     * Load an audio element, trying different extensions.
     */
    public Media loadAudio(String elementName) {
        if (audioCache.containsKey(elementName)) {
            return audioCache.get(elementName);
        }
        
        Media audio = tryLoadAudio(elementName);
        
        // Try default skin from file system if configured
        if (audio == null && defaultSkinDirectory != null) {
            audio = tryLoadAudioFromDirectory(defaultSkinDirectory, elementName);
        }
        
        // Try default skin from resources (bundled with application)
        if (audio == null) {
            audio = tryLoadAudioFromResources(elementName);
        }
        
        if (audio != null) {
            audioCache.put(elementName, audio);
        } else {
            logger.debug("Could not load audio element: {}", elementName);
        }
        
        return audio;
    }
    
    /**
     * Load multiple audio elements and return them as a list.
     * Useful for loading sets of hitsounds.
     */
    public List<Media> loadAudioSet(String... elementNames) {
        List<Media> audioList = new ArrayList<>();
        
        for (String elementName : elementNames) {
            Media audio = loadAudio(elementName);
            if (audio != null) {
                audioList.add(audio);
            }
        }
        
        return audioList;
    }
    
    /**
     * Check if an element exists in the skin (any supported format).
     */
    public boolean elementExists(String elementName) {
        // Check for images
        for (String ext : IMAGE_EXTENSIONS) {
            Path imagePath = skinDirectory.resolve(elementName + "." + ext);
            if (Files.exists(imagePath)) {
                return true;
            }
            // Check HD version
            imagePath = skinDirectory.resolve(elementName + "@2x." + ext);
            if (Files.exists(imagePath)) {
                return true;
            }
        }
        
        // Check for audio
        for (String ext : AUDIO_EXTENSIONS) {
            Path audioPath = skinDirectory.resolve(elementName + "." + ext);
            if (Files.exists(audioPath)) {
                return true;
            }
        }
        
        // Check for animated frames
        for (String ext : IMAGE_EXTENSIONS) {
            Path framePath = skinDirectory.resolve(elementName + "-0." + ext);
            if (Files.exists(framePath)) {
                return true;
            }
            Path framePath1 = skinDirectory.resolve(elementName + "-1." + ext);
            if (Files.exists(framePath1)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get a list of all elements present in the skin, categorized.
     */
    public Map<SkinElementRegistry.ElementCategory, List<String>> getCategorizedElements() {
        Map<SkinElementRegistry.ElementCategory, List<String>> categorized = new HashMap<>();
        
        try {
            List<Path> allFiles = Files.list(skinDirectory)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());
            
            for (Path file : allFiles) {
                String fileName = file.getFileName().toString();
                ElementDefinition def = SkinElementRegistry.getDefinition(fileName);
                
                if (def != null) {
                    categorized.computeIfAbsent(def.getCategory(), k -> new ArrayList<>())
                        .add(fileName);
                }
            }
        } catch (Exception e) {
            logger.error("Error categorizing skin elements", e);
        }
        
        return categorized;
    }
    
    // Private helper methods
    
    private Image tryLoadImage(String elementName) {
        return tryLoadImageFromDirectory(skinDirectory, elementName);
    }
    
    private Image tryLoadImageFromDirectory(Path directory, String elementName) {
        // Try each image extension with both exact case and case-insensitive search
        for (String ext : IMAGE_EXTENSIONS) {
            // First try exact case
            Path imagePath = directory.resolve(elementName + "." + ext);
            if (Files.exists(imagePath)) {
                try {
                    logger.debug("Found image file: {} with extension: {}", elementName, ext);
                    Image image = new Image(imagePath.toUri().toString());
                    // Verify the image loaded correctly
                    if (!image.isError()) {
                        return image;
                    } else {
                        logger.warn("Image error for {}: {}", imagePath, image.getException());
                    }
                } catch (Exception e) {
                    logger.error("Error loading image: {}", imagePath, e);
                }
            }
            
            // Try case-insensitive search if exact match fails
            Path foundPath = findFileIgnoreCase(directory, elementName + "." + ext);
            if (foundPath != null) {
                try {
                    logger.debug("Found image file (case-insensitive): {}", foundPath.getFileName());
                    Image image = new Image(foundPath.toUri().toString());
                    if (!image.isError()) {
                        return image;
                    }
                } catch (Exception e) {
                    logger.error("Error loading image: {}", foundPath, e);
                }
            }
        }
        
        // Log which formats were checked for debugging
        logger.debug("Image not found for '{}' in directory: {}. Checked extensions: {}", 
                    elementName, directory, IMAGE_EXTENSIONS);
        return null;
    }
    
    private Media tryLoadAudio(String elementName) {
        return tryLoadAudioFromDirectory(skinDirectory, elementName);
    }
    
    private Media tryLoadAudioFromDirectory(Path directory, String elementName) {
        // Try each audio extension with both exact case and case-insensitive search
        for (String ext : AUDIO_EXTENSIONS) {
            // First try exact case
            Path audioPath = directory.resolve(elementName + "." + ext);
            if (Files.exists(audioPath)) {
                try {
                    logger.debug("Found audio file: {} with extension: {}", elementName, ext);
                    Media media = new Media(audioPath.toUri().toString());
                    // Verify the media loaded correctly
                    if (media.getError() == null) {
                        return media;
                    } else {
                        logger.warn("Media error for {}: {}", audioPath, media.getError());
                    }
                } catch (Exception e) {
                    logger.error("Error loading audio: {}", audioPath, e);
                }
            }
            
            // Try case-insensitive search if exact match fails
            Path foundPath = findFileIgnoreCase(directory, elementName + "." + ext);
            if (foundPath != null) {
                try {
                    logger.debug("Found audio file (case-insensitive): {}", foundPath.getFileName());
                    Media media = new Media(foundPath.toUri().toString());
                    if (media.getError() == null) {
                        return media;
                    }
                } catch (Exception e) {
                    logger.error("Error loading audio: {}", foundPath, e);
                }
            }
        }
        
        // Log which formats were checked
        logger.debug("Audio not found for '{}' in directory: {}. Checked extensions: {}", 
                    elementName, directory, AUDIO_EXTENSIONS);
        return null;
    }
    
    /**
     * Try to load an image from bundled resources (default skin).
     */
    private Image tryLoadImageFromResources(String elementName) {
        for (String ext : IMAGE_EXTENSIONS) {
            String resourcePath = DEFAULT_SKIN_RESOURCE_PATH + elementName + "." + ext;
            try {
                URL resource = getClass().getResource(resourcePath);
                if (resource != null) {
                    return new Image(resource.toExternalForm());
                }
            } catch (Exception e) {
                // Try next extension
            }
        }
        return null;
    }
    
    /**
     * Try to load audio from bundled resources (default skin).
     */
    private Media tryLoadAudioFromResources(String elementName) {
        for (String ext : AUDIO_EXTENSIONS) {
            String resourcePath = DEFAULT_SKIN_RESOURCE_PATH + elementName + "." + ext;
            try {
                URL resource = getClass().getResource(resourcePath);
                if (resource != null) {
                    return new Media(resource.toExternalForm());
                }
            } catch (Exception e) {
                // Try next extension
            }
        }
        return null;
    }
    
    /**
     * Get statistics about element coverage in the skin.
     */
    public SkinElementStats getElementStats() {
        SkinElementStats stats = new SkinElementStats();
        
        // Count required elements
        for (ElementDefinition def : SkinElementRegistry.getRequiredElements()) {
            if (elementExists(def.getBaseName())) {
                stats.presentRequiredElements++;
            }
            stats.totalRequiredElements++;
        }
        
        // Count by category
        Map<SkinElementRegistry.ElementCategory, List<String>> categorized = getCategorizedElements();
        for (Map.Entry<SkinElementRegistry.ElementCategory, List<String>> entry : categorized.entrySet()) {
            stats.elementsByCategory.put(entry.getKey(), entry.getValue().size());
        }
        
        return stats;
    }
    
    /**
     * Find a file in a directory with case-insensitive matching.
     * Returns the actual path if found, null otherwise.
     */
    private Path findFileIgnoreCase(Path directory, String fileName) {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return null;
        }
        
        String lowerFileName = fileName.toLowerCase();
        try {
            return Files.list(directory)
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase().equals(lowerFileName))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            logger.debug("Error searching for file case-insensitive: {}", fileName, e);
            return null;
        }
    }
    
    public static class SkinElementStats {
        public int totalRequiredElements = 0;
        public int presentRequiredElements = 0;
        public Map<SkinElementRegistry.ElementCategory, Integer> elementsByCategory = new HashMap<>();
        
        public double getRequiredElementCoverage() {
            if (totalRequiredElements == 0) return 0;
            return (double) presentRequiredElements / totalRequiredElements * 100;
        }
    }
}