package com.osuskin.tool.view.gameplay;

import javafx.scene.image.Image;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared cache for elements that are used by multiple components.
 * Prevents duplicate loading of the same elements.
 */
public class SharedElementCache {
    private static final SharedElementCache INSTANCE = new SharedElementCache();
    
    // Shared number images
    private final Map<String, Image> numberImages = new ConcurrentHashMap<>();
    
    // Shared UI elements
    private final Map<String, Image> uiElements = new ConcurrentHashMap<>();
    
    private SharedElementCache() {
        // Private constructor for singleton
    }
    
    public static SharedElementCache getInstance() {
        return INSTANCE;
    }
    
    /**
     * Get or load a number image.
     */
    public Image getNumber(String key, java.util.function.Supplier<Image> loader) {
        return numberImages.computeIfAbsent(key, k -> loader.get());
    }
    
    /**
     * Get or load a UI element.
     */
    public Image getUIElement(String key, java.util.function.Supplier<Image> loader) {
        return uiElements.computeIfAbsent(key, k -> loader.get());
    }
    
    /**
     * Check if a number is already cached.
     */
    public boolean hasNumber(String key) {
        return numberImages.containsKey(key);
    }
    
    /**
     * Check if a UI element is already cached.
     */
    public boolean hasUIElement(String key) {
        return uiElements.containsKey(key);
    }
    
    /**
     * Clear all caches.
     */
    public void clear() {
        numberImages.clear();
        uiElements.clear();
    }
    
    /**
     * Get cache statistics.
     */
    public String getStats() {
        return String.format("SharedCache[numbers=%d, ui=%d]", 
            numberImages.size(), uiElements.size());
    }
}