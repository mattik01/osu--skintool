package com.osuskin.tool.service;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced manifest that contains exact file paths and precomputed animation frames.
 * This is the single source of truth for what elements exist and where they are.
 */
public class SkinElementManifest implements Serializable {
    private static final long serialVersionUID = 2L; // Increment for incompatible changes
    
    // Exact path mappings: element name -> actual file path
    private final Map<String, String> exactPaths = new ConcurrentHashMap<>();
    
    // Precomputed animation frames: animation base name -> list of exact frame paths
    final Map<String, List<String>> animationFrames = new ConcurrentHashMap<>();
    
    // Elements that require fallback to default skin
    private final Set<String> fallbackElements = new HashSet<>();
    
    // Metadata for validation
    private long directoryModifiedTime;
    private int totalFileCount;
    private long creationTime;
    private String skinPath;
    
    // Performance optimization: categorized elements for batch loading
    private final Set<String> criticalElements = new HashSet<>(); // Numbers, cursor, hit circles
    private final Set<String> secondaryElements = new HashSet<>(); // Everything else
    
    public SkinElementManifest(String skinPath) {
        this.skinPath = skinPath;
        this.creationTime = System.currentTimeMillis();
    }
    
    /**
     * Add an element with its exact file path.
     */
    public void addElement(String elementName, String exactFilePath, boolean isCritical) {
        exactPaths.put(elementName, exactFilePath);
        
        if (isCritical) {
            criticalElements.add(elementName);
        } else {
            secondaryElements.add(elementName);
        }
    }
    
    /**
     * Add precomputed animation frames.
     */
    public void addAnimation(String baseName, List<String> framePaths) {
        animationFrames.put(baseName, new ArrayList<>(framePaths));
    }
    
    /**
     * Check if an element exists in the manifest.
     */
    public boolean containsElement(String elementName) {
        return exactPaths.containsKey(elementName) || fallbackElements.contains(elementName);
    }
    
    /**
     * Check if element needs fallback to default.
     */
    public boolean needsFallback(String elementName) {
        return fallbackElements.contains(elementName);
    }
    
    /**
     * Mark an element as requiring fallback.
     */
    public void addFallbackElement(String elementName) {
        fallbackElements.add(elementName);
    }
    
    /**
     * Get the exact file path for an element.
     */
    public String getExactPath(String elementName) {
        return exactPaths.get(elementName);
    }
    
    /**
     * Get precomputed animation frames.
     */
    public List<String> getAnimationFrames(String baseName) {
        return animationFrames.get(baseName);
    }
    
    /**
     * Check if this is an animated element.
     */
    public boolean isAnimated(String baseName) {
        return animationFrames.containsKey(baseName);
    }
    
    /**
     * Quick validation check without file I/O.
     */
    public boolean isLikelyValid(Path skinDirectory) {
        // Check if too old (more than 7 days)
        if (System.currentTimeMillis() - creationTime > 7L * 24 * 60 * 60 * 1000) {
            return false;
        }
        
        // Check if directory was modified
        if (skinDirectory != null && skinDirectory.toFile().exists()) {
            long currentModTime = skinDirectory.toFile().lastModified();
            if (currentModTime > directoryModifiedTime) {
                return false; // Directory changed since manifest creation
            }
        }
        
        return true;
    }
    
    /**
     * Get all critical elements for priority loading.
     */
    public Set<String> getCriticalElements() {
        return new HashSet<>(criticalElements);
    }
    
    /**
     * Get all secondary elements for background loading.
     */
    public Set<String> getSecondaryElements() {
        return new HashSet<>(secondaryElements);
    }
    
    /**
     * Get total element count.
     */
    public int getTotalElementCount() {
        return exactPaths.size() + fallbackElements.size();
    }
    
    /**
     * Set metadata for validation.
     */
    public void setMetadata(long directoryModifiedTime, int totalFileCount) {
        this.directoryModifiedTime = directoryModifiedTime;
        this.totalFileCount = totalFileCount;
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    public String getSkinPath() {
        return skinPath;
    }
    
    @Override
    public String toString() {
        return String.format("SkinElementManifest[elements=%d, animations=%d, critical=%d, age=%dms]",
            exactPaths.size(), 
            animationFrames.size(),
            criticalElements.size(),
            System.currentTimeMillis() - creationTime);
    }
}