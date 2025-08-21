package com.osuskin.tool.service;

import com.osuskin.tool.model.Skin;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous skin loading service that prevents UI blocking.
 * Manages loading lifecycle and provides progress updates.
 */
public class AsyncSkinLoader {
    private static final Logger logger = LoggerFactory.getLogger(AsyncSkinLoader.class);
    
    private final ExecutorService executor = ForkJoinPool.commonPool();
    private final PriorityLoader priorityLoader = new PriorityLoader();
    private CompletableFuture<SkinLoadResult> currentLoad;
    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final StringProperty status = new SimpleStringProperty("");
    
    // Result class to hold loaded data
    public static class SkinLoadResult {
        public SkinElementLoader loader;
        public SkinIndexCache.SkinIndex index;
        public boolean success;
        public String errorMessage;
        public long loadTimeMs;
        
        public SkinLoadResult() {
            this.success = false;
        }
    }
    
    public DoubleProperty progressProperty() {
        return progress;
    }
    
    public StringProperty statusProperty() {
        return status;
    }
    
    public boolean isLoading() {
        return isLoading.get();
    }
    
    /**
     * Load a skin asynchronously with progress tracking.
     * Cancels any previous loading operation.
     */
    public CompletableFuture<SkinLoadResult> loadSkinAsync(Skin skin) {
        // Cancel previous load if exists
        if (currentLoad != null && !currentLoad.isDone()) {
            currentLoad.cancel(true);
            logger.info("Cancelled previous skin load");
        }
        
        isLoading.set(true);
        Platform.runLater(() -> {
            progress.set(0);
            status.set("Initializing...");
        });
        
        long startTime = System.currentTimeMillis();
        
        currentLoad = CompletableFuture.supplyAsync(() -> {
            SkinLoadResult result = new SkinLoadResult();
            Path skinPath = skin.getDirectoryPathAsPath();
            
            try {
                // Step 1: Create/load index (20% progress)
                Platform.runLater(() -> {
                    progress.set(0.1);
                    status.set("Checking skin index...");
                });
                
                SkinIndexCache indexCache = new SkinIndexCache();
                long indexStart = System.nanoTime();
                SkinIndexCache.SkinIndexResult indexResult = indexCache.loadOrCreateIndex(skinPath);
                result.index = indexResult.index;
                long indexTime = (System.nanoTime() - indexStart) / 1_000_000;
                
                // Now we KNOW if cache was used - no guessing!
                boolean cacheHit = indexResult.usedCache;
                
                logger.info("Index loaded in {}ms with {} elements, {} animations (cache: {})", 
                    indexTime, result.index.availableElements.size(), 
                    result.index.animationFrameCounts.size(), cacheHit ? "HIT" : "MISS");
                
                Platform.runLater(() -> {
                    progress.set(0.2);
                    if (cacheHit) {
                        status.set("Using cached index ✓");
                    } else {
                        status.set("Index built");
                    }
                });
                
                // Step 2: Initialize element loader (30% progress)
                Platform.runLater(() -> {
                    status.set("Initializing element loader...");
                });
                
                result.loader = new SkinElementLoader(skinPath);
                result.loader.setCurrentSkin(skin);
                
                Platform.runLater(() -> {
                    progress.set(0.3);
                    status.set("Ready to load elements");
                });
                
                // Step 3: Load elements using priority system
                Platform.runLater(() -> {
                    status.set("Loading critical elements...");
                });
                
                // Use PriorityLoader for progressive loading
                priorityLoader.loadSkinWithPriority(
                    skin,
                    result.loader,
                    // Critical elements loaded callback
                    () -> Platform.runLater(() -> {
                        progress.set(0.5);
                        status.set("Critical elements loaded");
                    }),
                    // High priority loaded callback
                    () -> Platform.runLater(() -> {
                        progress.set(0.7);
                        status.set("High priority elements loaded");
                    }),
                    // Medium priority loaded callback
                    () -> Platform.runLater(() -> {
                        progress.set(0.9);
                        status.set("Medium priority elements loaded");
                    }),
                    // All elements loaded callback
                    () -> Platform.runLater(() -> {
                        progress.set(1.0);
                        status.set("All elements loaded");
                    }),
                    // Progress callback
                    new PriorityLoader.ProgressCallback() {
                        @Override
                        public void onProgress(int current, int total) {
                            Platform.runLater(() -> {
                                double baseProgress = 0.3;
                                double loadProgress = 0.7 * ((double)current / total);
                                progress.set(baseProgress + loadProgress);
                            });
                        }
                    }
                );
                
                result.success = true;
                result.loadTimeMs = System.currentTimeMillis() - startTime;
                
                logger.info("Skin loaded successfully in {}ms", result.loadTimeMs);
                
            } catch (Exception e) {
                result.success = false;
                result.errorMessage = e.getMessage();
                logger.error("Error loading skin: " + skin.getName(), e);
            } finally {
                isLoading.set(false);
                Platform.runLater(() -> {
                    if (result.success) {
                        progress.set(1.0);
                        status.set("Load complete");
                    } else {
                        status.set("Load failed: " + result.errorMessage);
                    }
                });
            }
            
            return result;
        }, executor);
        
        return currentLoad;
    }
    
    /**
     * Create a JavaFX Task for skin loading (alternative to CompletableFuture).
     * Useful for direct binding to UI components.
     */
    public Task<SkinLoadResult> createLoadTask(Skin skin) {
        return new Task<SkinLoadResult>() {
            @Override
            protected SkinLoadResult call() throws Exception {
                SkinLoadResult result = new SkinLoadResult();
                Path skinPath = skin.getDirectoryPathAsPath();
                long startTime = System.currentTimeMillis();
                
                try {
                    // Update progress
                    updateProgress(0, 100);
                    updateMessage("Checking skin index...");
                    
                    // Load index
                    SkinIndexCache indexCache = new SkinIndexCache();
                    long indexStart = System.nanoTime();
                    SkinIndexCache.SkinIndexResult indexResult = indexCache.loadOrCreateIndex(skinPath);
                    result.index = indexResult.index;
                    long indexTime = (System.nanoTime() - indexStart) / 1_000_000;
                    
                    // Now we KNOW if cache was used - no guessing!
                    boolean cacheHit = indexResult.usedCache;
                    logger.info("Task: Index loaded in {}ms with {} elements (cache: {})", 
                        indexTime, result.index.availableElements.size(), cacheHit ? "HIT" : "MISS");
                    
                    updateProgress(20, 100);
                    if (cacheHit) {
                        updateMessage("Using cached index ✓");
                        try {
                            Thread.sleep(200); // Brief pause to show the message
                        } catch (InterruptedException e) {
                            // Task was cancelled, that's OK
                            Thread.currentThread().interrupt();
                            result.success = false;
                            result.errorMessage = "Loading cancelled";
                            return result; // Return early if cancelled
                        }
                    } else {
                        updateMessage("Index built");
                    }
                    updateMessage("Initializing loader...");
                    
                    // Initialize loader
                    result.loader = new SkinElementLoader(skinPath);
                    result.loader.setCurrentSkin(skin);
                    
                    updateProgress(30, 100);
                    updateMessage("Loading elements by priority...");
                    
                    // Use PriorityLoader for progressive loading
                    priorityLoader.loadSkinWithPriority(
                        skin,
                        result.loader,
                        // Critical elements loaded
                        () -> {
                            updateProgress(50, 100);
                            updateMessage("Critical elements loaded");
                        },
                        // High priority loaded
                        () -> {
                            updateProgress(70, 100);
                            updateMessage("High priority elements loaded");
                        },
                        // Medium priority loaded
                        () -> {
                            updateProgress(90, 100);
                            updateMessage("Medium priority elements loaded");
                        },
                        // All elements loaded
                        () -> {
                            updateProgress(100, 100);
                            updateMessage("All elements loaded");
                        },
                        // Progress callback
                        new PriorityLoader.ProgressCallback() {
                            @Override
                            public void onProgress(int current, int total) {
                                int progressValue = 30 + (70 * current / total);
                                updateProgress(progressValue, 100);
                            }
                        }
                    );
                    
                    updateMessage("Complete");
                    
                    result.success = true;
                    result.loadTimeMs = System.currentTimeMillis() - startTime;
                    
                } catch (Exception e) {
                    result.success = false;
                    result.errorMessage = e.getMessage();
                    updateMessage("Error: " + e.getMessage());
                    logger.error("Error loading skin: " + skin.getName(), e);
                    // Don't throw - return the result with success=false
                }
                
                return result;
            }
        };
    }
    
    /**
     * Cancel the current loading operation.
     */
    public void cancelCurrentLoad() {
        if (currentLoad != null && !currentLoad.isDone()) {
            currentLoad.cancel(true);
            isLoading.set(false);
            Platform.runLater(() -> {
                status.set("Load cancelled");
            });
        }
    }
    
    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        cancelCurrentLoad();
        executor.shutdown();
    }
}