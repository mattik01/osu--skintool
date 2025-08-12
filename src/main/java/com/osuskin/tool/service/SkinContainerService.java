package com.osuskin.tool.service;

import com.osuskin.tool.model.ElementGroup;
import com.osuskin.tool.model.Skin;
import com.osuskin.tool.model.SkinContainer;
import com.osuskin.tool.model.SkinContainerState;
import com.osuskin.tool.model.SkinElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SkinContainerService {
    private static final Logger logger = LoggerFactory.getLogger(SkinContainerService.class);
    private final SkinContainer container;
    private SkinContainerState persistentState;
    
    public SkinContainerService() {
        this.container = new SkinContainer();
    }
    
    public void setPersistentState(SkinContainerState state) {
        this.persistentState = state;
    }
    
    public SkinContainerState getPersistentState() {
        return persistentState;
    }
    
    public void selectGroup(ElementGroup group, Skin skin, String skinContainerPath) {
        if (skin == null) {
            logger.warn("Cannot select group {} from null skin", group);
            return;
        }
        
        Set<SkinElement> groupElements = new HashSet<>();
        
        for (SkinElement element : skin.getElements()) {
            String fileName = element.getFileName().toLowerCase();
            
            String baseFileName = fileName;
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot > 0) {
                baseFileName = fileName.substring(0, lastDot);
            }
            
            if (group.containsElement(baseFileName)) {
                groupElements.add(element);
            }
        }
        
        if (!groupElements.isEmpty()) {
            container.addGroupElements(group, skin.getName(), groupElements);
            
            // Copy files to Skin Container folder
            if (skinContainerPath != null) {
                copyElementsToContainer(groupElements, skinContainerPath);
            }
            
            // Update persistent state
            if (persistentState != null) {
                persistentState.addGroupSelection(group.name(), skin.getName());
            }
            
            logger.info("Selected {} elements from group {} in skin {}", 
                groupElements.size(), group.getDisplayName(), skin.getName());
        } else {
            logger.info("No elements found for group {} in skin {}", 
                group.getDisplayName(), skin.getName());
        }
    }
    
    public void selectGroup(ElementGroup group, Skin skin) {
        selectGroup(group, skin, null);
    }
    
    private void copyElementsToContainer(Set<SkinElement> elements, String containerPath) {
        Path containerDir = Paths.get(containerPath);
        
        for (SkinElement element : elements) {
            try {
                Path sourcePath = Paths.get(element.getFilePath());
                Path targetPath = containerDir.resolve(element.getFileName());
                
                Files.copy(sourcePath, targetPath, 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    
                logger.debug("Copied {} to Skin Container", element.getFileName());
            } catch (IOException e) {
                logger.error("Failed to copy element {} to container: {}", 
                    element.getFileName(), e.getMessage());
            }
        }
    }
    
    public void selectAllGroups(Skin skin, String skinContainerPath) {
        if (skin == null) {
            logger.warn("Cannot select all groups from null skin");
            return;
        }
        
        for (ElementGroup group : ElementGroup.values()) {
            selectGroup(group, skin, skinContainerPath);
        }
        
        logger.info("Selected all available groups from skin {}", skin.getName());
    }
    
    public void selectAllGroups(Skin skin) {
        selectAllGroups(skin, null);
    }
    
    public void deselectGroup(ElementGroup group, String skinContainerPath) {
        SkinContainer.ContainerGroupInfo info = container.getGroupInfo(group);
        
        // Remove files from Skin Container folder
        if (skinContainerPath != null && info.hasElements()) {
            removeElementsFromContainer(info.getElements(), skinContainerPath);
        }
        
        container.removeGroup(group);
        
        // Update persistent state
        if (persistentState != null) {
            persistentState.removeGroupSelection(group.name());
        }
        
        logger.info("Deselected group {}", group.getDisplayName());
    }
    
    public void deselectGroup(ElementGroup group) {
        deselectGroup(group, null);
    }
    
    private void removeElementsFromContainer(Set<SkinElement> elements, String containerPath) {
        Path containerDir = Paths.get(containerPath);
        
        for (SkinElement element : elements) {
            try {
                Path targetPath = containerDir.resolve(element.getFileName());
                Files.deleteIfExists(targetPath);
                logger.debug("Removed {} from Skin Container", element.getFileName());
            } catch (IOException e) {
                logger.error("Failed to remove element {} from container: {}", 
                    element.getFileName(), e.getMessage());
            }
        }
    }
    
    public boolean isGroupSelected(ElementGroup group, String skinName) {
        return container.hasGroup(group, skinName);
    }
    
    public SkinContainer.ContainerGroupInfo getGroupInfo(ElementGroup group) {
        return container.getGroupInfo(group);
    }
    
    public void exportSkin(String targetDirectory, String skinName) throws IOException {
        if (container.isEmpty()) {
            throw new IllegalStateException("Cannot export empty skin container");
        }
        
        Path targetPath = Paths.get(targetDirectory, skinName);
        
        if (Files.exists(targetPath)) {
            throw new IOException("Skin directory already exists: " + targetPath);
        }
        
        Files.createDirectories(targetPath);
        logger.info("Creating new skin at: {}", targetPath);
        
        createSkinIni(targetPath, skinName);
        
        int copiedCount = 0;
        for (SkinElement element : container.getAllElements().values()) {
            Path sourcePath = Paths.get(element.getFilePath());
            Path destPath = targetPath.resolve(element.getFileName());
            
            try {
                Files.copy(sourcePath, destPath);
                copiedCount++;
            } catch (IOException e) {
                logger.error("Failed to copy element: {} - {}", element.getFileName(), e.getMessage());
            }
        }
        
        logger.info("Exported {} elements to new skin: {}", copiedCount, skinName);
    }
    
    private void createSkinIni(Path skinDirectory, String skinName) throws IOException {
        Path skinIniPath = skinDirectory.resolve("skin.ini");
        
        StringBuilder iniContent = new StringBuilder();
        iniContent.append("[General]\n");
        iniContent.append("Name: ").append(skinName).append("\n");
        iniContent.append("Author: Mixed Skin\n");
        iniContent.append("Version: 2.5\n\n");
        
        iniContent.append("[Colours]\n");
        iniContent.append("Combo1: 255,192,0\n");
        iniContent.append("Combo2: 0,202,0\n");
        iniContent.append("Combo3: 18,124,255\n");
        iniContent.append("Combo4: 242,24,57\n\n");
        
        iniContent.append("[Fonts]\n");
        iniContent.append("HitCirclePrefix: default\n");
        iniContent.append("HitCircleOverlap: -2\n");
        iniContent.append("ScorePrefix: score\n");
        iniContent.append("ScoreOverlap: 0\n");
        iniContent.append("ComboPrefix: combo\n");
        iniContent.append("ComboOverlap: 0\n");
        
        Files.writeString(skinIniPath, iniContent.toString());
    }
    
    public void clearContainer(String skinContainerPath) {
        // Remove all files from Skin Container folder
        if (skinContainerPath != null) {
            for (ElementGroup group : ElementGroup.values()) {
                SkinContainer.ContainerGroupInfo info = container.getGroupInfo(group);
                if (info.hasElements()) {
                    removeElementsFromContainer(info.getElements(), skinContainerPath);
                }
            }
        }
        
        container.clear();
        
        // Clear persistent state
        if (persistentState != null) {
            persistentState.clear();
        }
        
        logger.info("Cleared skin container");
    }
    
    public void clearContainer() {
        clearContainer(null);
    }
    
    public SkinContainer getContainer() {
        return container;
    }
    
    public int getTotalElementCount() {
        return container.getTotalElementCount();
    }
    
    public void restoreFromPersistentState(SkinContainerState state, String skinsDirectory, String skinContainerPath) {
        if (state == null || state.isEmpty()) {
            logger.info("No persistent state to restore");
            return;
        }
        
        container.clear();
        
        for (Map.Entry<String, String> entry : state.getGroupSelections().entrySet()) {
            try {
                ElementGroup group = ElementGroup.valueOf(entry.getKey());
                String skinName = entry.getValue();
                
                // Find the skin in the skins directory
                Path skinPath = Paths.get(skinsDirectory, skinName);
                if (Files.exists(skinPath)) {
                    // Scan the skin directory to get elements
                    Skin skin = scanSkinDirectory(skinPath, skinName);
                    if (skin != null) {
                        selectGroup(group, skin, skinContainerPath);
                        logger.info("Restored group {} from skin {}", group.getDisplayName(), skinName);
                    }
                } else {
                    logger.warn("Skin {} not found for group {}, skipping restoration", skinName, group.getDisplayName());
                }
            } catch (IllegalArgumentException e) {
                logger.error("Invalid group name in persistent state: {}", entry.getKey());
            }
        }
        
        logger.info("Restored {} groups from persistent state", state.getGroupSelections().size());
    }
    
    private Skin scanSkinDirectory(Path skinPath, String skinName) {
        Skin skin = new Skin(skinName, skinPath);
        
        try {
            Files.walk(skinPath, 1)
                .filter(Files::isRegularFile)
                .forEach(filePath -> {
                    String fileName = filePath.getFileName().toString();
                    SkinElement element = new SkinElement();
                    element.setFileName(fileName);
                    element.setFilePath(filePath.toString());
                    element.setType(SkinElement.ElementType.fromFileName(fileName));
                    element.setExists(true);
                    skin.addElement(element);
                });
            return skin;
        } catch (IOException e) {
            logger.error("Failed to scan skin directory: {}", skinPath, e);
            return null;
        }
    }
}