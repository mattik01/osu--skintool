package com.osuskin.tool.service;

import com.osuskin.tool.model.Skin;
import com.osuskin.tool.model.PreviewElements;
import com.osuskin.tool.util.PerformanceMonitor;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Asynchronous preview loader that loads all required preview elements
 * without blocking the UI, with accurate progress tracking.
 */
public class AsyncPreviewLoader {
    private static final Logger logger = LoggerFactory.getLogger(AsyncPreviewLoader.class);
    
    // Thread pool for parallel loading
    private static final int THREAD_COUNT = Math.min(4, Runtime.getRuntime().availableProcessors());
    private static final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("AsyncPreviewLoader-" + t.getId());
        return t;
    });
    
    // Current loading task tracking
    private volatile Task<PreviewLoadResult> currentTask;
    private volatile boolean cancelRequested = false;
    
    /**
     * Result of preview loading operation
     */
    public static class PreviewLoadResult {
        public final SkinElementLoader elementLoader;
        public final Map<String, Image> loadedElements;
        public final long loadTimeMs;
        public final int elementsLoaded;
        public final boolean wasCancelled;
        
        public PreviewLoadResult(SkinElementLoader loader, Map<String, Image> elements, 
                                long time, boolean cancelled) {
            this.elementLoader = loader;
            this.loadedElements = elements;
            this.loadTimeMs = time;
            this.elementsLoaded = elements.size();
            this.wasCancelled = cancelled;
        }
    }
    
    /**
     * Create a preview loading task for a skin.
     * Loads all required preview elements without blocking the UI.
     * Cancels any previous loading operation.
     */
    public Task<PreviewLoadResult> createPreviewTask(Skin skin) {
        // Cancel previous task if exists
        if (currentTask != null && currentTask.isRunning()) {
            cancelRequested = true;
            currentTask.cancel();
        }
        
        cancelRequested = false;
        
        Task<PreviewLoadResult> task = new Task<PreviewLoadResult>() {
            @Override
            protected PreviewLoadResult call() throws Exception {
                long startTime = System.currentTimeMillis();
                Path skinDir = skin.getDirectoryPathAsPath();
                
                // Start performance monitoring session
                PerformanceMonitor.startSession(skin.getName());
                PerformanceMonitor.recordMetadata("skinPath", skinDir.toString());
                PerformanceMonitor.recordMetadata("skinName", skin.getName());
                
                updateMessage("Checking manifest cache...");
                updateProgress(0, 100);
                
                // Step 1: Create element loader and load/build manifest with progress tracking
                PerformanceMonitor.startStep("Manifest Loading");
                
                // Create a special loader to check manifest status
                SkinElementLoader elementLoader = new SkinElementLoader(skinDir, false); // Don't auto-load manifest
                
                // Load manifest with progress tracking
                SkinElementLoader.ManifestLoadResult manifestResult = elementLoader.loadOrCreateManifest(progress -> {
                    Platform.runLater(() -> {
                        if (progress.usingCache) {
                            // Fast path - using cached manifest
                            updateMessage("✓ Using cached manifest");
                            updateProgress(10, 100);
                        } else if (progress.building) {
                            // Slow path - building manifest
                            double manifestProgress = progress.progress * 0.1; // Scale to 0-10% of total
                            updateMessage("Building manifest: " + progress.message);
                            updateProgress(manifestProgress, 100);
                        }
                    });
                });
                
                PerformanceMonitor.endStep("Manifest Loading");
                
                if (cancelRequested || isCancelled()) {
                    return new PreviewLoadResult(null, Collections.emptyMap(), 0, true);
                }
                
                // Report manifest status
                String manifestStatus = manifestResult.wasCached ? 
                    String.format("Manifest loaded from cache (%dms)", manifestResult.loadTimeMs) :
                    String.format("Manifest built (%dms)", manifestResult.loadTimeMs);
                    
                updateProgress(10, 100);
                updateMessage(manifestStatus);
                
                // Step 2: Determine what elements to load (15% progress)
                PerformanceMonitor.startStep("Element Discovery");
                List<String> elementsToLoad = determineElementsToLoad(elementLoader);
                PerformanceMonitor.recordMetadata("elementsToLoadCount", elementsToLoad.size());
                PerformanceMonitor.endStep("Element Discovery");
                
                if (elementsToLoad.isEmpty()) {
                    logger.warn("No preview elements found in skin: {}", skin.getName());
                    updateMessage("No preview elements found");
                    PerformanceMonitor.endSession();
                    return new PreviewLoadResult(
                        elementLoader, 
                        Collections.emptyMap(), 
                        System.currentTimeMillis() - startTime,
                        false
                    );
                }
                
                updateProgress(15, 100);
                updateMessage("Loading " + elementsToLoad.size() + " elements...");
                
                // Step 3: Load all elements in parallel (15-95% progress)
                PerformanceMonitor.startStep("Element Loading");
                Map<String, Image> loadedElements = new ConcurrentHashMap<>();
                AtomicInteger loadedCount = new AtomicInteger(0);
                AtomicInteger failedCount = new AtomicInteger(0);
                int totalElements = elementsToLoad.size();
                
                // Track individual element types
                Map<String, Integer> elementTypeCounts = new ConcurrentHashMap<>();
                Map<String, Long> elementLoadTimes = new ConcurrentHashMap<>();
                
                // Create loading tasks for all elements
                List<CompletableFuture<Void>> loadingTasks = new ArrayList<>();
                
                for (String elementName : elementsToLoad) {
                    if (cancelRequested || isCancelled()) {
                        loadingTasks.forEach(f -> f.cancel(true));
                        PerformanceMonitor.endStep("Element Loading");
                        PerformanceMonitor.endSession();
                        return new PreviewLoadResult(null, loadedElements, 0, true);
                    }
                    
                    CompletableFuture<Void> loadFuture = CompletableFuture.runAsync(() -> {
                        long elementStartTime = System.nanoTime();
                        try {
                            if (!cancelRequested && !isCancelled()) {
                                // Track element type
                                String elementType = getElementType(elementName);
                                elementTypeCounts.merge(elementType, 1, Integer::sum);
                                
                                // Use the manifest-based loader to load the image
                                Image img = elementLoader.loadImageNoFallback(elementName);
                                if (img != null) {
                                    loadedElements.put(elementName, img);
                                    
                                    // Track load time
                                    long loadTime = (System.nanoTime() - elementStartTime) / 1_000_000;
                                    elementLoadTimes.put(elementName, loadTime);
                                }
                            }
                            
                            int loaded = loadedCount.incrementAndGet();
                            double progress = 15 + (80.0 * loaded / totalElements);
                            Platform.runLater(() -> {
                                updateProgress(progress, 100);
                                if (loaded % 5 == 0) {  // Update message every 5 elements
                                    updateMessage("Loaded " + loaded + "/" + totalElements + " elements");
                                }
                            });
                        } catch (Exception e) {
                            failedCount.incrementAndGet();
                            // Silently skip failed elements
                        }
                    }, executor);
                    
                    loadingTasks.add(loadFuture);
                }
                
                // Wait for all loading tasks to complete
                CompletableFuture.allOf(loadingTasks.toArray(new CompletableFuture[0])).join();
                
                // Log performance metrics
                PerformanceMonitor.recordMetadata("totalElementsRequested", totalElements);
                PerformanceMonitor.recordMetadata("ELEMENTS SUCCESSFULLY LOADED", loadedElements.size());
                PerformanceMonitor.recordMetadata("elementsFailed", failedCount.get());
                if (!elementTypeCounts.isEmpty()) {
                    StringBuilder typeBreakdown = new StringBuilder();
                    elementTypeCounts.forEach((type, count) -> 
                        typeBreakdown.append(type).append(":").append(count).append(" "));
                    PerformanceMonitor.recordMetadata("elementTypeBreakdown", typeBreakdown.toString().trim());
                }
                
                // Find slowest elements
                elementLoadTimes.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .forEach(entry -> 
                        PerformanceMonitor.recordMetadata("slowElement_" + entry.getKey(), entry.getValue() + "ms"));
                
                PerformanceMonitor.endStep("Element Loading");
                
                if (cancelRequested || isCancelled()) {
                    PerformanceMonitor.endSession();
                    return new PreviewLoadResult(null, loadedElements, 0, true);
                }
                
                // Step 4: Set preloaded elements on loader (95-100% progress)
                PerformanceMonitor.startStep("Finalizing");
                updateProgress(95, 100);
                updateMessage("Finalizing...");
                
                elementLoader.setCurrentSkin(skin);
                elementLoader.setPreloadedElements(loadedElements);
                PerformanceMonitor.endStep("Finalizing");
                
                updateProgress(100, 100);
                long loadTime = System.currentTimeMillis() - startTime;
                updateMessage("Complete: " + loadedElements.size() + " elements");
                
                // End performance monitoring session
                PerformanceMonitor.recordMetadata("totalLoadTimeMs", loadTime);
                PerformanceMonitor.endSession();
                
                return new PreviewLoadResult(elementLoader, loadedElements, loadTime, false);
            }
        };
        
        currentTask = task;
        return task;
    }
    
    /**
     * Determine which elements need to be loaded for preview.
     * Only loads standard versions (no @2x HD variants).
     */
    private List<String> determineElementsToLoad(SkinElementLoader loader) {
        final List<String> toLoad = new ArrayList<>();
        
        // Get categorized elements from the manifest-based loader
        Map<com.osuskin.tool.model.SkinElementRegistry.ElementCategory, List<String>> categorized = 
            loader.getCategorizedElements();
        
        // Add all preview-required elements from each category
        for (List<String> elements : categorized.values()) {
            for (String element : elements) {
                // Skip @2x variants
                if (element.contains("@2x")) {
                    continue;
                }
                
                if (PreviewElements.isRequiredForPreview(element) && 
                    !PreviewElements.isAudioFile(element)) {
                    toLoad.add(element);
                }
            }
        }
        
        // Limit scorebar-colour frames to 10 for performance
        return limitScorebarFrames(toLoad);
    }
    
    /**
     * Limit scorebar-colour frames to maximum 10 for performance.
     */
    private List<String> limitScorebarFrames(List<String> elements) {
        // Find all scorebar-colour frames
        List<String> scorebarFrames = new ArrayList<>();
        List<String> otherElements = new ArrayList<>();
        
        for (String element : elements) {
            String fileName = element.toLowerCase();
            if (fileName.startsWith("scorebar-colour-")) {
                scorebarFrames.add(element);
            } else {
                otherElements.add(element);
            }
        }
        
        // Sort scorebar frames by number and keep only first 10
        if (scorebarFrames.size() > 10) {
            scorebarFrames.sort((a, b) -> {
                String nameA = a.toLowerCase().replaceAll("\\.(png|jpg)$", "");
                String nameB = b.toLowerCase().replaceAll("\\.(png|jpg)$", "");
                String numStrA = nameA.substring("scorebar-colour-".length());
                String numStrB = nameB.substring("scorebar-colour-".length());
                try {
                    return Integer.compare(Integer.parseInt(numStrA), Integer.parseInt(numStrB));
                } catch (NumberFormatException e) {
                    return 0;
                }
            });
            scorebarFrames = scorebarFrames.subList(0, 10);
        }
        
        // Combine limited scorebar frames with other elements
        List<String> result = new ArrayList<>(otherElements);
        result.addAll(scorebarFrames);
        return result;
    }
    
    /**
     * Cancel current loading operation.
     */
    public void cancelCurrentLoad() {
        cancelRequested = true;
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
        }
    }
    
    /**
     * Get element type from filename for categorization.
     */
    private String getElementType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.startsWith("hitcircle")) return "hitcircle";
        if (lower.startsWith("approachcircle")) return "approachcircle";
        if (lower.startsWith("slider")) return "slider";
        if (lower.startsWith("cursor")) return "cursor";
        if (lower.startsWith("hit")) return "hit";
        if (lower.startsWith("scorebar")) return "scorebar";
        if (lower.startsWith("score-")) return "score";
        if (lower.startsWith("combo-")) return "combo";
        if (lower.startsWith("default-")) return "default";
        if (lower.contains("overlay")) return "overlay";
        return "other";
    }
    
    /**
     * Shutdown the executor service.
     */
    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}