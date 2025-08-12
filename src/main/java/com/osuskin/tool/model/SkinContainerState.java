package com.osuskin.tool.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SkinContainerState {
    
    @JsonProperty("groupSelections")
    private Map<String, String> groupSelections; // Group name -> Source skin name
    
    @JsonProperty("lastModified")
    private long lastModified;
    
    public SkinContainerState() {
        this.groupSelections = new HashMap<>();
        this.lastModified = System.currentTimeMillis();
    }
    
    public Map<String, String> getGroupSelections() {
        return groupSelections;
    }
    
    public void setGroupSelections(Map<String, String> groupSelections) {
        this.groupSelections = groupSelections;
        this.lastModified = System.currentTimeMillis();
    }
    
    public void addGroupSelection(String groupName, String skinName) {
        groupSelections.put(groupName, skinName);
        this.lastModified = System.currentTimeMillis();
    }
    
    public void removeGroupSelection(String groupName) {
        groupSelections.remove(groupName);
        this.lastModified = System.currentTimeMillis();
    }
    
    public String getSourceSkinForGroup(String groupName) {
        return groupSelections.get(groupName);
    }
    
    public boolean hasGroupSelection(String groupName) {
        return groupSelections.containsKey(groupName);
    }
    
    public void clear() {
        groupSelections.clear();
        this.lastModified = System.currentTimeMillis();
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
    
    public boolean isEmpty() {
        return groupSelections.isEmpty();
    }
}