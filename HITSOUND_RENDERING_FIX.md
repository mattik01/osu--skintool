# Hitsound Rendering Fix Plan

## Critical Issues to Fix

### 1. Implement Proper Audio Mixing
**Problem**: Java only schedules sounds, doesn't mix them
**Solution**: 
- Use a library like TarsosDSP or integrate FFmpeg via ProcessBuilder
- Pre-render the entire arrangement into a single audio file
- Alternative: Use javax.sound.sampled for lower-level audio mixing

### 2. Handle Sliderslide Duration
**Problem**: Duration field is ignored for continuous sounds
**Solution**:
```java
if (sound.getDuration() != null && "sliderslide".equals(sound.getSound())) {
    // Loop the sliderslide for the specified duration
    MediaPlayer slidePlayer = new MediaPlayer(media);
    slidePlayer.setCycleCount(MediaPlayer.INDEFINITE);
    slidePlayer.setStopTime(Duration.millis(sound.getDuration()));
    // Schedule to stop after duration
}
```

### 3. Support OGG Format
**Problem**: JavaFX doesn't support OGG
**Solution**:
- Convert OGG to WAV on-the-fly using FFmpeg
- Cache converted files
- Or use a library like JOrbis for OGG decoding

### 4. Implement Proper Volume Pipeline
**Problem**: Missing mix weights and final boost
**Solution**:
```java
private static final double MIX_WEIGHT_PER_SOUND = 1.5;
private static final double FINAL_BOOST_VOLUME = 3.0;

// When mixing:
double mixedVolume = baseVolume * INDIVIDUAL_SOUND_MULTIPLIER * MIX_WEIGHT_PER_SOUND;
// After mixing all sounds:
finalVolume = mixedVolume * FINAL_BOOST_VOLUME;
```

## Recommended Approach: FFmpeg Integration

Since the Python implementation achieves perfect results with FFmpeg, the most reliable solution is to:

1. **Keep the Python script** and call it from Java:
```java
ProcessBuilder pb = new ProcessBuilder(
    "python", 
    "beatmap-hitsound-extractor/render_arrangement.py",
    arrangementPath,
    skinPath,
    outputPath
);
Process p = pb.start();
```

2. **Or replicate the FFmpeg commands in Java**:
```java
public class FFmpegMixer {
    public void mixHitsounds(List<ScheduledSound> sounds, String outputPath) {
        // Build FFmpeg command with exact same parameters as Python
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        
        // Add inputs for each sound with delay
        for (ScheduledSound sound : sounds) {
            // Create delayed version with silence padding
            // Mix with proper weights
        }
        
        // Execute FFmpeg
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.start().waitFor();
    }
}
```

## Quick Fixes (Partial Improvement)

If full FFmpeg integration isn't feasible immediately:

1. **Fix sliderslide duration handling**
2. **Add OGG to WAV conversion**
3. **Adjust volume multipliers to match Python**
4. **Pre-load all sounds to reduce scheduling delays**
5. **Use AudioSystem for better timing precision**

## Testing Approach

1. Render same arrangement with both implementations
2. Compare waveforms using audio analysis tools
3. Focus on arrangements with many sliders (e.g., Big_Black)
4. Verify timing accuracy with millisecond precision