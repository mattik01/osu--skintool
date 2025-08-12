package com.osuskin.tool.service;

import com.osuskin.tool.model.Configuration;
import com.osuskin.tool.model.Skin;
import com.osuskin.tool.model.SkinElement;
import com.osuskin.tool.util.ConfigurationManager;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class SkinScannerService {
    
    private static final Logger logger = LoggerFactory.getLogger(SkinScannerService.class);
    
    private static final Set<String> SKIN_INI_NAMES = Set.of("skin.ini", "Skin.ini", "SKIN.INI");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(".wav", ".mp3", ".ogg");
    private static final Set<String> COMPRESSED_EXTENSIONS = Set.of(".zip", ".osk", ".tar", ".tar.gz", ".7z");
    
    private final ConfigurationManager configurationManager;
    private List<Path> compressedSkinFiles = new ArrayList<>();
    
    public SkinScannerService(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }
    
    public Task<List<Skin>> createScanTask() {
        return new Task<List<Skin>>() {
            @Override
            protected List<Skin> call() throws Exception {
                return scanSkins();
            }
        };
    }
    
    public List<Skin> scanSkins() throws IOException {
        Configuration config = configurationManager.getConfiguration();
        Path skinsDirectory = config.getOsuSkinsDirectoryPath();
        
        if (skinsDirectory == null || !Files.exists(skinsDirectory)) {
            logger.warn("Skins directory does not exist: {}", skinsDirectory);
            return Collections.emptyList();
        }
        
        logger.info("Starting skin scan in directory: {}", skinsDirectory);
        List<Skin> skins = new ArrayList<>();
        compressedSkinFiles.clear();
        
        // First, ensure Skin Container exists and add it as the first skin
        Path skinContainerPath = config.getSkinContainerPathAsPath();
        if (skinContainerPath == null) {
            skinContainerPath = skinsDirectory.resolve("Skin Container");
            config.setSkinContainerPathAsPath(skinContainerPath);
            configurationManager.saveConfiguration();
        }
        final Path finalSkinContainerPath = skinContainerPath;
        
        // Create Skin Container directory if it doesn't exist
        if (!Files.exists(skinContainerPath)) {
            try {
                Files.createDirectories(skinContainerPath);
                logger.info("Created Skin Container directory at: {}", skinContainerPath);
            } catch (IOException e) {
                logger.error("Failed to create Skin Container directory", e);
            }
        }
        
        // Always add Skin Container as first skin (even if empty)
        try {
            Skin skinContainer = scanSingleSkin(skinContainerPath);
            if (skinContainer != null) {
                skinContainer.setName("Skin Container");  // Special name
                skinContainer.setSpecial(true);  // Mark as special
                skins.add(skinContainer);
            } else {
                // Create a special empty skin for the container
                skinContainer = new Skin("Skin Container", skinContainerPath);
                skinContainer.setSpecial(true);
                skinContainer.setFileCount(0);
                skinContainer.setTotalSize(0);
                skinContainer.setLastModified(LocalDateTime.now());
                skins.add(skinContainer);
            }
        } catch (Exception e) {
            logger.warn("Failed to scan Skin Container directory: {}", skinContainerPath, e);
            // Even on error, add an empty Skin Container entry
            Skin skinContainer = new Skin("Skin Container", skinContainerPath);
            skinContainer.setSpecial(true);
            skinContainer.setFileCount(0);
            skinContainer.setTotalSize(0);
            skinContainer.setLastModified(LocalDateTime.now());
            skins.add(skinContainer);
        }
        
        // Then scan other skins
        try (Stream<Path> entries = Files.walk(skinsDirectory, 1)) {
            entries
                .filter(path -> !path.equals(skinsDirectory))
                .filter(path -> !path.equals(finalSkinContainerPath))  // Skip Skin Container, already added
                .forEach(entry -> {
                    if (Files.isDirectory(entry) && isSkinDirectory(entry)) {
                        try {
                            Skin skin = scanSingleSkin(entry);
                            if (skin != null) {
                                skins.add(skin);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to scan skin directory: {}", entry, e);
                        }
                    } else if (Files.isRegularFile(entry) && isCompressedSkinFile(entry)) {
                        compressedSkinFiles.add(entry);
                        logger.debug("Found compressed skin file: {}", entry.getFileName());
                    }
                });
        }
        
        // Update last scan time
        config.setLastScanTime(LocalDateTime.now());
        configurationManager.saveConfiguration();
        
        logger.info("Skin scan completed. Found {} extracted skins, {} compressed skin files", 
                   skins.size(), compressedSkinFiles.size());
        return skins;
    }
    
    private boolean isSkinDirectory(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.anyMatch(this::isSkinFile);
        } catch (IOException e) {
            logger.debug("Could not check directory: {}", directory, e);
            return false;
        }
    }
    
    private boolean isSkinFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        
        String fileName = file.getFileName().toString();
        
        // Check for skin.ini
        if (SKIN_INI_NAMES.contains(fileName)) {
            return true;
        }
        
        // Check for common skin elements
        for (SkinElement.ElementType elementType : SkinElement.ElementType.values()) {
            if (elementType.getFileName().equalsIgnoreCase(fileName)) {
                return true;
            }
        }
        
        return false;
    }
    
    private Skin scanSingleSkin(Path skinDirectory) throws IOException {
        String skinName = skinDirectory.getFileName().toString();
        logger.debug("Scanning skin: {}", skinName);
        
        Skin skin = new Skin(skinName, skinDirectory);
        
        // Get directory stats
        DirectoryStats stats = calculateDirectoryStats(skinDirectory);
        skin.setFileCount(stats.fileCount);
        skin.setTotalSize(stats.totalSize);
        skin.setLastModified(stats.lastModified);
        
        // Parse skin.ini if present
        parseSkinIni(skin, skinDirectory);
        
        // Scan for skin elements
        scanSkinElements(skin, skinDirectory);
        
        // Find preview image
        findPreviewImage(skin, skinDirectory);
        
        logger.debug("Completed scanning skin: {} ({} elements)", skinName, skin.getElementCount());
        return skin;
    }
    
    private void parseSkinIni(Skin skin, Path skinDirectory) {
        for (String iniFileName : SKIN_INI_NAMES) {
            Path iniPath = skinDirectory.resolve(iniFileName);
            if (Files.exists(iniPath)) {
                try {
                    List<String> lines = Files.readAllLines(iniPath);
                    parseSkinIniContent(skin, lines);
                    break;
                } catch (IOException e) {
                    logger.debug("Could not read skin.ini from: {}", iniPath, e);
                }
            }
        }
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
                    
                    parseSkinIniProperty(skin, currentSection, key, value);
                }
            }
        }
    }
    
    private void parseSkinIniProperty(Skin skin, String section, String key, String value) {
        if ("General".equalsIgnoreCase(section)) {
            switch (key.toLowerCase()) {
                case "name" -> skin.setName(value.isEmpty() ? skin.getName() : value);
                case "author" -> skin.setAuthor(value);
                case "version" -> skin.setVersion(value);
            }
        } else if ("Colours".equalsIgnoreCase(section) || "Colors".equalsIgnoreCase(section)) {
            // Parse combo colors (Combo1, Combo2, etc.)
            if (key.toLowerCase().startsWith("combo")) {
                try {
                    // Parse RGB values (format: "R,G,B" or "R , G , B")
                    String[] rgb = value.split(",");
                    if (rgb.length == 3) {
                        int r = Integer.parseInt(rgb[0].trim());
                        int g = Integer.parseInt(rgb[1].trim());
                        int b = Integer.parseInt(rgb[2].trim());
                        
                        // Clamp values to 0-255
                        r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));
                        
                        skin.addComboColor(r, g, b);
                        logger.debug("Added combo color: RGB({},{},{})", r, g, b);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid combo color format in skin.ini: {} = {}", key, value);
                }
            }
        }
    }
    
    private void scanSkinElements(Skin skin, Path skinDirectory) throws IOException {
        // For Skin Container, we need ALL files for proper selection
        boolean isSkinContainer = skin.getName().equals("Skin Container");
        
        try (Stream<Path> files = Files.list(skinDirectory)) {
            files
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    String fileName = file.getFileName().toString();
                    String lowerFileName = fileName.toLowerCase();
                    
                    // Skip skin.ini as it's metadata, not an element
                    if (lowerFileName.equals("skin.ini")) {
                        return;
                    }
                    
                    // For skin container or selection purposes, include ALL files
                    // For normal skins, only scan files we might need for preview
                    if (isSkinContainer || shouldIncludeFile(lowerFileName)) {
                        SkinElement.ElementType elementType = SkinElement.ElementType.fromFileName(lowerFileName);
                        
                        try {
                            SkinElement element;
                            if (elementType != null) {
                                element = new SkinElement(elementType, file);
                            } else {
                                // Create element without type but with actual filename
                                element = new SkinElement();
                                element.setFilePath(file.toString());
                                element.setFileName(fileName); // Use original case
                                element.setExists(true);
                            }
                            element.setFileSize(Files.size(file));
                            skin.addElement(element);
                            
                            logger.trace("Found skin element: {}", fileName);
                        } catch (IOException e) {
                            logger.debug("Could not get file size for: {}", file, e);
                        }
                    }
                });
        }
    }
    
    private boolean shouldIncludeFile(String lowerFileName) {
        // Include all files that might be skin-related
        // This includes images, audio, ini files (except skin.ini), txt files, etc.
        return lowerFileName.endsWith(".png") || lowerFileName.endsWith(".jpg") || 
               lowerFileName.endsWith(".jpeg") || lowerFileName.endsWith(".wav") || 
               lowerFileName.endsWith(".mp3") || lowerFileName.endsWith(".ogg") ||
               lowerFileName.endsWith(".ini") || lowerFileName.endsWith(".txt") ||
               lowerFileName.endsWith(".cfg") || lowerFileName.endsWith(".osk") ||
               lowerFileName.endsWith(".osu") || lowerFileName.endsWith(".osb");
    }
    
    public Skin scanSkin(Path skinDirectory) throws IOException {
        String skinName = skinDirectory.getFileName().toString();
        Skin skin = new Skin(skinName, skinDirectory);
        
        // Mark as special if it's the Skin Container
        if (skinName.equals("Skin Container")) {
            skin.setSpecial(true);
        }
        
        // Parse skin.ini if present
        parseSkinIni(skin, skinDirectory);
        
        // Scan for skin elements
        scanSkinElements(skin, skinDirectory);
        
        // Find preview image
        findPreviewImage(skin, skinDirectory);
        
        // Set basic statistics
        skin.setFileCount(skin.getElements().size());
        long totalSize = skin.getElements().stream()
            .mapToLong(SkinElement::getFileSize)
            .sum();
        skin.setTotalSize(totalSize);
        
        return skin;
    }
    
    private void findPreviewImage(Skin skin, Path skinDirectory) {
        // Look for common preview image names
        String[] previewNames = {
            "menu-back.png", "menu-background.png", "preview.png", 
            "screenshot.png", "skin-preview.png"
        };
        
        for (String previewName : previewNames) {
            Path previewPath = skinDirectory.resolve(previewName);
            if (Files.exists(previewPath)) {
                skin.setPreviewImagePathAsPath(previewPath);
                break;
            }
        }
        
        // If no specific preview found, use the first large image
        if (skin.getPreviewImagePathAsPath() == null) {
            try (Stream<Path> files = Files.list(skinDirectory)) {
                Optional<Path> firstImage = files
                    .filter(Files::isRegularFile)
                    .filter(this::isImageFile)
                    .filter(this::isLargeEnoughForPreview)
                    .findFirst();
                
                firstImage.ifPresent(skin::setPreviewImagePathAsPath);
            } catch (IOException e) {
                logger.debug("Could not search for preview image in: {}", skinDirectory, e);
            }
        }
    }
    
    private boolean isImageFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
    
    private boolean isLargeEnoughForPreview(Path file) {
        try {
            return Files.size(file) > 10000; // At least 10KB
        } catch (IOException e) {
            return false;
        }
    }
    
    private DirectoryStats calculateDirectoryStats(Path directory) throws IOException {
        DirectoryStats stats = new DirectoryStats();
        
        Files.walkFileTree(directory, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                stats.fileCount++;
                stats.totalSize += attrs.size();
                
                LocalDateTime fileTime = LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                if (stats.lastModified == null || fileTime.isAfter(stats.lastModified)) {
                    stats.lastModified = fileTime;
                }
                
                return FileVisitResult.CONTINUE;
            }
        });
        
        return stats;
    }
    
    private boolean isCompressedSkinFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        
        String fileName = file.getFileName().toString().toLowerCase();
        boolean isCompressedFormat = COMPRESSED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
        
        if (!isCompressedFormat) {
            return false;
        }
        
        // Check if this compressed file has already been extracted
        String originalFileName = file.getFileName().toString();
        String extractedDirName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        Path extractedDir = file.getParent().resolve(extractedDirName);
        
        // Only consider it a "compressed skin file" if it hasn't been extracted yet
        return !Files.exists(extractedDir);
    }
    
    public List<Path> getCompressedSkinFiles() {
        return new ArrayList<>(compressedSkinFiles);
    }
    
    public int getCompressedSkinCount() {
        return compressedSkinFiles.size();
    }
    
    /**
     * Get all compressed skin files that can be extracted (only those not yet extracted)
     */
    public List<Path> getExtractableCompressedSkinFiles() {
        Configuration config = configurationManager.getConfiguration();
        Path skinsDirectory = config.getOsuSkinsDirectoryPath();
        
        if (skinsDirectory == null || !Files.exists(skinsDirectory)) {
            return Collections.emptyList();
        }
        
        List<Path> extractable = new ArrayList<>();
        try (Stream<Path> entries = Files.walk(skinsDirectory, 1)) {
            entries
                .filter(Files::isRegularFile)
                .filter(this::isCompressedFormat)
                .filter(file -> {
                    // Only include files that haven't been extracted yet
                    String originalFileName = file.getFileName().toString();
                    String extractedDirName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
                    Path extractedDir = file.getParent().resolve(extractedDirName);
                    return !Files.exists(extractedDir);
                })
                .forEach(extractable::add);
        } catch (IOException e) {
            logger.error("Error finding extractable compressed files", e);
        }
        
        return extractable;
    }
    
    private boolean isCompressedFormat(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return COMPRESSED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
    
    public boolean extractCompressedSkin(Path compressedFile) throws IOException {
        if (!Files.exists(compressedFile)) {
            logger.warn("Compressed file does not exist: {}", compressedFile);
            return false;
        }
        
        Configuration config = configurationManager.getConfiguration();
        Path skinsDirectory = config.getOsuSkinsDirectoryPath();
        
        if (skinsDirectory == null || !Files.exists(skinsDirectory)) {
            logger.warn("Skins directory does not exist: {}", skinsDirectory);
            return false;
        }
        
        String fileName = compressedFile.getFileName().toString();
        String skinName = fileName.substring(0, fileName.lastIndexOf('.'));
        Path extractPath = skinsDirectory.resolve(skinName);
        
        // Create extraction directory
        if (Files.exists(extractPath)) {
            logger.warn("Directory already exists: {}", extractPath);
            return false;
        }
        
        try {
            Files.createDirectories(extractPath);
            
            String extension = fileName.toLowerCase();
            if (extension.endsWith(".zip") || extension.endsWith(".osk")) {
                return extractZipFile(compressedFile, extractPath);
            }
            // Add support for other formats if needed
            
            logger.warn("Unsupported compressed file format: {}", fileName);
            return false;
            
        } catch (IOException e) {
            logger.error("Failed to extract compressed skin: {}", compressedFile, e);
            if (Files.exists(extractPath)) {
                try {
                    Files.walk(extractPath)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException ex) {
                                logger.debug("Failed to cleanup: {}", path);
                            }
                        });
                } catch (IOException ex) {
                    logger.debug("Failed to cleanup directory: {}", extractPath);
                }
            }
            throw e;
        }
    }
    
    private boolean extractZipFile(Path zipFile, Path extractPath) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            // Analyze ZIP structure to detect nested folder pattern
            String rootFolderToStrip = detectRootFolder(zip);
            
            zip.stream().forEach(entry -> {
                try {
                    String entryName = entry.getName();
                    
                    // Skip the root folder if we detected one to strip
                    if (rootFolderToStrip != null && entryName.startsWith(rootFolderToStrip)) {
                        // Remove the root folder prefix
                        entryName = entryName.substring(rootFolderToStrip.length());
                        
                        // Skip if this results in an empty path (the root folder entry itself)
                        if (entryName.isEmpty()) {
                            return;
                        }
                    }
                    
                    Path entryPath = extractPath.resolve(entryName);
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        try (InputStream in = zip.getInputStream(entry)) {
                            Files.copy(in, entryPath);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Failed to extract entry: {}", entry.getName(), e);
                }
            });
        }
        
        logger.info("Successfully extracted skin: {} -> {}", zipFile.getFileName(), extractPath.getFileName());
        return true;
    }
    
    /**
     * Detects if a ZIP file has all its content in a single root folder that should be stripped.
     * Returns the folder prefix to strip, or null if no stripping is needed.
     */
    private String detectRootFolder(ZipFile zip) {
        List<String> entryNames = zip.stream()
            .map(entry -> entry.getName())
            .filter(name -> !name.isEmpty())
            .toList();
            
        if (entryNames.isEmpty()) {
            return null;
        }
        
        // Find potential root folder - look for entries that contain skin files
        Set<String> potentialRootFolders = new HashSet<>();
        boolean hasRootLevelSkinFiles = false;
        
        for (String entryName : entryNames) {
            // Check if this entry contains skin-related files at root level
            if (!entryName.contains("/") && isSkinRelatedFile(entryName)) {
                hasRootLevelSkinFiles = true;
                break;
            }
            
            // Check for skin files in subdirectories
            if (entryName.contains("/")) {
                String[] parts = entryName.split("/");
                if (parts.length >= 2 && isSkinRelatedFile(parts[parts.length - 1])) {
                    potentialRootFolders.add(parts[0] + "/");
                }
            }
        }
        
        // If there are skin files at root level, don't strip anything
        if (hasRootLevelSkinFiles) {
            return null;
        }
        
        // If all skin files are in a single subdirectory, strip that directory
        if (potentialRootFolders.size() == 1) {
            String rootFolder = potentialRootFolders.iterator().next();
            
            // Verify that most entries are in this folder
            long entriesInRoot = entryNames.stream()
                .filter(name -> name.startsWith(rootFolder))
                .count();
                
            // If more than 80% of entries are in this folder, strip it
            if (entriesInRoot > entryNames.size() * 0.8) {
                logger.debug("Detected nested folder structure, will strip root folder: {}", rootFolder);
                return rootFolder;
            }
        }
        
        return null;
    }
    
    /**
     * Checks if a filename is related to osu! skin files
     */
    private boolean isSkinRelatedFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        
        String lowerName = filename.toLowerCase();
        
        // Check for skin.ini
        if (SKIN_INI_NAMES.stream().anyMatch(name -> name.toLowerCase().equals(lowerName))) {
            return true;
        }
        
        // Check for common skin elements
        for (SkinElement.ElementType elementType : SkinElement.ElementType.values()) {
            if (elementType.getFileName().toLowerCase().equals(lowerName)) {
                return true;
            }
        }
        
        // Check for common skin file patterns
        return lowerName.startsWith("hitcircle") ||
               lowerName.startsWith("cursor") ||
               lowerName.startsWith("slider") ||
               lowerName.startsWith("spinner") ||
               lowerName.startsWith("menu-") ||
               lowerName.startsWith("score-") ||
               lowerName.startsWith("ranking-") ||
               lowerName.contains("hitnormal") ||
               lowerName.contains("hitclap") ||
               lowerName.contains("hitfinish") ||
               lowerName.contains("hitwhistle");
    }
    
    private static class DirectoryStats {
        int fileCount = 0;
        long totalSize = 0;
        LocalDateTime lastModified = null;
    }
}