package com.osuskin.tool.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Configuration {
    
    @JsonProperty("osuSkinsDirectory")
    private String osuSkinsDirectory;
    
    @JsonProperty("skinContainerPath")
    private String skinContainerPath;
    
    @JsonProperty("cacheDirectory")
    private String cacheDirectory;
    
    @JsonProperty("lastScanTime")
    private LocalDateTime lastScanTime;
    
    @JsonProperty("thumbnailSize")
    private int thumbnailSize = 150;
    
    @JsonProperty("audioPreviewDuration")
    private int audioPreviewDuration = 30;
    
    @JsonProperty("enableAudioPreview")
    private boolean enableAudioPreview = true;
    
    @JsonProperty("autoScanOnStartup")
    private boolean autoScanOnStartup = true;
    
    @JsonProperty("windowWidth")
    private double windowWidth = 1000;
    
    @JsonProperty("windowHeight")
    private double windowHeight = 700;
    
    @JsonProperty("windowMaximized")
    private boolean windowMaximized = false;
    
    public Configuration() {
        // Default constructor for Jackson
    }
    
    // Getters and Setters
    public String getOsuSkinsDirectory() {
        return osuSkinsDirectory;
    }
    
    public void setOsuSkinsDirectory(String osuSkinsDirectory) {
        this.osuSkinsDirectory = osuSkinsDirectory;
    }
    
    public Path getOsuSkinsDirectoryPath() {
        return osuSkinsDirectory != null ? Paths.get(osuSkinsDirectory) : null;
    }
    
    public void setOsuSkinsDirectoryPath(Path path) {
        this.osuSkinsDirectory = path != null ? path.toString() : null;
    }
    
    public String getSkinContainerPath() {
        return skinContainerPath;
    }
    
    public void setSkinContainerPath(String skinContainerPath) {
        this.skinContainerPath = skinContainerPath;
    }
    
    public Path getSkinContainerPathAsPath() {
        return skinContainerPath != null ? Paths.get(skinContainerPath) : null;
    }
    
    public void setSkinContainerPathAsPath(Path path) {
        this.skinContainerPath = path != null ? path.toString() : null;
    }
    
    public String getCacheDirectory() {
        return cacheDirectory;
    }
    
    public void setCacheDirectory(String cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }
    
    public Path getCacheDirectoryPath() {
        return cacheDirectory != null ? Paths.get(cacheDirectory) : null;
    }
    
    public void setCacheDirectoryPath(Path path) {
        this.cacheDirectory = path != null ? path.toString() : null;
    }
    
    public LocalDateTime getLastScanTime() {
        return lastScanTime;
    }
    
    public void setLastScanTime(LocalDateTime lastScanTime) {
        this.lastScanTime = lastScanTime;
    }
    
    public int getThumbnailSize() {
        return thumbnailSize;
    }
    
    public void setThumbnailSize(int thumbnailSize) {
        this.thumbnailSize = Math.max(50, Math.min(300, thumbnailSize));
    }
    
    public int getAudioPreviewDuration() {
        return audioPreviewDuration;
    }
    
    public void setAudioPreviewDuration(int audioPreviewDuration) {
        this.audioPreviewDuration = Math.max(5, Math.min(60, audioPreviewDuration));
    }
    
    public boolean isEnableAudioPreview() {
        return enableAudioPreview;
    }
    
    public void setEnableAudioPreview(boolean enableAudioPreview) {
        this.enableAudioPreview = enableAudioPreview;
    }
    
    public boolean isAutoScanOnStartup() {
        return autoScanOnStartup;
    }
    
    public void setAutoScanOnStartup(boolean autoScanOnStartup) {
        this.autoScanOnStartup = autoScanOnStartup;
    }
    
    public double getWindowWidth() {
        return windowWidth;
    }
    
    public void setWindowWidth(double windowWidth) {
        this.windowWidth = Math.max(600, windowWidth);
    }
    
    public double getWindowHeight() {
        return windowHeight;
    }
    
    public void setWindowHeight(double windowHeight) {
        this.windowHeight = Math.max(400, windowHeight);
    }
    
    public boolean isWindowMaximized() {
        return windowMaximized;
    }
    
    public void setWindowMaximized(boolean windowMaximized) {
        this.windowMaximized = windowMaximized;
    }
    
    public boolean isConfigured() {
        return osuSkinsDirectory != null && !osuSkinsDirectory.trim().isEmpty();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Configuration that = (Configuration) o;
        return thumbnailSize == that.thumbnailSize &&
               audioPreviewDuration == that.audioPreviewDuration &&
               enableAudioPreview == that.enableAudioPreview &&
               autoScanOnStartup == that.autoScanOnStartup &&
               Double.compare(that.windowWidth, windowWidth) == 0 &&
               Double.compare(that.windowHeight, windowHeight) == 0 &&
               windowMaximized == that.windowMaximized &&
               Objects.equals(osuSkinsDirectory, that.osuSkinsDirectory) &&
               Objects.equals(skinContainerPath, that.skinContainerPath) &&
               Objects.equals(cacheDirectory, that.cacheDirectory) &&
               Objects.equals(lastScanTime, that.lastScanTime);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(osuSkinsDirectory, skinContainerPath, cacheDirectory, 
                          lastScanTime, thumbnailSize, audioPreviewDuration, 
                          enableAudioPreview, autoScanOnStartup, windowWidth, 
                          windowHeight, windowMaximized);
    }
    
    @Override
    public String toString() {
        return "Configuration{" +
               "osuSkinsDirectory='" + osuSkinsDirectory + '\'' +
               ", skinContainerPath='" + skinContainerPath + '\'' +
               ", configured=" + isConfigured() +
               '}';
    }
}