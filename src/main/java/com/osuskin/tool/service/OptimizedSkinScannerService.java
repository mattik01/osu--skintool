package com.osuskin.tool.service;

import com.osuskin.tool.model.Configuration;
import com.osuskin.tool.model.Skin;
import com.osuskin.tool.model.SkinElement;
import com.osuskin.tool.util.ConfigurationManager;
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
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Optimized skin scanner service with better performance for WSL and large collections
 */
public class OptimizedSkinScannerService {
    
    private static final Logger logger = LoggerFactory.getLogger(OptimizedSkinScannerService.class);
    
    private static final Set<String> SKIN_INI_NAMES = Set.of("skin.ini", "Skin.ini", "SKIN.INI");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(".wav", ".mp3", ".ogg");
    private static final Set<String> COMPRESSED_EXTENSIONS = Set.of(".zip", ".osk", ".tar", ".tar.gz", ".7z");
    
    // Performance settings
    private static final int THREAD_POOL_SIZE = 4; // Parallel scanning threads
    private static final boolean LIGHTWEIGHT_SCAN = true; // Skip counting all files, just get metadata
    private static final int MAX_FILES_PER_SKIN = 100; // Limit file scanning for performance
    
    private final ConfigurationManager configurationManager;
    private final ExecutorService executorService;
    private List<Path> compressedSkinFiles = new ArrayList<>();
    
    // Cache for skin metadata
    private final Map<Path, Skin> skinCache = new ConcurrentHashMap<>();
    private LocalDateTime lastCacheTime;
    
    public OptimizedSkinScannerService(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }
    
    public Task<List<Skin>> createScanTask() {
        return new Task<List<Skin>>() {
            @Override
            protected List<Skin> call() throws Exception {
                updateMessage("Scanning skins directory...");
                return scanSkins();
            }
        };
    }
    
    public Task<List<Skin>> createQuickScanTask() {
        return new Task<List<Skin>>() {
            @Override
            protected List<Skin> call() throws Exception {
                updateMessage("Quick scanning skins...");
                return quickScanSkins();
            }
        };
    }
    
    /**
     * Quick scan - only reads skin.ini for metadata, doesn't count files
     */
    public List<Skin> quickScanSkins() throws IOException {
        Configuration config = configurationManager.getConfiguration();
        Path skinsDirectory = config.getOsuSkinsDirectoryPath();
        
        if (skinsDirectory == null || !Files.exists(skinsDirectory)) {
            logger.warn("Skins directory does not exist: {}", skinsDirectory);
            return Collections.emptyList();
        }
        
        logger.info("Starting quick skin scan in directory: {}", skinsDirectory);
        List<Skin> skins = Collections.synchronizedList(new ArrayList<>());
        
        // Add Skin Container first
        addSkinContainer(skins, config, skinsDirectory);
        
        // Get all skin directories
        List<Path> skinDirectories = new ArrayList<>();
        try (Stream<Path> entries = Files.walk(skinsDirectory, 1)) {
            entries
                .filter(path -> !path.equals(skinsDirectory))
                .filter(path -> !path.equals(config.getSkinContainerPathAsPath()))
                .filter(Files::isDirectory)
                .forEach(skinDirectories::add);
        }
        
        logger.info("Found {} potential skin directories", skinDirectories.size());
        
        // Process skins in parallel
        List<Future<Skin>> futures = new ArrayList<>();
        for (Path skinDir : skinDirectories) {
            futures.add(executorService.submit(() -> quickScanSingleSkin(skinDir)));
        }
        
        // Collect results
        for (Future<Skin> future : futures) {
            try {
                Skin skin = future.get(5, TimeUnit.SECONDS); // 5 second timeout per skin
                if (skin != null) {
                    skins.add(skin);
                }
            } catch (TimeoutException e) {
                logger.warn("Timeout scanning skin, skipping");
            } catch (Exception e) {
                logger.debug("Error scanning skin", e);
            }
        }
        
        // Update last scan time
        config.setLastScanTime(LocalDateTime.now());
        configurationManager.saveConfiguration();
        
        logger.info("Quick scan completed. Found {} skins", skins.size());
        return skins;
    }
    
    /**
     * Full scan with file counting (slower)
     */
    public List<Skin> scanSkins() throws IOException {
        // Use cached results if recent enough (within 5 minutes)
        if (lastCacheTime != null && 
            lastCacheTime.isAfter(LocalDateTime.now().minusMinutes(5)) && 
            !skinCache.isEmpty()) {
            logger.info("Using cached skin data");
            return new ArrayList<>(skinCache.values());
        }
        
        Configuration config = configurationManager.getConfiguration();
        Path skinsDirectory = config.getOsuSkinsDirectoryPath();
        
        if (skinsDirectory == null || !Files.exists(skinsDirectory)) {
            logger.warn("Skins directory does not exist: {}", skinsDirectory);
            return Collections.emptyList();
        }
        
        logger.info("Starting full skin scan in directory: {}", skinsDirectory);
        List<Skin> skins = Collections.synchronizedList(new ArrayList<>());
        compressedSkinFiles.clear();
        skinCache.clear();
        
        // Add Skin Container first
        addSkinContainer(skins, config, skinsDirectory);
        
        // Get all skin directories
        List<Path> skinDirectories = new ArrayList<>();
        try (Stream<Path> entries = Files.walk(skinsDirectory, 1)) {
            entries
                .filter(path -> !path.equals(skinsDirectory))
                .filter(path -> !path.equals(config.getSkinContainerPathAsPath()))
                .forEach(entry -> {
                    if (Files.isDirectory(entry)) {
                        skinDirectories.add(entry);
                    } else if (Files.isRegularFile(entry) && isCompressedSkinFile(entry)) {
                        compressedSkinFiles.add(entry);
                    }
                });
        }
        
        logger.info("Found {} potential skin directories, {} compressed files", 
                   skinDirectories.size(), compressedSkinFiles.size());
        
        // Process skins in parallel
        List<Future<Skin>> futures = new ArrayList<>();
        for (Path skinDir : skinDirectories) {
            futures.add(executorService.submit(() -> scanSingleSkin(skinDir)));
        }
        
        // Collect results
        for (Future<Skin> future : futures) {
            try {
                Skin skin = future.get(10, TimeUnit.SECONDS); // 10 second timeout per skin
                if (skin != null) {
                    skins.add(skin);
                    skinCache.put(skin.getDirectoryPathAsPath(), skin);
                }
            } catch (TimeoutException e) {
                logger.warn("Timeout scanning skin, skipping");
            } catch (Exception e) {
                logger.debug("Error scanning skin", e);
            }
        }
        
        // Update cache time
        lastCacheTime = LocalDateTime.now();
        
        // Update last scan time
        config.setLastScanTime(LocalDateTime.now());
        configurationManager.saveConfiguration();
        
        logger.info("Full scan completed. Found {} skins", skins.size());
        return skins;
    }
    
    private void addSkinContainer(List<Skin> skins, Configuration config, Path skinsDirectory) {
        Path skinContainerPath = config.getSkinContainerPathAsPath();
        if (skinContainerPath == null) {
            skinContainerPath = skinsDirectory.resolve("Skin Container");
            config.setSkinContainerPathAsPath(skinContainerPath);
            try {
                configurationManager.saveConfiguration();
            } catch (Exception e) {
                logger.error("Failed to save configuration", e);
            }
        }
        
        // Create Skin Container directory if it doesn't exist
        if (!Files.exists(skinContainerPath)) {
            try {
                Files.createDirectories(skinContainerPath);
                logger.info("Created Skin Container directory at: {}", skinContainerPath);
            } catch (IOException e) {
                logger.error("Failed to create Skin Container directory", e);
            }
        }
        
        // Add Skin Container
        Skin skinContainer = new Skin("Skin Container", skinContainerPath);
        skinContainer.setSpecial(true);
        skinContainer.setFileCount(0);
        skinContainer.setTotalSize(0);
        skinContainer.setLastModified(LocalDateTime.now());
        skins.add(skinContainer);
    }
    
    /**
     * Quick scan - only checks for skin.ini and gets basic metadata
     */
    private Skin quickScanSingleSkin(Path skinDirectory) {
        try {
            String skinName = skinDirectory.getFileName().toString();
            
            // Quick check if it's a skin directory
            if (!quickCheckIsSkin(skinDirectory)) {
                return null;
            }
            
            Skin skin = new Skin(skinName, skinDirectory);
            
            // Only parse skin.ini for metadata
            parseSkinIniWithEncoding(skin, skinDirectory);
            
            // Set basic stats without full file scan
            BasicFileAttributes attrs = Files.readAttributes(skinDirectory, BasicFileAttributes.class);
            skin.setLastModified(LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()));
            skin.setFileCount(-1); // Indicate not counted
            skin.setTotalSize(0);
            
            // Quick preview image check
            findPreviewImageQuick(skin, skinDirectory);
            
            return skin;
        } catch (Exception e) {
            logger.debug("Error in quick scan for: {}", skinDirectory, e);
            return null;
        }
    }
    
    /**
     * Full scan with file counting
     */
    private Skin scanSingleSkin(Path skinDirectory) {
        try {
            String skinName = skinDirectory.getFileName().toString();
            logger.debug("Scanning skin: {}", skinName);
            
            Skin skin = new Skin(skinName, skinDirectory);
            
            // Parse skin.ini with proper encoding handling
            parseSkinIniWithEncoding(skin, skinDirectory);
            
            // Get basic directory stats (lightweight)
            if (LIGHTWEIGHT_SCAN) {
                BasicFileAttributes attrs = Files.readAttributes(skinDirectory, BasicFileAttributes.class);
                skin.setLastModified(LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()));
                skin.setFileCount(countFilesLightweight(skinDirectory));
                skin.setTotalSize(0); // Skip size calculation for performance
            } else {
                DirectoryStats stats = calculateDirectoryStats(skinDirectory);
                skin.setFileCount(stats.fileCount);
                skin.setTotalSize(stats.totalSize);
                skin.setLastModified(stats.lastModified);
            }
            
            // Scan for key skin elements only (not all files)
            scanKeyElements(skin, skinDirectory);
            
            // Find preview image
            findPreviewImageQuick(skin, skinDirectory);
            
            logger.debug("Completed scanning skin: {} ({} elements)", skinName, skin.getElementCount());
            return skin;
        } catch (Exception e) {
            logger.warn("Failed to scan skin directory: {}", skinDirectory, e);
            return null;
        }
    }
    
    /**
     * Parse skin.ini with multiple encoding fallbacks
     */
    private void parseSkinIniWithEncoding(Skin skin, Path skinDirectory) {
        // Try different encodings
        List<Charset> charsets = Arrays.asList(
            StandardCharsets.UTF_8,
            StandardCharsets.ISO_8859_1,
            Charset.forName("Windows-1252"),
            StandardCharsets.US_ASCII
        );
        
        for (String iniFileName : SKIN_INI_NAMES) {
            Path iniPath = skinDirectory.resolve(iniFileName);
            if (Files.exists(iniPath)) {
                for (Charset charset : charsets) {
                    try {
                        List<String> lines = Files.readAllLines(iniPath, charset);
                        parseSkinIniContent(skin, lines);
                        return; // Success, exit
                    } catch (Exception e) {
                        // Try next encoding
                    }
                }
                
                // If all encodings fail, try reading with error replacement
                try {
                    List<String> lines = readLinesWithFallback(iniPath);
                    parseSkinIniContent(skin, lines);
                    return;
                } catch (Exception e) {
                    logger.debug("Could not parse skin.ini with any encoding: {}", iniPath);
                }
            }
        }
    }
    
    /**
     * Read file lines with invalid character replacement
     */
    private List<String> readLinesWithFallback(Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Replace invalid characters
                line = line.replaceAll("[^\\x00-\\x7F]", "?");
                lines.add(line);
            }
        }
        return lines;
    }
    
    private void parseSkinIniContent(Skin skin, List<String> lines) {
        String currentSection = "";
        
        for (String line : lines) {
            line = line.trim();
            
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length() - 1);
                continue;
            }
            
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    
                    if ("General".equalsIgnoreCase(currentSection)) {
                        switch (key.toLowerCase()) {
                            case "name" -> skin.setName(value.isEmpty() ? skin.getName() : value);
                            case "author" -> skin.setAuthor(value);
                            case "version" -> skin.setVersion(value);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Quick check if directory contains skin files
     */
    private boolean quickCheckIsSkin(Path directory) {
        try {
            // Just check for skin.ini or a few key files
            for (String iniName : SKIN_INI_NAMES) {
                if (Files.exists(directory.resolve(iniName))) {
                    return true;
                }
            }
            
            // Check for at least one skin element file
            try (Stream<Path> files = Files.list(directory)) {
                return files
                    .limit(20) // Only check first 20 files
                    .anyMatch(file -> {
                        String name = file.getFileName().toString().toLowerCase();
                        return name.contains("hitcircle") || 
                               name.contains("cursor") || 
                               name.contains("default-");
                    });
            }
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Lightweight file counting (just counts, doesn't calculate sizes)
     */
    private int countFilesLightweight(Path directory) {
        try (Stream<Path> files = Files.walk(directory, 1)) {
            return (int) files
                .filter(Files::isRegularFile)
                .limit(MAX_FILES_PER_SKIN)
                .count();
        } catch (IOException e) {
            return 0;
        }
    }
    
    /**
     * Scan only key skin elements for preview
     */
    private void scanKeyElements(Skin skin, Path directory) {
        // Only scan for important elements
        Set<String> keyElements = Set.of(
            "hitcircle.png", "cursor.png", "default-0.png",
            "menu-back.png", "scorebar-bg.png"
        );
        
        try (Stream<Path> files = Files.list(directory)) {
            files.limit(MAX_FILES_PER_SKIN)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    String fileName = file.getFileName().toString().toLowerCase();
                    if (keyElements.contains(fileName)) {
                        SkinElement.ElementType type = determineElementType(fileName);
                        SkinElement element = new SkinElement(type, file);
                        skin.addElement(element);
                    }
                });
        } catch (IOException e) {
            logger.debug("Error scanning elements", e);
        }
    }
    
    /**
     * Quick preview image search
     */
    private void findPreviewImageQuick(Skin skin, Path directory) {
        List<String> previewNames = Arrays.asList(
            "preview.png", "preview.jpg", "screenshot.png", 
            "menu-background.jpg", "menu-background.png"
        );
        
        for (String name : previewNames) {
            Path imagePath = directory.resolve(name);
            if (Files.exists(imagePath)) {
                skin.setPreviewImagePath(imagePath.toString());
                return;
            }
        }
    }
    
    private SkinElement.ElementType determineElementType(String fileName) {
        if (fileName.contains("hitcircle")) return SkinElement.ElementType.HIT_CIRCLE;
        if (fileName.contains("cursor")) return SkinElement.ElementType.CURSOR;
        if (fileName.contains("menu-back")) return SkinElement.ElementType.MENU_BACK;
        if (fileName.contains("sliderb")) return SkinElement.ElementType.SLIDER_BALL;
        // Default to cursor if no match (since we need to return something)
        return SkinElement.ElementType.CURSOR;
    }
    
    private boolean isCompressedSkinFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return COMPRESSED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
    
    private static class DirectoryStats {
        int fileCount;
        long totalSize;
        LocalDateTime lastModified;
    }
    
    private DirectoryStats calculateDirectoryStats(Path directory) throws IOException {
        DirectoryStats stats = new DirectoryStats();
        stats.lastModified = LocalDateTime.MIN;
        
        Files.walkFileTree(directory, EnumSet.noneOf(FileVisitOption.class), 2, // Max depth 2
            new SimpleFileVisitor<Path>() {
                private int count = 0;
                
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (++count > MAX_FILES_PER_SKIN) {
                        return FileVisitResult.TERMINATE;
                    }
                    
                    stats.fileCount++;
                    stats.totalSize += attrs.size();
                    
                    LocalDateTime fileModified = LocalDateTime.ofInstant(
                        attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                    if (fileModified.isAfter(stats.lastModified)) {
                        stats.lastModified = fileModified;
                    }
                    
                    return FileVisitResult.CONTINUE;
                }
            });
        
        return stats;
    }
    
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
    
    // Methods for compatibility with MainController
    
    public int getCompressedSkinCount() {
        return compressedSkinFiles.size();
    }
    
    public List<Path> getExtractableCompressedSkinFiles() {
        // Return compressed files that haven't been extracted yet
        // For now, return all compressed files
        return new ArrayList<>(compressedSkinFiles);
    }
    
    public boolean extractCompressedSkin(Path compressedFile) throws IOException {
        // TODO: Implement extraction logic
        // For now, return false (not implemented)
        logger.warn("Extraction not yet implemented for: {}", compressedFile);
        return false;
    }
    
    public Skin scanSkin(Path skinPath) throws IOException {
        // Scan a single skin directory
        if (!Files.isDirectory(skinPath)) {
            return null;
        }
        return scanSingleSkin(skinPath);
    }
}