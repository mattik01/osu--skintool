package com.osuskin.tool.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Arrangement {
    @JsonProperty("beatmap_folder")
    private String beatmapFolder;
    
    @JsonProperty("difficulty_name")
    private String difficultyName;
    
    @JsonProperty("start_time")
    private int startTime;
    
    @JsonProperty("end_time")
    private int endTime;
    
    @JsonProperty("hit_objects")
    private List<HitObject> hitObjects;
    
    @JsonProperty("unique_sounds")
    private List<String> uniqueSounds;
    
    @JsonProperty("samplesets_used")
    private List<String> samplesetsUsed;
    
    @JsonProperty("variety_score")
    private int varietyScore;
    
    @JsonProperty("audio_file")
    private String audioFile;
    
    public static class HitObject {
        private int time;
        private List<Sound> sounds;
        private String sampleset;
        private String additions;
        
        @JsonProperty("sample_index")
        private int sampleIndex;
        
        private int volume;
        
        public static class Sound {
            private String sound;
            private String sampleset;
            
            @JsonProperty("sample_index")
            private int sampleIndex;
            
            private int volume;
            
            @JsonProperty("time_offset")
            private int timeOffset;
            
            // Duration field for slider sounds
            private Integer duration;
            
            // Getters and setters
            public String getSound() { return sound; }
            public void setSound(String sound) { this.sound = sound; }
            
            public String getSampleset() { return sampleset; }
            public void setSampleset(String sampleset) { this.sampleset = sampleset; }
            
            public int getSampleIndex() { return sampleIndex; }
            public void setSampleIndex(int sampleIndex) { this.sampleIndex = sampleIndex; }
            
            public int getVolume() { return volume; }
            public void setVolume(int volume) { this.volume = volume; }
            
            public int getTimeOffset() { return timeOffset; }
            public void setTimeOffset(int timeOffset) { this.timeOffset = timeOffset; }
            
            public Integer getDuration() { return duration; }
            public void setDuration(Integer duration) { this.duration = duration; }
        }
        
        // Getters and setters
        public int getTime() { return time; }
        public void setTime(int time) { this.time = time; }
        
        public List<Sound> getSounds() { return sounds; }
        public void setSounds(List<Sound> sounds) { this.sounds = sounds; }
        
        public String getSampleset() { return sampleset; }
        public void setSampleset(String sampleset) { this.sampleset = sampleset; }
        
        public String getAdditions() { return additions; }
        public void setAdditions(String additions) { this.additions = additions; }
        
        public int getSampleIndex() { return sampleIndex; }
        public void setSampleIndex(int sampleIndex) { this.sampleIndex = sampleIndex; }
        
        public int getVolume() { return volume; }
        public void setVolume(int volume) { this.volume = volume; }
    }
    
    // Getters and setters
    public String getBeatmapFolder() { return beatmapFolder; }
    public void setBeatmapFolder(String beatmapFolder) { this.beatmapFolder = beatmapFolder; }
    
    public String getDifficultyName() { return difficultyName; }
    public void setDifficultyName(String difficultyName) { this.difficultyName = difficultyName; }
    
    public int getStartTime() { return startTime; }
    public void setStartTime(int startTime) { this.startTime = startTime; }
    
    public int getEndTime() { return endTime; }
    public void setEndTime(int endTime) { this.endTime = endTime; }
    
    public List<HitObject> getHitObjects() { return hitObjects; }
    public void setHitObjects(List<HitObject> hitObjects) { this.hitObjects = hitObjects; }
    
    public List<String> getUniqueSounds() { return uniqueSounds; }
    public void setUniqueSounds(List<String> uniqueSounds) { this.uniqueSounds = uniqueSounds; }
    
    public List<String> getSamplesetsUsed() { return samplesetsUsed; }
    public void setSamplesetsUsed(List<String> samplesetsUsed) { this.samplesetsUsed = samplesetsUsed; }
    
    public int getVarietyScore() { return varietyScore; }
    public void setVarietyScore(int varietyScore) { this.varietyScore = varietyScore; }
    
    public String getAudioFile() { return audioFile; }
    public void setAudioFile(String audioFile) { this.audioFile = audioFile; }
}