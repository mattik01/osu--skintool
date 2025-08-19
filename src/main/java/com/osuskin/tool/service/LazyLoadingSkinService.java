package com.osuskin.tool.service;

import com.osuskin.tool.model.Skin;
import com.osuskin.tool.model.SkinElement;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight skin service with lazy loading and minimal memory footprint.
 * Only loads what's needed, when it's needed.
 */
public class LazyLoadingSkinService {
    
    private static final Logger logger = LoggerFactory.getLogger(LazyLoadingSkinService.class);
    
    // File patterns for different loading stages
    private static final List<String> VISUAL_PRIORITY_FILES = Arrays.asList(
        "hitcircle.png", "hitcircleoverlay.png", "approachcircle.png",
        "cursor.png", "cursormiddle.png", "cursortrail.png",
        "default-0.png", "default-1.png", "default-2.png", "default-3.png",
        "default-4.png", "default-5.png", "default-6.png", "default-7.png",
        "default-8.png", "default-9.png",
        "hit0.png", "hit50.png", "hit100.png", "hit100k.png", "hit300.png", "hit300k.png",
        "sliderb0.png", "sliderfollowcircle.png", "sliderscorepoint.png",
        "reversearrow.png", "sliderpoint10.png", "sliderpoint30.png"
    );
    
    private static final List<String> UI_FILES = Arrays.asList(
        "scorebar-bg.png", "scorebar-colour.png",
        "score-0.png", "score-1.png", "score-2.png", "score-3.png",
        "score-4.png", "score-5.png", "score-6.png", "score-7.png",
        "score-8.png", "score-9.png", "score-comma.png", "score-dot.png",
        "menu-back.png", "menu-background.jpg", "menu-background.png",
        "ranking-panel.png", "ranking-graph.png",
        "play-skip.png", "pause-overlay.png", "fail-background.png"
    );
    
    private static final List<String> HITSOUND_FILES = Arrays.asList(
        "normal-hitnormal.wav", "normal-hitwhistle.wav", 
        "normal-hitfinish.wav", "normal-hitclap.wav",
        "soft-hitnormal.wav", "soft-hitwhistle.wav",
        "soft-hitfinish.wav", "soft-hitclap.wav",
        "drum-hitnormal.wav", "drum-hitwhistle.wav",
        "drum-hitfinish.wav", "drum-hitclap.wav"
    );
    
    private static final List<String> MISC_AUDIO_FILES = Arrays.asList(
        "combobreak.wav", "comboburst.wav",
        "applause.wav", "failsound.wav",
        "seeya.wav", "welcome.wav",
        "menu-hit.wav", "menu-back.wav", "menu-click.wav",
        "click-short.wav", "click-close.wav"
    );
    
    // Current loading task (to cancel if needed)
    private Task<Void> currentLoadingTask = null;
    private final AtomicBoolean cancelCurrentTask = new AtomicBoolean(false);
    
    // Dedicated thread pool for loading operations
    private final ExecutorService loadingExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY); // Low priority to not interfere with UI
        t.setName("SkinLoader");
        return t;
    });
    
    /**
     * Quick scan - only gets basic metadata, no file loading
     */
    public List<Skin> quickScanDirectory(Path skinsDirectory) throws IOException {
        if (!Files.exists(skinsDirectory) || !Files.isDirectory(skinsDirectory)) {
            logger.warn("Invalid skins directory: {}", skinsDirectory);
            return Collections.emptyList();
        }
        
        List<Skin> skins = new ArrayList<>();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skinsDirectory)) {
            for (Path skinDir : stream) {
                if (Files.isDirectory(skinDir)) {
                    Skin skin = createMinimalSkin(skinDir);
                    if (skin != null) {
                        skins.add(skin);
                    }
                }
            }
        }
        
        logger.info("Quick scan found {} skins", skins.size());
        return skins;
    }
    
    /**
     * Create minimal skin object with just metadata
     */
    private Skin createMinimalSkin(Path skinDirectory) {
        try {
            String skinName = skinDirectory.getFileName().toString();
            Skin skin = new Skin(skinName, skinDirectory);
            
            // Only parse skin.ini for name/author/version
            parseSkinIniLightweight(skin, skinDirectory);
            
            // Set basic attributes
            BasicFileAttributes attrs = Files.readAttributes(skinDirectory, BasicFileAttributes.class);
            skin.setLastModified(LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()));
            
            // Don't count files or load elements yet
            skin.setFileCount(-1); // -1 indicates not loaded
            
            return skin;
        } catch (Exception e) {
            logger.debug("Could not create skin for: {}", skinDirectory, e);
            return null;
        }
    }
    
    /**
     * Lightweight skin.ini parsing - just metadata
     */
    private void parseSkinIniLightweight(Skin skin, Path skinDirectory) {
        List<String> iniNames = Arrays.asList("skin.ini", "Skin.ini", "SKIN.INI");
        
        for (String iniName : iniNames) {
            Path iniPath = skinDirectory.resolve(iniName);
            if (Files.exists(iniPath)) {
                try {
                    // Try different encodings
                    List<String> lines = tryReadFileWithEncodings(iniPath);
                    if (lines != null) {
                        parseBasicSkinInfo(skin, lines);
                        return;
                    }
                } catch (Exception e) {
                    logger.debug("Could not parse skin.ini: {}", iniPath, e);
                }
            }
        }
    }
    
    /**
     * Try reading file with multiple encodings
     */
    private List<String> tryReadFileWithEncodings(Path file) {
        List<Charset> charsets = Arrays.asList(
            StandardCharsets.UTF_8,
            StandardCharsets.ISO_8859_1,
            Charset.forName("Windows-1252")
        );
        
        for (Charset charset : charsets) {
            try {
                return Files.readAllLines(file, charset);
            } catch (Exception e) {
                // Try next encoding
            }
        }
        
        // Last resort - read with replacement characters
        try {
            return readWithReplacement(file);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Read file replacing invalid characters
     */
    private List<String> readWithReplacement(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Replace non-ASCII characters
                line = line.replaceAll("[^\\x00-\\x7F]", "?");
                lines.add(line);
            }
        }
        return lines;
    }
    
    /**
     * Parse only basic skin info (name, author, version)
     */
    private void parseBasicSkinInfo(Skin skin, List<String> lines) {
        String section = "";
        
        for (String line : lines) {
            line = line.trim();
            
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1);
                continue;
            }
            
            if ("General".equalsIgnoreCase(section) && line.contains("=")) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim().toLowerCase();
                    String value = parts[1].trim();
                    
                    switch (key) {
                        case "name" -> skin.setName(value.isEmpty() ? skin.getName() : value);
                        case "author" -> skin.setAuthor(value);
                        case "version" -> skin.setVersion(value);
                    }
                }
            }
        }
    }
    
    /**
     * Create a cancellable task for progressive skin loading
     * This task runs in a separate thread and doesn't block the UI
     */
    public Task<Void> createProgressiveLoadTask(Skin skin, Runnable onProgress) {
        // Cancel any existing task
        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            cancelCurrentTask.set(true);
            currentLoadingTask.cancel();
        }
        
        cancelCurrentTask.set(false);
        
        currentLoadingTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Path skinDir = skin.getDirectoryPathAsPath();
                
                if (skinDir == null || !Files.exists(skinDir)) {
                    updateMessage("Skin directory not found");
                    return null;
                }
                
                // Clear existing elements (fresh load) - thread safe
                synchronized (skin.getElements()) {
                    skin.getElements().clear();
                }
                
                int totalSteps = 4;
                int currentStep = 0;
                
                // Step 1: Load visual elements (highest priority)
                if (!cancelCurrentTask.get() && !isCancelled()) {
                    updateMessage("Loading visual elements...");
                    updateProgress(currentStep++, totalSteps);
                    loadFileGroup(skin, skinDir, VISUAL_PRIORITY_FILES, SkinElement.ElementType.HIT_CIRCLE);
                    
                    // Use Platform.runLater to update UI from background thread
                    javafx.application.Platform.runLater(() -> {
                        if (!cancelCurrentTask.get()) {
                            onProgress.run();
                        }
                    });
                    
                    // Small delay to allow UI to update
                    Thread.sleep(10);
                }
                
                // Step 2: Load UI elements
                if (!cancelCurrentTask.get() && !isCancelled()) {
                    updateMessage("Loading UI elements...");
                    updateProgress(currentStep++, totalSteps);
                    loadFileGroup(skin, skinDir, UI_FILES, SkinElement.ElementType.MENU_BACK);
                    
                    javafx.application.Platform.runLater(() -> {
                        if (!cancelCurrentTask.get()) {
                            onProgress.run();
                        }
                    });
                    
                    Thread.sleep(10);
                }
                
                // Step 3: Load hitsounds
                if (!cancelCurrentTask.get() && !isCancelled()) {
                    updateMessage("Loading hitsounds...");
                    updateProgress(currentStep++, totalSteps);
                    loadFileGroup(skin, skinDir, HITSOUND_FILES, SkinElement.ElementType.NORMAL_HITNORMAL);
                    
                    javafx.application.Platform.runLater(() -> {
                        if (!cancelCurrentTask.get()) {
                            onProgress.run();
                        }
                    });
                    
                    Thread.sleep(10);
                }
                
                // Step 4: Load misc audio
                if (!cancelCurrentTask.get() && !isCancelled()) {
                    updateMessage("Loading audio files...");
                    updateProgress(currentStep++, totalSteps);
                    loadFileGroup(skin, skinDir, MISC_AUDIO_FILES, SkinElement.ElementType.MENU_HIT);
                    
                    javafx.application.Platform.runLater(() -> {
                        if (!cancelCurrentTask.get()) {
                            onProgress.run();
                        }
                    });
                }
                
                // Find preview image if not already set
                if (!cancelCurrentTask.get() && !isCancelled() && skin.getPreviewImagePath() == null) {
                    findPreviewImage(skin, skinDir);
                }
                
                if (!cancelCurrentTask.get() && !isCancelled()) {
                    updateMessage("Loading complete");
                    updateProgress(totalSteps, totalSteps);
                }
                
                return null;
            }
            
            @Override
            protected void cancelled() {
                super.cancelled();
                updateMessage("Loading cancelled");
                logger.debug("Skin loading cancelled for: {}", skin.getName());
            }
            
            @Override
            protected void failed() {
                super.failed();
                updateMessage("Loading failed");
                logger.error("Skin loading failed for: {}", skin.getName(), getException());
            }
        };
        
        // Execute on dedicated thread pool
        loadingExecutor.submit(() -> {
            try {
                currentLoadingTask.run();
            } catch (Exception e) {
                logger.error("Error in loading task", e);
            }
        });
        
        return currentLoadingTask;
    }
    
    /**
     * Load a group of files for the skin (thread-safe)
     */
    private void loadFileGroup(Skin skin, Path skinDir, List<String> fileNames, SkinElement.ElementType defaultType) {
        for (String fileName : fileNames) {
            if (cancelCurrentTask.get() || Thread.currentThread().isInterrupted()) {
                break;
            }
            
            Path filePath = skinDir.resolve(fileName);
            if (Files.exists(filePath)) {
                try {
                    SkinElement.ElementType type = determineElementType(fileName);
                    SkinElement element = new SkinElement(type != null ? type : defaultType, filePath);
                    
                    // Thread-safe addition
                    synchronized (skin.getElements()) {
                        skin.addElement(element);
                    }
                } catch (Exception e) {
                    logger.debug("Could not load element: {}", fileName, e);
                }
            }
            
            // Also check for @2x versions
            String hdFileName = fileName.replace(".", "@2x.");
            Path hdPath = skinDir.resolve(hdFileName);
            if (Files.exists(hdPath)) {
                try {
                    SkinElement.ElementType type = determineElementType(hdFileName);
                    SkinElement element = new SkinElement(type != null ? type : defaultType, hdPath);
                    
                    // Thread-safe addition
                    synchronized (skin.getElements()) {
                        skin.addElement(element);
                    }
                } catch (Exception e) {
                    logger.debug("Could not load HD element: {}", hdFileName, e);
                }
            }
        }
    }
    
    /**
     * Determine element type from filename
     */
    private SkinElement.ElementType determineElementType(String fileName) {
        fileName = fileName.toLowerCase();
        
        // Check against all known element types
        for (SkinElement.ElementType type : SkinElement.ElementType.values()) {
            if (fileName.startsWith(type.getFileName().toLowerCase().replace(".png", "").replace(".wav", ""))) {
                return type;
            }
        }
        
        // Special cases
        if (fileName.contains("hitcircle")) return SkinElement.ElementType.HIT_CIRCLE;
        if (fileName.contains("cursor")) return SkinElement.ElementType.CURSOR;
        if (fileName.contains("sliderb")) return SkinElement.ElementType.SLIDER_BALL;
        if (fileName.contains("menu-back")) return SkinElement.ElementType.MENU_BACK;
        if (fileName.contains("normal-hit")) return SkinElement.ElementType.NORMAL_HITNORMAL;
        
        return null;
    }
    
    /**
     * Find preview image for the skin
     */
    private void findPreviewImage(Skin skin, Path skinDir) {
        List<String> previewNames = Arrays.asList(
            "preview.png", "preview.jpg", "screenshot.png",
            "menu-background.jpg", "menu-background.png"
        );
        
        for (String name : previewNames) {
            Path imagePath = skinDir.resolve(name);
            if (Files.exists(imagePath)) {
                skin.setPreviewImagePath(imagePath.toString());
                return;
            }
        }
    }
    
    /**
     * Cancel current loading task
     */
    public void cancelCurrentLoading() {
        cancelCurrentTask.set(true);
        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            currentLoadingTask.cancel();
        }
    }
    
    /**
     * Check if currently loading
     */
    public boolean isLoading() {
        return currentLoadingTask != null && currentLoadingTask.isRunning();
    }
    
    /**
     * Shutdown the loading executor service
     */
    public void shutdown() {
        cancelCurrentLoading();
        loadingExecutor.shutdownNow();
        try {
            if (!loadingExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                logger.warn("Loading executor did not terminate in time");
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for executor shutdown", e);
            Thread.currentThread().interrupt();
        }
    }
}