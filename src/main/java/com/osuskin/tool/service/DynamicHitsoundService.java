package com.osuskin.tool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for dynamically regenerating hitsounds with the currently selected skin.
 * This allows the audio mixer to use hitsounds that match the selected skin
 * instead of pre-rendered hitsounds from a fixed skin.
 */
public class DynamicHitsoundService {
    private static final Logger logger = LoggerFactory.getLogger(DynamicHitsoundService.class);
    
    private final Path pythonScript;
    private final Path samplesDirectory;
    private final Path cacheDirectory;
    private final ConcurrentHashMap<String, Path> hitsoundCache = new ConcurrentHashMap<>();
    
    public DynamicHitsoundService() {
        String currentDir = System.getProperty("user.dir");
        this.pythonScript = Paths.get(currentDir, "beatmap-hitsound-extractor", "regenerate_single.py");
        this.samplesDirectory = Paths.get(currentDir, "beatmap-hitsound-extractor", "samples");
        this.cacheDirectory = Paths.get(currentDir, "beatmap-hitsound-extractor", "dynamic-cache");
        
        // Create cache directory if it doesn't exist
        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException e) {
            logger.error("Failed to create cache directory", e);
        }
    }
    
    /**
     * Get a cache key for a specific skin and sample combination.
     */
    private String getCacheKey(String skinPath, String sampleName) {
        // Use skin folder name and sample name as cache key
        Path skinDir = Paths.get(skinPath);
        String skinName = skinDir.getFileName().toString();
        return skinName + "_" + sampleName;
    }
    
    /**
     * Regenerate hitsounds for a specific sample using the given skin.
     * Returns the path to the regenerated hitsounds file.
     */
    public CompletableFuture<Path> regenerateHitsounds(String skinPath, String sampleName) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = getCacheKey(skinPath, sampleName);
            
            // Check cache first
            Path cachedPath = hitsoundCache.get(cacheKey);
            if (cachedPath != null && Files.exists(cachedPath)) {
                logger.debug("Using cached hitsounds for {} with skin {}", sampleName, skinPath);
                return cachedPath;
            }
            
            // Generate new hitsounds
            Path sampleDir = samplesDirectory.resolve(sampleName);
            Path arrangementFile = sampleDir.resolve("arrangement.json");
            
            if (!Files.exists(arrangementFile)) {
                logger.error("Arrangement file not found for sample: {}", sampleName);
                // Fall back to original pre-rendered hitsounds
                return sampleDir.resolve("hitsounds.mp3");
            }
            
            Path outputFile = cacheDirectory.resolve(cacheKey + "_hitsounds.mp3");
            
            try {
                logger.info("Regenerating hitsounds for {} with skin {}", sampleName, skinPath);
                
                // Create Python script if it doesn't exist
                if (!Files.exists(pythonScript)) {
                    createRegenerateScript();
                }
                
                // Run Python script to regenerate hitsounds
                ProcessBuilder pb = new ProcessBuilder(
                    "python3", pythonScript.toString(),
                    arrangementFile.toString(),
                    skinPath,
                    outputFile.toString()
                );
                
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                // Read output for debugging
                String output = new String(process.getInputStream().readAllBytes());
                
                int exitCode = process.waitFor();
                
                if (exitCode == 0 && Files.exists(outputFile)) {
                    logger.info("Successfully regenerated hitsounds for {}", sampleName);
                    hitsoundCache.put(cacheKey, outputFile);
                    return outputFile;
                } else {
                    logger.error("Failed to regenerate hitsounds. Exit code: {}, Output: {}", exitCode, output);
                    // Fall back to original
                    return sampleDir.resolve("hitsounds.mp3");
                }
                
            } catch (Exception e) {
                logger.error("Error regenerating hitsounds for {}", sampleName, e);
                // Fall back to original
                return sampleDir.resolve("hitsounds.mp3");
            }
        });
    }
    
    /**
     * Clear the hitsound cache.
     */
    public void clearCache() {
        hitsoundCache.clear();
        // Optionally delete cached files
        try {
            if (Files.exists(cacheDirectory)) {
                Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("_hitsounds.mp3"))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            logger.warn("Failed to delete cached file: {}", p);
                        }
                    });
            }
        } catch (IOException e) {
            logger.error("Error clearing cache", e);
        }
    }
    
    /**
     * Create the Python script for regenerating single hitsound files.
     */
    private void createRegenerateScript() throws IOException {
        String script = """
#!/usr/bin/env python3
'''
Regenerate hitsounds for a single arrangement with a specific skin.
Usage: python regenerate_single.py <arrangement.json> <skin_folder> <output_file>
'''

import sys
import json
import subprocess
import tempfile
from pathlib import Path

def load_skin_hitsounds(skin_folder):
    '''Load all hitsound files from a skin folder'''
    skin_path = Path(skin_folder)
    hitsounds = {}
    
    # All possible hitsound combinations
    samplesets = ['normal', 'soft', 'drum']
    additions = ['hitnormal', 'hitclap', 'hitwhistle', 'hitfinish', 'slidertick', 'sliderslide']
    
    for sampleset in samplesets:
        for addition in additions:
            # Try different extensions
            for ext in ['.wav', '.ogg', '.mp3']:
                filename = f"{sampleset}-{addition}{ext}"
                filepath = skin_path / filename
                if filepath.exists():
                    hitsounds[f"{sampleset}-{addition}"] = str(filepath)
                    break
    
    return hitsounds

def create_hitsound_mix(arrangement_data, skin_hitsounds, output_file):
    '''Create hitsound mix using FFmpeg'''
    hit_objects = arrangement_data.get('hit_objects', [])
    if not hit_objects:
        return False
    
    # Build FFmpeg command
    inputs = []
    filters = []
    
    for obj in hit_objects:
        time_ms = obj['time']
        sampleset = obj.get('sampleset', 'normal')
        
        # Add hitnormal
        hitnormal_key = f"{sampleset}-hitnormal"
        if hitnormal_key in skin_hitsounds:
            inputs.append(f"-i '{skin_hitsounds[hitnormal_key]}'")
            delay = int(time_ms)
            filters.append(f"[{len(inputs)-1}]adelay={delay}|{delay},volume=2.0[s{len(filters)}]")
        
        # Add additions based on hitsound flags
        if obj.get('has_whistle'):
            whistle_key = f"{sampleset}-hitwhistle"
            if whistle_key in skin_hitsounds:
                inputs.append(f"-i '{skin_hitsounds[whistle_key]}'")
                delay = int(time_ms)
                filters.append(f"[{len(inputs)-1}]adelay={delay}|{delay},volume=2.0[s{len(filters)}]")
        
        if obj.get('has_clap'):
            clap_key = f"{sampleset}-hitclap"
            if clap_key in skin_hitsounds:
                inputs.append(f"-i '{skin_hitsounds[clap_key]}'")
                delay = int(time_ms)
                filters.append(f"[{len(inputs)-1}]adelay={delay}|{delay},volume=2.0[s{len(filters)}]")
        
        if obj.get('has_finish'):
            finish_key = f"{sampleset}-hitfinish"
            if finish_key in skin_hitsounds:
                inputs.append(f"-i '{skin_hitsounds[finish_key]}'")
                delay = int(time_ms)
                filters.append(f"[{len(inputs)-1}]adelay={delay}|{delay},volume=2.0[s{len(filters)}]")
    
    if not inputs:
        return False
    
    # Create mix filter
    mix_inputs = ''.join(f"[s{i}]" for i in range(len(filters)))
    filters.append(f"{mix_inputs}amix=inputs={len(filters)}:duration=longest,volume=3.0[out]")
    
    # Build and run FFmpeg command
    cmd = f"ffmpeg -y {' '.join(inputs)} -filter_complex \\"{';'.join(filters)}\\" -map '[out]' -ac 2 -ar 44100 -b:a 192k '{output_file}'"
    
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return result.returncode == 0

def main():
    if len(sys.argv) != 4:
        print("Usage: python regenerate_single.py <arrangement.json> <skin_folder> <output_file>")
        sys.exit(1)
    
    arrangement_file = sys.argv[1]
    skin_folder = sys.argv[2]
    output_file = sys.argv[3]
    
    # Load arrangement
    with open(arrangement_file, 'r') as f:
        arrangement_data = json.load(f)
    
    # Load skin hitsounds
    skin_hitsounds = load_skin_hitsounds(skin_folder)
    
    if not skin_hitsounds:
        print(f"No hitsounds found in skin: {skin_folder}")
        sys.exit(1)
    
    # Create hitsound mix
    if create_hitsound_mix(arrangement_data, skin_hitsounds, output_file):
        print(f"Successfully created: {output_file}")
    else:
        print("Failed to create hitsound mix")
        sys.exit(1)

if __name__ == "__main__":
    main()
""";
        
        Files.writeString(pythonScript, script);
        // Make script executable
        pythonScript.toFile().setExecutable(true);
    }
}