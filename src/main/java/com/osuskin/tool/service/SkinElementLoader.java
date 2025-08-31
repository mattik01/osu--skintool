package com.osuskin.tool.service;

import com.osuskin.tool.model.Skin;
import com.osuskin.tool.model.SkinElementRegistry;
import com.osuskin.tool.util.PerformanceMonitor;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Optimized skin element loader that uses manifest-based loading with zero file discovery.
 * All element locations are predetermined in the manifest.
 */
public class SkinElementLoader {
    private static final Logger logger = LoggerFactory.getLogger(SkinElementLoader.class);
    
    // Current skin data
    private Path skinDirectory;
    private Skin currentSkin;
    private SkinElementManifest manifest;
    
    // Loaded elements cache
    private final Map<String, Image> loadedImages = new ConcurrentHashMap<>();
    private final Map<String, Media> loadedAudio = new ConcurrentHashMap<>();
    
    // Default skin cache (singleton)
    private final DefaultSkinCache defaultCache = DefaultSkinCache.getInstance();
    
    // Manifest rebuild state
    private volatile boolean rebuildInProgress = false;
    private long lastRebuildTime = 0;
    private static final long REBUILD_COOLDOWN = 5000; // 5 seconds
    private final AtomicInteger mismatchCount = new AtomicInteger(0);
    private static final int MAX_MISMATCHES_BEFORE_REBUILD = 3;
    
    public SkinElementLoader(Path skinDirectory) {
        setSkinDirectory(skinDirectory);
    }
    
    /**
     * Set the skin directory and load/create manifest.
     */
    public void setSkinDirectory(Path skinDirectory) {
        this.skinDirectory = skinDirectory;
        clearCache();
        mismatchCount.set(0);
        
        if (skinDirectory != null && Files.exists(skinDirectory)) {
            loadOrCreateManifest();
        } else {
            this.manifest = null;
        }
    }
    
    /**
     * Load existing manifest or create new one.
     */
    private void loadOrCreateManifest() {
        PerformanceMonitor.startStep("Load/Create Manifest");
        
        // Try to load cached manifest
        manifest = ManifestCache.loadManifest(skinDirectory);
        
        if (manifest == null || !manifest.isLikelyValid(skinDirectory)) {
            // Build new manifest
            logger.info("Building new manifest for: {}", skinDirectory);
            manifest = SkinManifestBuilder.buildManifest(skinDirectory);
            
            if (manifest != null) {
                // Cache the manifest
                ManifestCache.saveManifest(skinDirectory, manifest);
            }
        } else {
            logger.debug("Using cached manifest for: {}", skinDirectory);
        }
        
        PerformanceMonitor.endStep("Load/Create Manifest");
        
        if (manifest != null) {
            logger.info("Manifest loaded: {} elements, {} animations", 
                manifest.getTotalElementCount(), 
                manifest.getAnimationFrames("") != null ? manifest.getAnimationFrames("").size() : 0);
        }
    }
    
    /**
     * Load an image element using manifest-based loading.
     * NO file discovery - only loads from manifest or falls back to default.
     */
    public Image loadImage(String elementName) {
        if (elementName == null) return null;
        
        // Check cache first
        if (loadedImages.containsKey(elementName)) {
            return loadedImages.get(elementName);
        }
        
        // Check manifest
        if (manifest != null) {
            String elementLower = elementName.toLowerCase();
            
            // Check if this element needs fallback
            if (manifest.needsFallback(elementLower)) {
                // Element was marked as missing, use default immediately
                Image defaultImage = defaultCache.getImage(elementName);
                if (defaultImage != null) {
                    loadedImages.put(elementName, defaultImage);
                }
                return defaultImage;
            }
            
            // Try to load from exact path
            String exactPath = manifest.getExactPath(elementLower);
            if (exactPath != null) {
                Image image = loadImageFromPath(exactPath, elementName);
                if (image != null) {
                    loadedImages.put(elementName, image);
                    return image;
                }
            }
        }
        
        // Fallback to default if not in manifest at all
        Image defaultImage = defaultCache.getImage(elementName);
        if (defaultImage != null) {
            loadedImages.put(elementName, defaultImage);
        }
        return defaultImage;
    }
    
    /**
     * Load image without fallback to default.
     */
    public Image loadImageNoFallback(String elementName) {
        if (elementName == null) return null;
        
        // Check cache first
        if (loadedImages.containsKey(elementName)) {
            return loadedImages.get(elementName);
        }
        
        // Check manifest only
        if (manifest != null && manifest.containsElement(elementName.toLowerCase())) {
            String exactPath = manifest.getExactPath(elementName.toLowerCase());
            Image image = loadImageFromPath(exactPath, elementName);
            if (image != null) {
                loadedImages.put(elementName, image);
            }
            return image;
        }
        
        return null; // No fallback
    }
    
    /**
     * Load image with prefix support.
     */
    public Image loadImageWithPrefix(String prefix, String suffix) {
        if (prefix == null || suffix == null) return null;
        
        // Try with custom prefix first
        String customName = prefix + suffix;
        Image result = loadImage(customName);
        
        // If not found and prefix isn't default, try default prefix
        if (result == null && !prefix.equals("default") && !prefix.equals("score")) {
            result = loadImage("default" + suffix);
        }
        
        return result;
    }
    
    /**
     * Load animation frames from manifest.
     * IMPORTANT: Use whatever the skin provides - even if it's just 1 frame.
     */
    public List<Image> loadAnimation(String baseName) {
        if (manifest == null) {
            // No manifest, try single image
            Image single = loadImage(baseName);
            return single != null ? List.of(single) : Collections.emptyList();
        }
        
        // Check if this has animation frames in manifest
        if (manifest.isAnimated(baseName)) {
            List<String> framePaths = manifest.getAnimationFrames(baseName);
            if (framePaths != null && !framePaths.isEmpty()) {
                List<Image> frames = new ArrayList<>();
                for (String framePath : framePaths) {
                    Image frame = loadImageFromPath(framePath, baseName + "-frame");
                    if (frame != null) {
                        frames.add(frame);
                    }
                }
                // Return whatever we got - even if it's just 1 frame
                if (!frames.isEmpty()) {
                    return frames;
                }
            }
        }
        
        // Not animated or loading failed, try single image
        Image single = loadImage(baseName);
        if (single != null) {
            return List.of(single);
        }
        
        // Check for base frame with -0 suffix (some skins use this)
        Image frame0 = loadImage(baseName + "-0");
        if (frame0 != null) {
            return List.of(frame0);
        }
        
        return Collections.emptyList();
    }
    
    /**
     * Check if element exists in manifest.
     */
    public boolean elementExists(String elementName) {
        if (manifest != null) {
            return manifest.containsElement(elementName.toLowerCase());
        }
        return defaultCache.hasElement(elementName);
    }
    
    /**
     * Load all critical elements in parallel for fast preview startup.
     */
    public CompletableFuture<Void> preloadCriticalElements() {
        if (manifest == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        Set<String> criticalElements = manifest.getCriticalElements();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (String elementName : criticalElements) {
            futures.add(CompletableFuture.runAsync(() -> {
                loadImage(elementName); // This caches the image
            }));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Load image from exact path with mismatch detection.
     */
    private Image loadImageFromPath(String pathUri, String elementName) {
        try {
            // Load with background loading, no size constraints
            Image image = new Image(pathUri, true); // backgroundLoading only
            
            // Wait for background load to complete (with timeout)
            int waitCount = 0;
            while (image.getProgress() < 1.0 && !image.isError() && waitCount < 100) {
                Thread.sleep(10);
                waitCount++;
            }
            
            if (image.isError()) {
                handleManifestMismatch(elementName, pathUri, image.getException());
                return null;
            }
            
            return image;
            
        } catch (Exception e) {
            handleManifestMismatch(elementName, pathUri, e);
            return null;
        }
    }
    
    /**
     * Handle manifest mismatch when expected file is not found.
     */
    private void handleManifestMismatch(String elementName, String expectedPath, Exception error) {
        logger.warn("Manifest mismatch for '{}' at path '{}': {}", 
            elementName, expectedPath, error != null ? error.getMessage() : "unknown error");
        
        int mismatches = mismatchCount.incrementAndGet();
        
        // Trigger rebuild if threshold reached
        if (mismatches >= MAX_MISMATCHES_BEFORE_REBUILD && shouldTriggerRebuild()) {
            triggerManifestRebuild();
        }
    }
    
    /**
     * Check if we should trigger a rebuild.
     */
    private boolean shouldTriggerRebuild() {
        if (rebuildInProgress) return false;
        if (System.currentTimeMillis() - lastRebuildTime < REBUILD_COOLDOWN) return false;
        return true;
    }
    
    /**
     * Trigger asynchronous manifest rebuild.
     */
    private void triggerManifestRebuild() {
        if (rebuildInProgress || skinDirectory == null) return;
        
        rebuildInProgress = true;
        lastRebuildTime = System.currentTimeMillis();
        
        logger.info("Triggering manifest rebuild for: {}", skinDirectory);
        
        CompletableFuture.runAsync(() -> {
            try {
                // Build new manifest
                SkinElementManifest newManifest = SkinManifestBuilder.buildManifest(skinDirectory);
                
                if (newManifest != null) {
                    // Replace current manifest
                    this.manifest = newManifest;
                    
                    // Save to cache
                    ManifestCache.saveManifest(skinDirectory, newManifest);
                    
                    // Reset mismatch count
                    mismatchCount.set(0);
                    
                    logger.info("Manifest rebuilt successfully");
                }
            } catch (Exception e) {
                logger.error("Failed to rebuild manifest", e);
            } finally {
                rebuildInProgress = false;
            }
        });
    }
    
    /**
     * Load audio element.
     */
    public Media loadAudio(String elementName) {
        if (elementName == null) return null;
        
        // Check cache first
        if (loadedAudio.containsKey(elementName)) {
            return loadedAudio.get(elementName);
        }
        
        // Check manifest
        if (manifest != null && manifest.containsElement(elementName.toLowerCase())) {
            String exactPath = manifest.getExactPath(elementName.toLowerCase());
            try {
                Media media = new Media(exactPath);
                if (media.getError() == null) {
                    loadedAudio.put(elementName, media);
                    return media;
                }
            } catch (Exception e) {
                logger.debug("Failed to load audio: {}", elementName, e);
            }
        }
        
        // Fallback to default
        Media defaultAudio = defaultCache.getAudio(elementName);
        if (defaultAudio != null) {
            loadedAudio.put(elementName, defaultAudio);
        }
        return defaultAudio;
    }
    
    /**
     * Clear all caches.
     */
    public void clearCache() {
        loadedImages.clear();
        loadedAudio.clear();
    }
    
    /**
     * Set current skin metadata.
     */
    public void setCurrentSkin(Skin skin) {
        this.currentSkin = skin;
    }
    
    public Skin getCurrentSkin() {
        return currentSkin;
    }
    
    /**
     * Get statistics about the loader.
     */
    public String getStats() {
        return String.format("OptimizedLoader[manifest=%s, images=%d, audio=%d, mismatches=%d]",
            manifest != null ? manifest.getTotalElementCount() : 0,
            loadedImages.size(),
            loadedAudio.size(),
            mismatchCount.get());
    }
    
    /**
     * Set preloaded elements from AsyncPreviewLoader.
     * These are the preview elements loaded in the background.
     */
    public void setPreloadedElements(Map<String, Image> elements) {
        if (elements != null) {
            loadedImages.putAll(elements);
        }
    }
    
    /**
     * Get the frame count for an animation element.
     */
    public int getAnimationFrameCount(String baseName) {
        if (manifest != null) {
            List<String> frames = manifest.getAnimationFrames(baseName);
            return frames != null ? frames.size() : 0;
        }
        return 0;
    }
    
    /**
     * Load multiple audio files as a set.
     */
    public List<Media> loadAudioSet(String... elementNames) {
        List<Media> audioSet = new ArrayList<>();
        for (String name : elementNames) {
            Media audio = loadAudio(name);
            if (audio != null) {
                audioSet.add(audio);
            }
        }
        return audioSet;
    }
    
    /**
     * Set default skin directory (legacy compatibility).
     */
    public void setDefaultSkinDirectory(Path defaultSkinDirectory) {
        // Not needed with DefaultSkinCache singleton
        logger.debug("setDefaultSkinDirectory called but ignored - using DefaultSkinCache");
    }
    
    /**
     * Get categorized elements for the skin.
     */
    public Map<com.osuskin.tool.model.SkinElementRegistry.ElementCategory, List<String>> getCategorizedElements() {
        Map<com.osuskin.tool.model.SkinElementRegistry.ElementCategory, List<String>> categorized = new HashMap<>();
        
        if (manifest != null) {
            // Get all elements from manifest
            Set<String> allElements = new HashSet<>();
            allElements.addAll(manifest.getCriticalElements());
            allElements.addAll(manifest.getSecondaryElements());
            
            for (String element : allElements) {
                com.osuskin.tool.model.SkinElementRegistry.ElementDefinition def = 
                    com.osuskin.tool.model.SkinElementRegistry.getDefinition(element);
                if (def != null && def.getCategory() != null) {
                    categorized.computeIfAbsent(def.getCategory(), k -> new ArrayList<>()).add(element);
                }
            }
        }
        
        return categorized;
    }
    
    /**
     * Get element statistics.
     */
    public SkinElementStats getElementStats() {
        SkinElementStats stats = new SkinElementStats();
        
        if (manifest != null) {
            // Count elements by category
            Map<com.osuskin.tool.model.SkinElementRegistry.ElementCategory, List<String>> categorized = getCategorizedElements();
            for (Map.Entry<com.osuskin.tool.model.SkinElementRegistry.ElementCategory, List<String>> entry : categorized.entrySet()) {
                stats.elementsByCategory.put(entry.getKey(), entry.getValue().size());
            }
            
            // Count required elements
            Set<String> allElements = new HashSet<>();
            allElements.addAll(manifest.getCriticalElements());
            allElements.addAll(manifest.getSecondaryElements());
            
            for (String element : allElements) {
                com.osuskin.tool.model.SkinElementRegistry.ElementDefinition def = 
                    com.osuskin.tool.model.SkinElementRegistry.getDefinition(element);
                if (def != null && def.isRequired()) {
                    stats.presentRequiredElements++;
                }
            }
            
            // Get total required from registry
            stats.totalRequiredElements = com.osuskin.tool.model.SkinElementRegistry.getRequiredElements().size();
        }
        
        return stats;
    }
    
    /**
     * Statistics class for element counts.
     */
    public static class SkinElementStats {
        public int totalRequiredElements = 0;
        public int presentRequiredElements = 0;
        public Map<com.osuskin.tool.model.SkinElementRegistry.ElementCategory, Integer> elementsByCategory = new HashMap<>();
        
        public double getRequiredElementCoverage() {
            if (totalRequiredElements == 0) return 0;
            return (double) presentRequiredElements / totalRequiredElements;
        }
    }
}