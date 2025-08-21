package com.osuskin.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class SkinIndexCache {
    private static final Logger logger = LoggerFactory.getLogger(SkinIndexCache.class);
    private static final String INDEX_DIR_NAME = "skin-indexes";
    private static final String INDEX_FILE_SUFFIX = "-index.json";
    private final Path indexStorageDir;
    private static final ObjectMapper mapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);
    
    public SkinIndexCache() {
        // Use application config directory for storing indexes
        String userHome = System.getProperty("user.home");
        Path configDir = Path.of(userHome, ".config", "OsuSkinTool");
        this.indexStorageDir = configDir.resolve(INDEX_DIR_NAME);
        
        // Create index storage directory if it doesn't exist
        try {
            Files.createDirectories(indexStorageDir);
            logger.info("Index storage directory: {}", indexStorageDir);
        } catch (IOException e) {
            logger.error("Failed to create index storage directory", e);
        }
    }
    
    private String getSkinIndexKey(Path skinDir) {
        // Create a unique key for each skin based on its absolute path
        String path = skinDir.toAbsolutePath().toString();
        // Replace path separators and invalid filename characters
        return path.replaceAll("[/\\\\:*?\"<>|]", "_") + INDEX_FILE_SUFFIX;
    }
    
    public static class SkinIndexResult {
        public SkinIndex index;
        public boolean usedCache;
        
        public SkinIndexResult(SkinIndex index, boolean usedCache) {
            this.index = index;
            this.usedCache = usedCache;
        }
    }
    
    public static class SkinIndex {
        public String version = "1.0";
        // Removed validation fields - we now trust cached indices
        public Set<String> availableElements = new HashSet<>();
        public Map<String, Integer> animationFrameCounts = new HashMap<>();
        public Map<String, String> metadata = new HashMap<>();
        
        public boolean isValid(Path skinDir) {
            // Simplified validation: always trust the cached index
            // Index will only be rebuilt when:
            // 1. Index file doesn't exist
            // 2. User explicitly requests refresh (reload button)
            // 3. User changes skin directory
            return true;
        }
    }
    
    public SkinIndexResult loadOrCreateIndex(Path skinDir) {
        // Get index file path in app's config directory
        String indexKey = getSkinIndexKey(skinDir);
        Path indexPath = indexStorageDir.resolve(indexKey);
        
        // Try to load existing index
        if (Files.exists(indexPath)) {
            try {
                SkinIndex index = mapper.readValue(indexPath.toFile(), SkinIndex.class);
                if (index.isValid(skinDir)) {
                    logger.info("✓ Using cached index for: {} (skipping rebuild)", skinDir.getFileName());
                    return new SkinIndexResult(index, true); // USED CACHE
                }
                logger.info("✗ Index invalid, rebuilding for: {}", skinDir.getFileName());
            } catch (Exception e) {
                logger.warn("Failed to load index, rebuilding", e);
            }
        }
        
        // Build new index
        try {
            SkinIndex newIndex = rebuildIndex(skinDir);
            return new SkinIndexResult(newIndex, false); // DID NOT USE CACHE
        } catch (IOException e) {
            logger.error("Failed to rebuild index", e);
            return new SkinIndexResult(new SkinIndex(), false); // Return empty index as fallback
        }
    }
    
    private SkinIndex rebuildIndex(Path skinDir) throws IOException {
        logger.info("Building index for skin: {}", skinDir.getFileName());
        SkinIndex index = new SkinIndex();
        
        // Parse skin.ini metadata if exists
        Path skinIni = skinDir.resolve("skin.ini");
        if (Files.exists(skinIni)) {
            parseSkinIniMetadata(skinIni, index.metadata);
        }
        
        // Scan for available elements
        try (Stream<Path> paths = Files.list(skinDir)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                String baseName = removeExtension(name);
                
                // Add to available elements
                index.availableElements.add(baseName);
                
                // Also add without @2x suffix for lookup
                if (baseName.endsWith("@2x")) {
                    index.availableElements.add(baseName.substring(0, baseName.length() - 3));
                }
                
                // Check for animation sequences
                if (baseName.matches(".*-\\d+$")) {
                    String animBase = baseName.replaceAll("-\\d+$", "");
                    index.animationFrameCounts.merge(animBase, 1, Integer::sum);
                }
            }
        }
        
        // Save index to app's config directory, NOT the skin directory!
        String indexKey = getSkinIndexKey(skinDir);
        Path indexPath = indexStorageDir.resolve(indexKey);
        mapper.writeValue(indexPath.toFile(), index);
        
        logger.info("Index created with {} elements, {} animations", 
            index.availableElements.size(), index.animationFrameCounts.size());
        
        return index;
    }
    
    
    private static void parseSkinIniMetadata(Path skinIni, Map<String, String> metadata) {
        try {
            // Try UTF-8 first, then fall back to ISO-8859-1 if that fails
            List<String> lines;
            try {
                lines = Files.readAllLines(skinIni, StandardCharsets.UTF_8);
            } catch (MalformedInputException e) {
                // Fallback to ISO-8859-1 for non-UTF-8 files
                lines = Files.readAllLines(skinIni, StandardCharsets.ISO_8859_1);
            }
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("Name:")) {
                    metadata.put("name", line.substring(5).trim());
                } else if (line.startsWith("Author:")) {
                    metadata.put("author", line.substring(7).trim());
                } else if (line.startsWith("Version:")) {
                    metadata.put("version", line.substring(8).trim());
                }
            }
        } catch (IOException e) {
            logger.warn("Error parsing skin.ini metadata", e);
        }
    }
    
    private static String removeExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }
    
    public void invalidateIndex(Path skinDir) {
        String indexKey = getSkinIndexKey(skinDir);
        Path indexPath = indexStorageDir.resolve(indexKey);
        try {
            Files.deleteIfExists(indexPath);
            logger.info("Invalidated index for: {}", skinDir.getFileName());
        } catch (IOException e) {
            logger.error("Error deleting index file", e);
        }
    }
}