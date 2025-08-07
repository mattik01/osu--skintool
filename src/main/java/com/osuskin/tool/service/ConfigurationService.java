package com.osuskin.tool.service;

import com.osuskin.tool.model.Configuration;
import com.osuskin.tool.util.ConfigurationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class ConfigurationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);
    
    private final ConfigurationManager configurationManager;
    
    public ConfigurationService(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }
    
    public Configuration getConfiguration() {
        return configurationManager.getConfiguration();
    }
    
    public void updateOsuSkinsDirectory(Path directory) {
        configurationManager.setOsuSkinsDirectory(directory);
        configurationManager.saveConfiguration();
        logger.info("Updated osu skins directory to: {}", directory);
    }
    
    public void updateThumbnailSize(int size) {
        configurationManager.getConfiguration().setThumbnailSize(size);
        configurationManager.saveConfiguration();
        logger.info("Updated thumbnail size to: {}", size);
    }
    
    public void updateAudioPreview(boolean enabled, int duration) {
        Configuration config = configurationManager.getConfiguration();
        config.setEnableAudioPreview(enabled);
        config.setAudioPreviewDuration(duration);
        configurationManager.saveConfiguration();
        logger.info("Updated audio preview settings: enabled={}, duration={}s", enabled, duration);
    }
    
    public void updateWindowSettings(double width, double height, boolean maximized) {
        Configuration config = configurationManager.getConfiguration();
        config.setWindowWidth(width);
        config.setWindowHeight(height);
        config.setWindowMaximized(maximized);
        configurationManager.saveConfiguration();
        logger.debug("Updated window settings: {}x{}, maximized={}", width, height, maximized);
    }
    
    public void updateAutoScanOnStartup(boolean enabled) {
        configurationManager.getConfiguration().setAutoScanOnStartup(enabled);
        configurationManager.saveConfiguration();
        logger.info("Updated auto scan on startup: {}", enabled);
    }
    
    public boolean isFirstRun() {
        return configurationManager.isFirstRun();
    }
    
    public Path getCacheDirectory() {
        return configurationManager.getCacheDirectory();
    }
    
    public String getConfigurationSummary() {
        return configurationManager.getConfigurationSummary();
    }
}