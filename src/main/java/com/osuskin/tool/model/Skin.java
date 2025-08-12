package com.osuskin.tool.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Skin {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("directoryPath")
    private String directoryPath;
    
    @JsonProperty("author")
    private String author;
    
    @JsonProperty("version")
    private String version;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("elements")
    private List<SkinElement> elements;
    
    @JsonProperty("thumbnailPath")
    private String thumbnailPath;
    
    @JsonProperty("previewImagePath")
    private String previewImagePath;
    
    @JsonProperty("lastModified")
    private LocalDateTime lastModified;
    
    @JsonProperty("fileCount")
    private int fileCount;
    
    @JsonProperty("totalSize")
    private long totalSize;
    
    @JsonProperty("tags")
    private Set<String> tags;
    
    @JsonProperty("comboColors")
    private List<int[]> comboColors;  // RGB values for each combo color
    
    @JsonProperty("isSpecial")
    private boolean isSpecial;  // Mark special skins like Skin Container
    
    public Skin() {
        this.elements = new ArrayList<>();
        this.tags = new HashSet<>();
        this.comboColors = new ArrayList<>();
        this.isSpecial = false;
    }
    
    public Skin(String name, Path directoryPath) {
        this();
        this.name = name;
        this.directoryPath = directoryPath.toString();
        this.lastModified = LocalDateTime.now();
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDirectoryPath() {
        return directoryPath;
    }
    
    public void setDirectoryPath(String directoryPath) {
        this.directoryPath = directoryPath;
    }
    
    public Path getDirectoryPathAsPath() {
        return directoryPath != null ? Paths.get(directoryPath) : null;
    }
    
    public void setDirectoryPathAsPath(Path path) {
        this.directoryPath = path != null ? path.toString() : null;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<SkinElement> getElements() {
        return elements;
    }
    
    public void setElements(List<SkinElement> elements) {
        this.elements = elements != null ? elements : new ArrayList<>();
    }
    
    public void addElement(SkinElement element) {
        if (element != null) {
            this.elements.add(element);
        }
    }
    
    public SkinElement getElement(SkinElement.ElementType type) {
        return elements.stream()
                .filter(e -> e.getType() == type)
                .findFirst()
                .orElse(null);
    }
    
    public boolean hasElement(SkinElement.ElementType type) {
        return elements.stream()
                .anyMatch(e -> e.getType() == type && e.isExists());
    }
    
    public Collection<SkinElement> getImageElements() {
        return elements.stream()
                .filter(SkinElement::isImageFile)
                .toList();
    }
    
    public Collection<SkinElement> getAudioElements() {
        return elements.stream()
                .filter(SkinElement::isAudioFile)
                .toList();
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
    
    public String getPreviewImagePath() {
        return previewImagePath;
    }
    
    public void setPreviewImagePath(String previewImagePath) {
        this.previewImagePath = previewImagePath;
    }
    
    public Path getPreviewImagePathAsPath() {
        return previewImagePath != null ? Paths.get(previewImagePath) : null;
    }
    
    public void setPreviewImagePathAsPath(Path path) {
        this.previewImagePath = path != null ? path.toString() : null;
    }
    
    public LocalDateTime getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }
    
    public int getFileCount() {
        return fileCount;
    }
    
    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }
    
    public long getTotalSize() {
        return totalSize;
    }
    
    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }
    
    public String getFormattedTotalSize() {
        if (totalSize < 1024) {
            return totalSize + " B";
        } else if (totalSize < 1024 * 1024) {
            return String.format("%.1f KB", totalSize / 1024.0);
        } else {
            return String.format("%.1f MB", totalSize / (1024.0 * 1024.0));
        }
    }
    
    public Set<String> getTags() {
        return tags;
    }
    
    public void setTags(Set<String> tags) {
        this.tags = tags != null ? tags : new HashSet<>();
    }
    
    public void addTag(String tag) {
        if (tag != null && !tag.trim().isEmpty()) {
            this.tags.add(tag.trim().toLowerCase());
        }
    }
    
    public void removeTag(String tag) {
        if (tag != null) {
            this.tags.remove(tag.trim().toLowerCase());
        }
    }
    
    public boolean hasTag(String tag) {
        return tag != null && this.tags.contains(tag.trim().toLowerCase());
    }
    
    public String getDisplayInfo() {
        StringBuilder info = new StringBuilder(name);
        if (author != null && !author.trim().isEmpty()) {
            info.append(" by ").append(author);
        }
        if (version != null && !version.trim().isEmpty()) {
            info.append(" (v").append(version).append(")");
        }
        return info.toString();
    }
    
    public int getElementCount() {
        return (int) elements.stream()
                .filter(SkinElement::isExists)
                .count();
    }
    
    public int getImageElementCount() {
        return (int) elements.stream()
                .filter(element -> element.isExists() && element.isImageFile())
                .count();
    }
    
    public int getAudioElementCount() {
        return (int) elements.stream()
                .filter(element -> element.isExists() && element.isAudioFile())
                .count();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Skin skin = (Skin) o;
        return Objects.equals(name, skin.name) &&
               Objects.equals(directoryPath, skin.directoryPath);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, directoryPath);
    }
    
    public List<int[]> getComboColors() {
        return comboColors;
    }
    
    public void setComboColors(List<int[]> comboColors) {
        this.comboColors = comboColors;
    }
    
    public void addComboColor(int r, int g, int b) {
        if (comboColors == null) {
            comboColors = new ArrayList<>();
        }
        comboColors.add(new int[]{r, g, b});
    }
    
    public boolean isSpecial() {
        return isSpecial;
    }
    
    public void setSpecial(boolean special) {
        isSpecial = special;
    }
    
    @Override
    public String toString() {
        return "Skin{" +
               "name='" + name + '\'' +
               ", author='" + author + '\'' +
               ", elementCount=" + getElementCount() +
               ", totalSize=" + getFormattedTotalSize() +
               '}';
    }
}