package com.osuskin.tool.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.osuskin.tool.model.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ConfigurationManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);
    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String APP_DATA_FOLDER = "OsuSkinTool";
    
    private final ObjectMapper objectMapper;
    private final Path configFilePath;
    private Configuration configuration;
    
    public ConfigurationManager() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.configFilePath = getConfigFilePath();
        this.configuration = new Configuration();
        
        // Set default cache directory
        Path defaultCacheDir = getAppDataDirectory().resolve("cache");
        configuration.setCacheDirectoryPath(defaultCacheDir);
        
        logger.info("Configuration manager initialized. Config file: {}", configFilePath);
    }
    
    public Configuration getConfiguration() {
        return configuration;
    }
    
    public void loadConfiguration() {
        try {
            if (Files.exists(configFilePath)) {
                logger.info("Loading configuration from: {}", configFilePath);
                configuration = objectMapper.readValue(configFilePath.toFile(), Configuration.class);
                logger.info("Configuration loaded successfully");
            } else {
                logger.info("Configuration file not found, using defaults");
                configuration = new Configuration();
                // Set default cache directory
                Path defaultCacheDir = getAppDataDirectory().resolve("cache");
                configuration.setCacheDirectoryPath(defaultCacheDir);
            }
            
            // Ensure cache directory exists
            ensureCacheDirectoryExists();
            
        } catch (IOException e) {
            logger.error("Failed to load configuration from: " + configFilePath, e);
            configuration = new Configuration();
            // Set default cache directory on error
            Path defaultCacheDir = getAppDataDirectory().resolve("cache");
            configuration.setCacheDirectoryPath(defaultCacheDir);
        }
    }
    
    public void saveConfiguration() {
        try {
            // Ensure parent directory exists
            Path parentDir = configFilePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            logger.info("Saving configuration to: {}", configFilePath);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(configFilePath.toFile(), configuration);
            logger.info("Configuration saved successfully");
            
        } catch (IOException e) {
            logger.error("Failed to save configuration to: " + configFilePath, e);
            throw new RuntimeException("Failed to save configuration", e);
        }
    }
    
    public void updateConfiguration(Configuration newConfiguration) {
        if (newConfiguration != null) {
            this.configuration = newConfiguration;
            logger.info("Configuration updated in memory");
        }
    }
    
    public boolean isFirstRun() {
        return !Files.exists(configFilePath) || !configuration.isConfigured();
    }
    
    public void setOsuSkinsDirectory(Path directory) {
        configuration.setOsuSkinsDirectoryPath(directory);
        
        // Set default skin container path
        if (directory != null) {
            Path skinContainerPath = directory.resolve("Skin Container");
            configuration.setSkinContainerPathAsPath(skinContainerPath);
        }
        
        logger.info("Osu skins directory set to: {}", directory);
    }
    
    public Path getConfigDirectory() {
        return configFilePath.getParent();
    }
    
    public Path getCacheDirectory() {
        Path cacheDir = configuration.getCacheDirectoryPath();
        if (cacheDir == null) {
            cacheDir = getAppDataDirectory().resolve("cache");
            configuration.setCacheDirectoryPath(cacheDir);
        }
        return cacheDir;
    }
    
    private void ensureCacheDirectoryExists() {
        try {
            Path cacheDir = getCacheDirectory();
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
                logger.info("Created cache directory: {}", cacheDir);
            }
            
            // Create subdirectories for different cache types
            Path thumbnailsDir = cacheDir.resolve("thumbnails");
            if (!Files.exists(thumbnailsDir)) {
                Files.createDirectories(thumbnailsDir);
                logger.info("Created thumbnails directory: {}", thumbnailsDir);
            }
            
        } catch (IOException e) {
            logger.error("Failed to create cache directory", e);
        }
    }
    
    private Path getConfigFilePath() {
        return getAppDataDirectory().resolve(CONFIG_FILE_NAME);
    }
    
    private Path getAppDataDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        Path appDataDir;
        
        if (os.contains("win")) {
            // Windows: Use APPDATA
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                appDataDir = Paths.get(appData, APP_DATA_FOLDER);
            } else {
                appDataDir = Paths.get(System.getProperty("user.home"), "AppData", "Roaming", APP_DATA_FOLDER);
            }
        } else if (os.contains("mac")) {
            // macOS: Use Application Support
            appDataDir = Paths.get(System.getProperty("user.home"), "Library", "Application Support", APP_DATA_FOLDER);
        } else {
            // Linux and others: Use .config
            String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfigHome != null) {
                appDataDir = Paths.get(xdgConfigHome, APP_DATA_FOLDER);
            } else {
                appDataDir = Paths.get(System.getProperty("user.home"), ".config", APP_DATA_FOLDER);
            }
        }
        
        return appDataDir;
    }
    
    public void exportConfiguration(Path exportPath) throws IOException {
        logger.info("Exporting configuration to: {}", exportPath);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(exportPath.toFile(), configuration);
        logger.info("Configuration exported successfully");
    }
    
    public void importConfiguration(Path importPath) throws IOException {
        logger.info("Importing configuration from: {}", importPath);
        Configuration importedConfig = objectMapper.readValue(importPath.toFile(), Configuration.class);
        this.configuration = importedConfig;
        ensureCacheDirectoryExists();
        logger.info("Configuration imported successfully");
    }
    
    public void resetToDefaults() {
        logger.info("Resetting configuration to defaults");
        Path oldCacheDir = configuration.getCacheDirectoryPath();
        configuration = new Configuration();
        
        // Preserve cache directory if it was set
        if (oldCacheDir != null) {
            configuration.setCacheDirectoryPath(oldCacheDir);
        } else {
            Path defaultCacheDir = getAppDataDirectory().resolve("cache");
            configuration.setCacheDirectoryPath(defaultCacheDir);
        }
        
        logger.info("Configuration reset to defaults");
    }
    
    public String getConfigurationSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Configuration Summary:\n");
        summary.append("- Config file: ").append(configFilePath).append("\n");
        summary.append("- Configured: ").append(configuration.isConfigured()).append("\n");
        summary.append("- Osu skins directory: ").append(configuration.getOsuSkinsDirectory()).append("\n");
        summary.append("- Skin container: ").append(configuration.getSkinContainerPath()).append("\n");
        summary.append("- Cache directory: ").append(configuration.getCacheDirectory()).append("\n");
        summary.append("- Auto scan on startup: ").append(configuration.isAutoScanOnStartup()).append("\n");
        summary.append("- Audio preview enabled: ").append(configuration.isEnableAudioPreview()).append("\n");
        return summary.toString();
    }
}