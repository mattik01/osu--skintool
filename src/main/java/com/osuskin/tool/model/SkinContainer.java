package com.osuskin.tool.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SkinContainer {
    private final Map<ElementGroup, ContainerGroupInfo> groupSelections;
    private final Map<String, SkinElement> allElements;
    
    public SkinContainer() {
        this.groupSelections = new HashMap<>();
        this.allElements = new HashMap<>();
        
        for (ElementGroup group : ElementGroup.values()) {
            groupSelections.put(group, new ContainerGroupInfo());
        }
    }
    
    public static class ContainerGroupInfo {
        private String sourceSkinName;
        private final Set<SkinElement> elements;
        
        public ContainerGroupInfo() {
            this.elements = new HashSet<>();
        }
        
        public String getSourceSkinName() {
            return sourceSkinName;
        }
        
        public void setSourceSkinName(String sourceSkinName) {
            this.sourceSkinName = sourceSkinName;
        }
        
        public Set<SkinElement> getElements() {
            return new HashSet<>(elements);
        }
        
        public void setElements(Set<SkinElement> newElements) {
            elements.clear();
            elements.addAll(newElements);
        }
        
        public void clear() {
            sourceSkinName = null;
            elements.clear();
        }
        
        public boolean hasElements() {
            return !elements.isEmpty();
        }
    }
    
    public void addGroupElements(ElementGroup group, String skinName, Set<SkinElement> elements) {
        ContainerGroupInfo info = groupSelections.get(group);
        info.setSourceSkinName(skinName);
        info.setElements(elements);
        
        for (SkinElement element : elements) {
            allElements.put(element.getFileName(), element);
        }
    }
    
    public void removeGroup(ElementGroup group) {
        ContainerGroupInfo info = groupSelections.get(group);
        
        for (SkinElement element : info.getElements()) {
            allElements.remove(element.getFileName());
        }
        
        info.clear();
    }
    
    public boolean hasGroup(ElementGroup group, String skinName) {
        ContainerGroupInfo info = groupSelections.get(group);
        return info.hasElements() && skinName.equals(info.getSourceSkinName());
    }
    
    public ContainerGroupInfo getGroupInfo(ElementGroup group) {
        return groupSelections.get(group);
    }
    
    public Map<String, SkinElement> getAllElements() {
        return new HashMap<>(allElements);
    }
    
    public void clear() {
        for (ContainerGroupInfo info : groupSelections.values()) {
            info.clear();
        }
        allElements.clear();
    }
    
    public boolean isEmpty() {
        return allElements.isEmpty();
    }
    
    public int getTotalElementCount() {
        return allElements.size();
    }
}