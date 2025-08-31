package com.osuskin.tool.service;

import javafx.scene.image.Image;
import javafx.scene.media.Media;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Preloads and caches all default skin elements at application startup.
 * This eliminates all file I/O when falling back to defaults.
 */
public class DefaultSkinCache {
    private static final Logger logger = LoggerFactory.getLogger(DefaultSkinCache.class);
    private static final DefaultSkinCache INSTANCE = new DefaultSkinCache();
    
    // Cache for default images
    private final Map<String, Image> imageCache = new ConcurrentHashMap<>();
    
    // Cache for default audio
    private final Map<String, Media> audioCache = new ConcurrentHashMap<>();
    
    // Resource path for bundled defaults
    private static final String DEFAULT_SKIN_RESOURCE_PATH = "/default-skin/";
    
    // Default skin directory (if configured)
    private Path defaultSkinDirectory;
    
    // Loading state
    private volatile boolean loaded = false;
    private CompletableFuture<Void> loadingFuture;
    
    // Critical default elements that must be preloaded
    private static final Set<String> CRITICAL_DEFAULTS = Set.of(
        // Numbers
        "default-0", "default-1", "default-2", "default-3", "default-4",
        "default-5", "default-6", "default-7", "default-8", "default-9",
        "score-0", "score-1", "score-2", "score-3", "score-4",
        "score-5", "score-6", "score-7", "score-8", "score-9",
        "combo-0", "combo-1", "combo-2", "combo-3", "combo-4",
        "combo-5", "combo-6", "combo-7", "combo-8", "combo-9",
        "score-x", "score-percent", "combo-x",
        
        // Hit circles
        "hitcircle", "hitcircleoverlay", "approachcircle",
        
        // Cursor
        "cursor", "cursortrail", "cursormiddle",
        
        // Sliders
        "sliderstartcircle", "sliderstartcircleoverlay", 
        "sliderendcircle", "sliderendcircleoverlay",
        "sliderb", "sliderb0", "sliderfollowcircle", "reversearrow",
        
        // Hit bursts
        "hit0", "hit50", "hit100", "hit100k", "hit300", "hit300k", "hit300g",
        
        // Health bar
        "scorebar-bg", "scorebar-colour", "scorebar",
        "scorebar-marker", "scorebar-ki", "scorebar-kidanger", "scorebar-kidanger2",
        
        // Lighting
        "lighting"
    );
    
    // File extensions to try
    private static final String[] IMAGE_EXTENSIONS = {"png", "jpg", "jpeg"};
    private static final String[] AUDIO_EXTENSIONS = {"wav", "ogg", "mp3"};
    
    private DefaultSkinCache() {
        // Private constructor for singleton
    }
    
    public static DefaultSkinCache getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initialize and preload all default elements.
     * This should be called once at application startup.
     */
    public CompletableFuture<Void> initialize(Path defaultSkinDir) {
        if (loadingFuture != null) {
            return loadingFuture; // Already loading or loaded
        }
        
        this.defaultSkinDirectory = defaultSkinDir;
        
        logger.info("Initializing default skin cache...");
        long startTime = System.currentTimeMillis();
        
        loadingFuture = CompletableFuture.runAsync(() -> {
            try {
                preloadDefaultElements();
                loaded = true;
                
                long elapsed = System.currentTimeMillis() - startTime;
                logger.info("Default skin cache loaded in {}ms: {} images cached", 
                    elapsed, imageCache.size());
            } catch (Exception e) {
                logger.error("Failed to initialize default skin cache", e);
            }
        });
        
        return loadingFuture;
    }
    
    /**
     * Preload all critical default elements.
     */
    private void preloadDefaultElements() {
        // Load from resources first (bundled defaults)
        loadFromResources();
        
        // Then override with file system defaults if available
        if (defaultSkinDirectory != null && Files.exists(defaultSkinDirectory)) {
            loadFromDirectory(defaultSkinDirectory);
        }
    }
    
    /**
     * Load default elements from bundled resources.
     */
    private void loadFromResources() {
        for (String elementName : CRITICAL_DEFAULTS) {
            // Try to load as image
            Image image = tryLoadImageFromResources(elementName);
            if (image != null) {
                imageCache.put(elementName, image);
                continue;
            }
            
            // Try to load as audio
            Media audio = tryLoadAudioFromResources(elementName);
            if (audio != null) {
                audioCache.put(elementName, audio);
            }
        }
    }
    
    /**
     * Load default elements from file system directory.
     */
    private void loadFromDirectory(Path directory) {
        for (String elementName : CRITICAL_DEFAULTS) {
            // Try to load as image
            Image image = tryLoadImageFromDirectory(directory, elementName);
            if (image != null) {
                imageCache.put(elementName, image); // Override resource version
                continue;
            }
            
            // Try to load as audio
            Media audio = tryLoadAudioFromDirectory(directory, elementName);
            if (audio != null) {
                audioCache.put(elementName, audio); // Override resource version
            }
        }
    }
    
    /**
     * Try to load an image from resources.
     */
    private Image tryLoadImageFromResources(String elementName) {
        for (String ext : IMAGE_EXTENSIONS) {
            String resourcePath = DEFAULT_SKIN_RESOURCE_PATH + elementName + "." + ext;
            try {
                URL resource = getClass().getResource(resourcePath);
                if (resource != null) {
                    // Load with background loading, no size constraints
                    Image image = new Image(resource.toExternalForm(), true); // backgroundLoading only
                    
                    // Wait for background load to complete
                    while (image.getProgress() < 1.0 && !image.isError()) {
                        Thread.sleep(10);
                    }
                    
                    if (!image.isError()) {
                        return image;
                    }
                }
            } catch (Exception e) {
                // Try next extension
            }
        }
        return null;
    }
    
    /**
     * Try to load an audio from resources.
     */
    private Media tryLoadAudioFromResources(String elementName) {
        for (String ext : AUDIO_EXTENSIONS) {
            String resourcePath = DEFAULT_SKIN_RESOURCE_PATH + elementName + "." + ext;
            try {
                URL resource = getClass().getResource(resourcePath);
                if (resource != null) {
                    Media media = new Media(resource.toExternalForm());
                    if (media.getError() == null) {
                        return media;
                    }
                }
            } catch (Exception e) {
                // Try next extension
            }
        }
        return null;
    }
    
    /**
     * Try to load an image from directory.
     */
    private Image tryLoadImageFromDirectory(Path directory, String elementName) {
        for (String ext : IMAGE_EXTENSIONS) {
            Path imagePath = directory.resolve(elementName + "." + ext);
            if (Files.exists(imagePath)) {
                try {
                    // Load with background loading, no size constraints
                    Image image = new Image(imagePath.toUri().toString(), true); // backgroundLoading only
                    
                    // Wait for background load
                    while (image.getProgress() < 1.0 && !image.isError()) {
                        Thread.sleep(10);
                    }
                    
                    if (!image.isError()) {
                        return image;
                    }
                } catch (Exception e) {
                    logger.debug("Failed to load default image: {}", imagePath, e);
                }
            }
        }
        return null;
    }
    
    /**
     * Try to load audio from directory.
     */
    private Media tryLoadAudioFromDirectory(Path directory, String elementName) {
        for (String ext : AUDIO_EXTENSIONS) {
            Path audioPath = directory.resolve(elementName + "." + ext);
            if (Files.exists(audioPath)) {
                try {
                    Media media = new Media(audioPath.toUri().toString());
                    if (media.getError() == null) {
                        return media;
                    }
                } catch (Exception e) {
                    logger.debug("Failed to load default audio: {}", audioPath, e);
                }
            }
        }
        return null;
    }
    
    /**
     * Get a default image from cache.
     */
    public Image getImage(String elementName) {
        waitForLoad();
        return imageCache.get(elementName);
    }
    
    /**
     * Get default audio from cache.
     */
    public Media getAudio(String elementName) {
        waitForLoad();
        return audioCache.get(elementName);
    }
    
    /**
     * Check if a default element exists.
     */
    public boolean hasElement(String elementName) {
        waitForLoad();
        return imageCache.containsKey(elementName) || audioCache.containsKey(elementName);
    }
    
    /**
     * Wait for cache to finish loading.
     */
    private void waitForLoad() {
        if (!loaded && loadingFuture != null) {
            try {
                loadingFuture.get(); // Block until loaded
            } catch (Exception e) {
                logger.error("Failed to wait for default cache load", e);
            }
        }
    }
    
    /**
     * Get cache statistics.
     */
    public String getStats() {
        return String.format("DefaultCache[images=%d, audio=%d, loaded=%s]",
            imageCache.size(), audioCache.size(), loaded);
    }
}