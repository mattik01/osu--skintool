package com.osuskin.tool.service;

import com.osuskin.tool.model.Configuration;
import com.osuskin.tool.model.Skin;
import com.osuskin.tool.model.SkinElement;
import com.osuskin.tool.util.ConfigurationManager;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

public class SkinScannerService {
    
    private static final Logger logger = LoggerFactory.getLogger(SkinScannerService.class);
    
    private static final Set<String> SKIN_INI_NAMES = Set.of("skin.ini", "Skin.ini", "SKIN.INI");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(".wav", ".mp3", ".ogg");
    
    private final ConfigurationManager configurationManager;
    
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
        
        try (Stream<Path> directories = Files.walk(skinsDirectory, 1)) {
            directories
                .filter(Files::isDirectory)
                .filter(path -> !path.equals(skinsDirectory))
                .filter(this::isSkinDirectory)
                .forEach(skinDir -> {
                    try {
                        Skin skin = scanSingleSkin(skinDir);
                        if (skin != null) {
                            skins.add(skin);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to scan skin directory: {}", skinDir, e);
                    }
                });
        }
        
        // Update last scan time
        config.setLastScanTime(LocalDateTime.now());
        configurationManager.saveConfiguration();
        
        logger.info("Skin scan completed. Found {} skins", skins.size());
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
        }
    }
    
    private void scanSkinElements(Skin skin, Path skinDirectory) throws IOException {
        try (Stream<Path> files = Files.list(skinDirectory)) {
            files
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    String fileName = file.getFileName().toString();
                    SkinElement.ElementType elementType = SkinElement.ElementType.fromFileName(fileName);
                    
                    if (elementType != null) {
                        try {
                            SkinElement element = new SkinElement(elementType, file);
                            element.setFileSize(Files.size(file));
                            skin.addElement(element);
                            
                            logger.trace("Found skin element: {} -> {}", fileName, elementType);
                        } catch (IOException e) {
                            logger.debug("Could not get file size for: {}", file, e);
                        }
                    }
                });
        }
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
    
    private static class DirectoryStats {
        int fileCount = 0;
        long totalSize = 0;
        LocalDateTime lastModified = null;
    }
}