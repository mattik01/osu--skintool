package com.osuskin.tool.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SkinElement {
    
    @JsonProperty("fileName")
    private String fileName;
    
    public enum ElementType {
        CURSOR("cursor.png", "Cursor"),
        MENU_BACK("menu-back.png", "Menu Background"),
        FOLLOW_POINT("followpoint.png", "Follow Point"),
        HIT_CIRCLE("hitcircle.png", "Hit Circle"),
        HIT_CIRCLE_OVERLAY("hitcircleoverlay.png", "Hit Circle Overlay"),
        APPROACH_CIRCLE("approachcircle.png", "Approach Circle"),
        SLIDER_BALL("sliderb0.png", "Slider Ball"),
        SPINNER_CIRCLE("spinner-circle.png", "Spinner Circle"),
        COMBO_BURST("comboburst-0.png", "Combo Burst"),
        
        // Hit sounds
        NORMAL_HITNORMAL("normal-hitnormal.wav", "Normal Hit"),
        NORMAL_HITWHISTLE("normal-hitwhistle.wav", "Normal Whistle"),
        NORMAL_HITFINISH("normal-hitfinish.wav", "Normal Finish"),
        NORMAL_HITCLAP("normal-hitclap.wav", "Normal Clap"),
        SOFT_HITNORMAL("soft-hitnormal.wav", "Soft Hit"),
        SOFT_HITWHISTLE("soft-hitwhistle.wav", "Soft Whistle"),
        SOFT_HITFINISH("soft-hitfinish.wav", "Soft Finish"),
        SOFT_HITCLAP("soft-hitclap.wav", "Soft Clap"),
        DRUM_HITNORMAL("drum-hitnormal.wav", "Drum Hit"),
        DRUM_HITWHISTLE("drum-hitwhistle.wav", "Drum Whistle"),
        DRUM_HITFINISH("drum-hitfinish.wav", "Drum Finish"),
        DRUM_HITCLAP("drum-hitclap.wav", "Drum Clap"),
        
        // UI sounds
        MENU_HIT("menu-hit.wav", "Menu Hit"),
        MENU_BACK_SOUND("menu-back.wav", "Menu Back"),
        CLICK_SHORT("click-short.wav", "Click Short"),
        CLICK_CLOSE("click-close.wav", "Click Close");
        
        private final String fileName;
        private final String displayName;
        
        ElementType(String fileName, String displayName) {
            this.fileName = fileName;
            this.displayName = displayName;
        }
        
        public String getFileName() {
            return fileName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public boolean isAudioFile() {
            return fileName.toLowerCase().endsWith(".wav") || 
                   fileName.toLowerCase().endsWith(".mp3") || 
                   fileName.toLowerCase().endsWith(".ogg");
        }
        
        public boolean isImageFile() {
            return fileName.toLowerCase().endsWith(".png") || 
                   fileName.toLowerCase().endsWith(".jpg") || 
                   fileName.toLowerCase().endsWith(".jpeg");
        }
        
        public static ElementType fromFileName(String fileName) {
            for (ElementType type : values()) {
                if (type.fileName.equalsIgnoreCase(fileName)) {
                    return type;
                }
            }
            return null;
        }
    }
    
    @JsonProperty("type")
    private ElementType type;
    
    @JsonProperty("filePath")
    private String filePath;
    
    @JsonProperty("actualFileName")
    private String actualFileName;  // Store the actual filename from disk
    
    @JsonProperty("thumbnailPath")
    private String thumbnailPath;
    
    @JsonProperty("fileSize")
    private long fileSize;
    
    @JsonProperty("exists")
    private boolean exists;
    
    public SkinElement() {
        // Default constructor for Jackson
    }
    
    public SkinElement(ElementType type, Path filePath) {
        this.type = type;
        this.filePath = filePath.toString();
        this.actualFileName = filePath.getFileName().toString();
        this.exists = true;
    }
    
    public ElementType getType() {
        return type;
    }
    
    public void setType(ElementType type) {
        this.type = type;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public Path getFilePathAsPath() {
        return filePath != null ? Paths.get(filePath) : null;
    }
    
    public void setFilePathAsPath(Path path) {
        this.filePath = path != null ? path.toString() : null;
    }
    
    public String getThumbnailPath() {
        return thumbnailPath;
    }
    
    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }
    
    public Path getThumbnailPathAsPath() {
        return thumbnailPath != null ? Paths.get(thumbnailPath) : null;
    }
    
    public void setThumbnailPathAsPath(Path path) {
        this.thumbnailPath = path != null ? path.toString() : null;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    
    public boolean isExists() {
        return exists;
    }
    
    public void setExists(boolean exists) {
        this.exists = exists;
    }
    
    public String getDisplayName() {
        return type != null ? type.getDisplayName() : "Unknown";
    }
    
    public String getFileName() {
        // Return actual filename if available, otherwise use type's filename
        if (actualFileName != null) {
            return actualFileName;
        }
        return type != null ? type.getFileName() : "unknown";
    }
    
    public void setFileName(String fileName) {
        this.actualFileName = fileName;
    }
    
    public boolean isAudioFile() {
        return type != null && type.isAudioFile();
    }
    
    public boolean isImageFile() {
        return type != null && type.isImageFile();
    }
    
    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkinElement that = (SkinElement) o;
        return fileSize == that.fileSize &&
               exists == that.exists &&
               type == that.type &&
               Objects.equals(filePath, that.filePath) &&
               Objects.equals(thumbnailPath, that.thumbnailPath);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, filePath, thumbnailPath, fileSize, exists);
    }
    
    @Override
    public String toString() {
        return "SkinElement{" +
               "type=" + type +
               ", filePath='" + filePath + '\'' +
               ", exists=" + exists +
               ", fileSize=" + getFormattedFileSize() +
               '}';
    }
}