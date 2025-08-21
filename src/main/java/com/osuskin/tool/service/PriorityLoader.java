package com.osuskin.tool.service;

import com.osuskin.tool.model.LoadPriority;
import com.osuskin.tool.model.Skin;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PriorityLoader {
    private static final Logger logger = LoggerFactory.getLogger(PriorityLoader.class);
    private final ForkJoinPool executor = ForkJoinPool.commonPool();
    
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total);
    }
    
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final StringProperty status = new SimpleStringProperty("");
    
    private Map<LoadPriority, List<String>> elementsByPriority;
    private AtomicInteger totalElements;
    private AtomicInteger loadedElements;
    
    public DoubleProperty progressProperty() {
        return progress;
    }
    
    public StringProperty statusProperty() {
        return status;
    }
    
    public CompletableFuture<Map<LoadPriority, List<String>>> scanAndCategorizeElements(Path skinDir) {
        return CompletableFuture.supplyAsync(() -> {
            Map<LoadPriority, List<String>> categorized = new ConcurrentHashMap<>();
            for (LoadPriority priority : LoadPriority.values()) {
                categorized.put(priority, Collections.synchronizedList(new ArrayList<>()));
            }
            
            try (Stream<Path> paths = Files.list(skinDir)) {
                paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        LoadPriority priority = LoadPriority.getPriority(fileName);
                        categorized.get(priority).add(fileName);
                    });
            } catch (IOException e) {
                logger.error("Error scanning skin directory: " + skinDir, e);
            }
            
            // Log summary
            categorized.forEach((priority, elements) -> {
                logger.debug("Priority {}: {} elements", priority, elements.size());
            });
            
            return categorized;
        }, executor);
    }
    
    public void loadSkinWithPriority(Skin skin, SkinElementLoader loader, 
                                    Runnable onCriticalLoaded, 
                                    Runnable onHighLoaded,
                                    Runnable onMediumLoaded,
                                    Runnable onComplete) {
        loadSkinWithPriority(skin, loader, onCriticalLoaded, onHighLoaded, 
                           onMediumLoaded, onComplete, null);
    }
    
    public void loadSkinWithPriority(Skin skin, SkinElementLoader loader, 
                                    Runnable onCriticalLoaded, 
                                    Runnable onHighLoaded,
                                    Runnable onMediumLoaded,
                                    Runnable onComplete,
                                    ProgressCallback progressCallback) {
        Path skinDir = skin.getDirectoryPathAsPath();
        
        // First scan and categorize all elements
        scanAndCategorizeElements(skinDir).thenAccept(elements -> {
            this.elementsByPriority = elements;
            this.totalElements = new AtomicInteger(
                elements.values().stream().mapToInt(List::size).sum()
            );
            this.loadedElements = new AtomicInteger(0);
            
            // Start loading in priority order
            loadPriorityGroup(skinDir, loader, LoadPriority.CRITICAL, elements.get(LoadPriority.CRITICAL), progressCallback)
                .thenRun(() -> {
                    Platform.runLater(onCriticalLoaded);
                    logger.info("Critical elements loaded for skin: {}", skin.getName());
                    
                    // Continue with high priority
                    loadPriorityGroup(skinDir, loader, LoadPriority.HIGH, elements.get(LoadPriority.HIGH), progressCallback)
                        .thenRun(() -> {
                            Platform.runLater(onHighLoaded);
                            logger.info("High priority elements loaded");
                            
                            // Continue with medium priority
                            loadPriorityGroup(skinDir, loader, LoadPriority.MEDIUM, elements.get(LoadPriority.MEDIUM), progressCallback)
                                .thenRun(() -> {
                                    Platform.runLater(onMediumLoaded);
                                    logger.info("Medium priority elements loaded");
                                    
                                    // Finally load low priority
                                    loadPriorityGroup(skinDir, loader, LoadPriority.LOW, elements.get(LoadPriority.LOW), progressCallback)
                                        .thenRun(() -> {
                                            Platform.runLater(onComplete);
                                            logger.info("All elements loaded");
                                        });
                                });
                        });
                });
        });
    }
    
    private CompletableFuture<Void> loadPriorityGroup(Path skinDir, SkinElementLoader loader,
                                                      LoadPriority priority, List<String> elements,
                                                      ProgressCallback progressCallback) {
        if (elements.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            logger.debug("Loading {} {} priority elements", elements.size(), priority);
            
            // Update status
            Platform.runLater(() -> 
                status.set(String.format("Loading %s priority elements...", priority.toString().toLowerCase()))
            );
            
            // Process elements in parallel within priority group
            elements.parallelStream().forEach(element -> {
                try {
                    // The actual loading will be done by the loader
                    // Here we just track progress
                    int loaded = loadedElements.incrementAndGet();
                    int total = totalElements.get();
                    double progressValue = (double) loaded / total;
                    
                    // Call progress callback if provided
                    if (progressCallback != null) {
                        progressCallback.onProgress(loaded, total);
                    }
                    
                    Platform.runLater(() -> {
                        progress.set(progressValue);
                        if (loaded % 10 == 0) { // Update status every 10 elements
                            status.set(String.format("Loading %s (%d/%d)", 
                                element, loaded, total));
                        }
                    });
                } catch (Exception e) {
                    logger.warn("Error loading element: " + element, e);
                }
            });
        }, executor);
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}