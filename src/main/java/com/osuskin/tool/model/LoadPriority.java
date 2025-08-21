package com.osuskin.tool.model;

import java.util.HashMap;
import java.util.Map;

public enum LoadPriority {
    CRITICAL(0),   // hitcircle, cursor, approachcircle - needed immediately
    HIGH(1),       // numbers, hit bursts, sliders - needed soon  
    MEDIUM(2),     // scorebar, UI elements - nice to have
    LOW(3);        // menu elements, sounds - can wait
    
    private final int order;
    
    private static final Map<String, LoadPriority> ELEMENT_PRIORITIES = new HashMap<>();
    
    static {
        // Critical elements - show preview immediately
        ELEMENT_PRIORITIES.put("hitcircle", CRITICAL);
        ELEMENT_PRIORITIES.put("hitcircleoverlay", CRITICAL);
        ELEMENT_PRIORITIES.put("cursor", CRITICAL);
        ELEMENT_PRIORITIES.put("cursormiddle", CRITICAL);
        ELEMENT_PRIORITIES.put("cursortrail", CRITICAL);
        ELEMENT_PRIORITIES.put("approachcircle", CRITICAL);
        
        // High priority - needed for gameplay
        ELEMENT_PRIORITIES.put("default-", HIGH);
        ELEMENT_PRIORITIES.put("hit0", HIGH);
        ELEMENT_PRIORITIES.put("hit50", HIGH);
        ELEMENT_PRIORITIES.put("hit100", HIGH);
        ELEMENT_PRIORITIES.put("hit100k", HIGH);
        ELEMENT_PRIORITIES.put("hit300", HIGH);
        ELEMENT_PRIORITIES.put("hit300g", HIGH);
        ELEMENT_PRIORITIES.put("hit300k", HIGH);
        ELEMENT_PRIORITIES.put("sliderb", HIGH);
        ELEMENT_PRIORITIES.put("sliderfollowcircle", HIGH);
        ELEMENT_PRIORITIES.put("sliderball", HIGH);
        ELEMENT_PRIORITIES.put("reversearrow", HIGH);
        ELEMENT_PRIORITIES.put("sliderscorepoint", HIGH);
        
        // Medium priority - UI elements
        ELEMENT_PRIORITIES.put("scorebar", MEDIUM);
        ELEMENT_PRIORITIES.put("score-", MEDIUM);
        ELEMENT_PRIORITIES.put("combo-", MEDIUM);
        ELEMENT_PRIORITIES.put("ranking", MEDIUM);
        ELEMENT_PRIORITIES.put("section-", MEDIUM);
        ELEMENT_PRIORITIES.put("star", MEDIUM);
        ELEMENT_PRIORITIES.put("spinner", MEDIUM);
        
        // Low priority - menu and sounds
        ELEMENT_PRIORITIES.put("menu", LOW);
        ELEMENT_PRIORITIES.put("selection", LOW);
        ELEMENT_PRIORITIES.put("button", LOW);
        ELEMENT_PRIORITIES.put("pause", LOW);
        ELEMENT_PRIORITIES.put("fail", LOW);
        ELEMENT_PRIORITIES.put("applause", LOW);
        ELEMENT_PRIORITIES.put("combobreak", LOW);
        ELEMENT_PRIORITIES.put("drum-", LOW);
        ELEMENT_PRIORITIES.put("normal-", LOW);
        ELEMENT_PRIORITIES.put("soft-", LOW);
    }
    
    LoadPriority(int order) {
        this.order = order;
    }
    
    public int getOrder() {
        return order;
    }
    
    public static LoadPriority getPriority(String elementName) {
        if (elementName == null) {
            return MEDIUM;
        }
        
        // Remove file extension and @2x suffix
        String cleanName = elementName.toLowerCase()
            .replaceAll("\\.(png|jpg|jpeg|wav|mp3|ogg)$", "")
            .replaceAll("@2x$", "");
        
        // Check for exact match first
        LoadPriority priority = ELEMENT_PRIORITIES.get(cleanName);
        if (priority != null) {
            return priority;
        }
        
        // Check for prefix matches
        for (Map.Entry<String, LoadPriority> entry : ELEMENT_PRIORITIES.entrySet()) {
            if (cleanName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // Check for animation frames (e.g., hit300-0, hit300-1)
        if (cleanName.matches(".*-\\d+$")) {
            String baseElement = cleanName.replaceAll("-\\d+$", "");
            return getPriority(baseElement);
        }
        
        return MEDIUM;
    }
    
    public boolean isHigherPriorityThan(LoadPriority other) {
        return this.order < other.order;
    }
}