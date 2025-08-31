package com.osuskin.tool.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PerformanceMonitor {
    // TOGGLE THIS TO ENABLE/DISABLE PERFORMANCE LOGGING
    private static final boolean ENABLED = false;  // Set to true to see performance logs in console
    
    private static final Map<String, Long> stepStartTimes = new ConcurrentHashMap<>();
    private static final Map<String, Long> stepMemoryBefore = new ConcurrentHashMap<>();
    private static long sessionStartTime;
    private static String currentSkinName;
    
    public static void startSession(String skinName) {
        if (!ENABLED) return;
        
        currentSkinName = skinName;
        sessionStartTime = System.currentTimeMillis();
        stepStartTimes.clear();
        stepMemoryBefore.clear();
        
        System.out.println("\n========================================");
        System.out.println("PERFORMANCE LOG - Skin: " + skinName);
        System.out.println("========================================");
    }
    
    public static void startStep(String stepName) {
        if (!ENABLED) return;
        
        long startTime = System.currentTimeMillis();
        long memoryBefore = getUsedMemory();
        
        stepStartTimes.put(stepName, startTime);
        stepMemoryBefore.put(stepName, memoryBefore);
        
        System.out.println("→ " + stepName + " started");
    }
    
    public static void endStep(String stepName) {
        if (!ENABLED) return;
        
        Long startTime = stepStartTimes.get(stepName);
        Long memBefore = stepMemoryBefore.get(stepName);
        
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            long memoryAfter = getUsedMemory();
            long memoryDelta = memoryAfter - (memBefore != null ? memBefore : memoryAfter);
            
            System.out.println("← " + stepName + " completed: " + duration + " ms | Memory: +" + 
                              String.format("%.1f", memoryDelta / (1024.0 * 1024.0)) + " MB");
        }
    }
    
    public static void recordMetadata(String key, Object value) {
        if (!ENABLED) return;
        System.out.println("  • " + key + ": " + value);
    }
    
    public static void endSession() {
        if (!ENABLED) return;
        
        long totalDuration = System.currentTimeMillis() - sessionStartTime;
        
        System.out.println("========================================");
        System.out.println("TOTAL TIME: " + totalDuration + " ms");
        System.out.println("========================================\n");
    }
    
    private static long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
    
    // Keep these for compatibility but they do nothing now
    public static void setEnabled(boolean value) {}
    public static boolean isEnabled() { return ENABLED; }
    
    // Simple auto-closeable for try-with-resources
    public static class AutoCloseable implements java.lang.AutoCloseable {
        private final String stepName;
        
        public AutoCloseable(String stepName) {
            this.stepName = stepName;
            startStep(stepName);
        }
        
        @Override
        public void close() {
            endStep(stepName);
        }
    }
    
    public static AutoCloseable measure(String stepName) {
        return new AutoCloseable(stepName);
    }
}