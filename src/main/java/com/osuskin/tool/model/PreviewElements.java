package com.osuskin.tool.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Defines the skin elements required for the visual preview.
 * Only these elements should be loaded for preview mode.
 */
public class PreviewElements {
    
    /**
     * Core elements absolutely required for basic preview
     */
    private static final Set<String> REQUIRED_ELEMENTS = new HashSet<>();
    
    /**
     * Optional elements that enhance the preview but aren't critical
     */
    private static final Set<String> OPTIONAL_ELEMENTS = new HashSet<>();
    
    /**
     * Element prefixes that should be loaded (for numbered/animated elements)
     */
    private static final Set<String> REQUIRED_PREFIXES = new HashSet<>();
    
    static {
        // Core gameplay elements (CRITICAL)
        REQUIRED_ELEMENTS.add("hitcircle");
        REQUIRED_ELEMENTS.add("hitcircleoverlay");
        REQUIRED_ELEMENTS.add("approachcircle");
        REQUIRED_ELEMENTS.add("cursor");
        REQUIRED_ELEMENTS.add("cursortrail");
        REQUIRED_ELEMENTS.add("cursormiddle");
        
        // Slider elements (HIGH)
        REQUIRED_ELEMENTS.add("sliderb");
        REQUIRED_ELEMENTS.add("sliderball");
        REQUIRED_ELEMENTS.add("sliderfollowcircle");
        REQUIRED_ELEMENTS.add("reversearrow");
        REQUIRED_ELEMENTS.add("sliderscorepoint");
        REQUIRED_ELEMENTS.add("sliderstartcircle");
        REQUIRED_ELEMENTS.add("sliderstartcircleoverlay");
        REQUIRED_ELEMENTS.add("sliderendcircle");
        REQUIRED_ELEMENTS.add("sliderendcircleoverlay");
        
        // Hit burst elements (HIGH)
        REQUIRED_ELEMENTS.add("hit0");
        REQUIRED_ELEMENTS.add("hit50");
        REQUIRED_ELEMENTS.add("hit100");
        REQUIRED_ELEMENTS.add("hit100k");
        REQUIRED_ELEMENTS.add("hit300");
        REQUIRED_ELEMENTS.add("hit300g");
        REQUIRED_ELEMENTS.add("hit300k");
        REQUIRED_ELEMENTS.add("lighting");
        
        // Numbers (HIGH) - handled by prefix
        // default-0 through default-9
        
        // UI Elements (MEDIUM)
        REQUIRED_ELEMENTS.add("scorebar-bg");
        REQUIRED_ELEMENTS.add("scorebar-colour");
        REQUIRED_ELEMENTS.add("scorebar");  // Legacy fallback
        REQUIRED_ELEMENTS.add("combo-x");
        REQUIRED_ELEMENTS.add("score-percent");
        
        // Optional UI elements (not critical but nice to have)
        OPTIONAL_ELEMENTS.add("scorebar-marker");
        OPTIONAL_ELEMENTS.add("scorebar-ki");
        OPTIONAL_ELEMENTS.add("scorebar-ki-glow");
        OPTIONAL_ELEMENTS.add("scorebar-kidanger");
        OPTIONAL_ELEMENTS.add("scorebar-kidanger2");
        
        // Prefixes for numbered/animated elements
        REQUIRED_PREFIXES.add("default-");      // default-0 through default-9
        REQUIRED_PREFIXES.add("score-");        // score-0 through score-9
        REQUIRED_PREFIXES.add("combo-");        // combo-0 through combo-9
        REQUIRED_PREFIXES.add("hit0-");         // hit0 animation frames
        REQUIRED_PREFIXES.add("hit50-");        // hit50 animation frames
        REQUIRED_PREFIXES.add("hit100-");       // hit100 animation frames
        REQUIRED_PREFIXES.add("hit100k-");      // hit100k animation frames
        REQUIRED_PREFIXES.add("hit300-");       // hit300 animation frames
        REQUIRED_PREFIXES.add("hit300g-");      // hit300g animation frames
        REQUIRED_PREFIXES.add("hit300k-");      // hit300k animation frames
        REQUIRED_PREFIXES.add("lighting-");     // lighting animation frames
        REQUIRED_PREFIXES.add("scorebar-colour-"); // scorebar animation (limited to 10 frames)
    }
    
    /**
     * Check if an element should be loaded for preview.
     * @param elementName The element name (without extension)
     * @return true if this element is needed for preview
     */
    public static boolean isRequiredForPreview(String elementName) {
        if (elementName == null) {
            return false;
        }
        
        // Remove extension for checking
        String cleanName = elementName.toLowerCase()
            .replaceAll("\\.(png|jpg|jpeg|wav|mp3|ogg)$", "");
        
        // Check exact match first
        if (REQUIRED_ELEMENTS.contains(cleanName)) {
            return true;
        }
        
        // Check optional elements
        if (OPTIONAL_ELEMENTS.contains(cleanName)) {
            return true;
        }
        
        // Check prefixes for numbered/animated elements
        for (String prefix : REQUIRED_PREFIXES) {
            if (cleanName.startsWith(prefix)) {
                // Special case: limit scorebar-colour frames
                if (prefix.equals("scorebar-colour-")) {
                    // Extract frame number
                    String frameNumStr = cleanName.substring(prefix.length());
                    try {
                        int frameNum = Integer.parseInt(frameNumStr);
                        // Only load frames 0-9 for performance
                        return frameNum < 10;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if this is an audio file (loaded on-demand).
     */
    public static boolean isAudioFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".ogg");
    }
    
    /**
     * Get the total maximum number of elements that should be loaded.
     * Used for progress tracking.
     */
    public static int getExpectedElementCount() {
        // Approximate count:
        // ~25 core elements
        // 10 default numbers
        // 10 score numbers  
        // 10 combo numbers
        // ~30 animation frames (limited)
        // ~10 UI elements
        return 95; // This is an estimate
    }
}