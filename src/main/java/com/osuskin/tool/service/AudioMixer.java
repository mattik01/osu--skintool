package com.osuskin.tool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-time audio mixer using javax.sound.sampled for accurate hitsound rendering.
 * This provides proper audio mixing similar to the Python FFmpeg implementation.
 */
public class AudioMixer {
    private static final Logger logger = LoggerFactory.getLogger(AudioMixer.class);
    
    // Audio format: 44100 Hz, 16-bit, stereo (matching Python's FFmpeg)
    private static final AudioFormat MIXER_FORMAT = new AudioFormat(
        44100.0f,  // Sample rate
        16,        // Sample size in bits
        2,         // Channels (stereo)
        true,      // Signed
        false      // Little-endian
    );
    
    // Volume multipliers - balanced for audible but controlled layering
    private static final float INDIVIDUAL_SOUND_MULTIPLIER = 1.2f;  // Moderate reduction from 2.0f
    private static final float MIX_WEIGHT_PER_SOUND = 1.0f;  // Slight reduction from 1.5f
    private static final float FINAL_MIX_MULTIPLIER = 1.8f;  // Moderate reduction from 3.0f
    
    // Cache for loaded and converted audio samples
    private final Map<String, AudioSample> sampleCache = new ConcurrentHashMap<>();
    private final Map<String, AudioSample> defaultHitsoundCache = new ConcurrentHashMap<>();
    
    /**
     * Represents a loaded audio sample in our standard format.
     */
    private static class AudioSample {
        final byte[] data;
        final int sampleCount;
        final AudioFormat format;
        
        AudioSample(byte[] data, int sampleCount, AudioFormat format) {
            this.data = data;
            this.sampleCount = sampleCount;
            this.format = format;
        }
    }
    
    /**
     * Scheduled sound with timing and volume.
     */
    public static class ScheduledSound {
        public final String audioFile;
        public final int startTimeMs;
        public final int durationMs;  // For looped sounds like sliderslide
        public final float volume;     // 0.0 to 1.0
        public final boolean loop;     // Whether to loop for duration
        
        public ScheduledSound(String audioFile, int startTimeMs, float volume) {
            this(audioFile, startTimeMs, 0, volume, false);
        }
        
        public ScheduledSound(String audioFile, int startTimeMs, int durationMs, float volume, boolean loop) {
            this.audioFile = audioFile;
            this.startTimeMs = startTimeMs;
            this.durationMs = durationMs;
            this.volume = volume;
            this.loop = loop;
        }
    }
    
    /**
     * Mix multiple sounds into a single audio stream.
     * 
     * @param sounds List of scheduled sounds to mix
     * @param durationMs Total duration of the output in milliseconds
     * @return Mixed audio data in MIXER_FORMAT
     */
    public byte[] mixSounds(List<ScheduledSound> sounds, int durationMs) {
        if (sounds == null || sounds.isEmpty()) {
            return createSilence(durationMs);
        }
        
        logger.info("Mixing {} sounds into {}ms clip", sounds.size(), durationMs);
        
        // Calculate buffer size for output
        int samplesPerMs = (int)(MIXER_FORMAT.getSampleRate() / 1000);
        int totalSamples = samplesPerMs * durationMs;
        int bytesPerSample = MIXER_FORMAT.getSampleSizeInBits() / 8 * MIXER_FORMAT.getChannels();
        int bufferSize = totalSamples * bytesPerSample;
        
        // Create accumulation buffer for mixing (using floats for precision)
        float[] mixBuffer = new float[totalSamples * MIXER_FORMAT.getChannels()];
        Arrays.fill(mixBuffer, 0.0f);
        
        // Track how many sounds contribute to each sample for normalization
        int activeSounds = 0;
        
        // Mix each sound into the buffer
        for (ScheduledSound sound : sounds) {
            try {
                AudioSample sample = loadAudioSample(sound.audioFile);
                if (sample == null) continue;
                
                activeSounds++;
                
                // Calculate position in output buffer
                int startSample = samplesPerMs * sound.startTimeMs;
                int startIndex = startSample * MIXER_FORMAT.getChannels();
                
                // Apply volume with reduced multipliers for consistent layering
                float effectiveVolume = sound.volume * INDIVIDUAL_SOUND_MULTIPLIER;
                
                if (sound.loop && sound.durationMs > 0) {
                    // Handle looped sounds (like sliderslide)
                    mixLoopedSample(mixBuffer, sample, startIndex, sound.durationMs * samplesPerMs, effectiveVolume);
                } else {
                    // Handle one-shot sounds
                    mixSample(mixBuffer, sample, startIndex, effectiveVolume);
                }
                
            } catch (Exception e) {
                logger.error("Failed to mix sound: {} - Error: {}", sound.audioFile, e.getMessage());
                if (logger.isDebugEnabled()) {
                    logger.debug("Full stack trace:", e);
                }
            }
        }
        
        // Apply mix weight and final boost
        float mixWeight = MIX_WEIGHT_PER_SOUND;
        float finalBoost = FINAL_MIX_MULTIPLIER;
        
        // Convert float buffer to byte array with normalization and limiting
        return convertToBytes(mixBuffer, mixWeight, finalBoost);
    }
    
    /**
     * Load and convert audio file to standard format with fallback to defaults.
     */
    private AudioSample loadAudioSample(String audioFile) throws Exception {
        // Check cache first
        if (sampleCache.containsKey(audioFile)) {
            return sampleCache.get(audioFile);
        }
        
        File file = new File(audioFile);
        if (!file.exists()) {
            // Try to use default hitsound instead
            String fileName = file.getName().toLowerCase();
            AudioSample defaultSample = loadDefaultHitsound(fileName);
            if (defaultSample != null) {
                logger.debug("Using default hitsound for missing file: {}", audioFile);
                return defaultSample;
            }
            logger.warn("Audio file not found and no default available: {}", audioFile);
            return null;
        }
        
        // Try different loading strategies
        AudioSample sample = null;
        
        // First try standard loading with format conversion
        try {
            sample = loadWithStandardConversion(file);
        } catch (Exception e) {
            logger.debug("Standard conversion failed for {}: {}", audioFile, e.getMessage());
        }
        
        // If standard loading failed, try manual PCM conversion
        if (sample == null) {
            try {
                sample = loadWithManualPCMConversion(file);
            } catch (Exception e) {
                logger.debug("Manual PCM conversion failed for {}: {}", audioFile, e.getMessage());
            }
        }
        
        // If still failed and it's an OGG file, try OGG decoder
        if (sample == null && audioFile.toLowerCase().endsWith(".ogg")) {
            try {
                File convertedWav = OggDecoder.convertOggToWav(audioFile);
                if (convertedWav != null && convertedWav.exists()) {
                    sample = loadAudioSample(convertedWav.getAbsolutePath());
                }
            } catch (IOException e) {
                logger.error("Failed to convert OGG file: {}", audioFile, e);
            }
        }
        
        // If all loading attempts failed, use default or silence
        if (sample == null) {
            String fileName = file.getName().toLowerCase();
            AudioSample defaultSample = loadDefaultHitsound(fileName);
            if (defaultSample != null) {
                logger.debug("Using default hitsound for problematic file: {}", audioFile);
                return defaultSample;
            }
            logger.warn("All loading attempts failed for: {}. Using silence.", audioFile);
            return createSilentSample(100);
        }
        
        sampleCache.put(audioFile, sample);
        return sample;
    }
    
    /**
     * Standard loading with format conversion.
     */
    private AudioSample loadWithStandardConversion(File file) throws Exception {
        try (AudioInputStream audioIn = AudioSystem.getAudioInputStream(file)) {
            // Convert to our standard format if needed
            AudioInputStream convertedIn = audioIn;
            if (!audioIn.getFormat().matches(MIXER_FORMAT)) {
                if (AudioSystem.isConversionSupported(MIXER_FORMAT, audioIn.getFormat())) {
                    convertedIn = AudioSystem.getAudioInputStream(MIXER_FORMAT, audioIn);
                } else {
                    throw new UnsupportedAudioFileException("Format conversion not supported");
                }
            }
            
            // Read all data
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = convertedIn.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            
            byte[] audioData = baos.toByteArray();
            int bytesPerSample = (MIXER_FORMAT.getSampleSizeInBits() / 8) * MIXER_FORMAT.getChannels();
            int sampleCount = audioData.length / bytesPerSample;
            
            return new AudioSample(audioData, sampleCount, MIXER_FORMAT);
        }
    }
    
    /**
     * Manual PCM conversion for problematic WAV files.
     * Handles various PCM formats that JavaFX might not support directly.
     */
    private AudioSample loadWithManualPCMConversion(File file) throws Exception {
        try (AudioInputStream audioIn = AudioSystem.getAudioInputStream(file)) {
            AudioFormat sourceFormat = audioIn.getFormat();
            
            // Check if it's a PCM format we can handle manually
            if (!sourceFormat.getEncoding().toString().contains("PCM")) {
                throw new UnsupportedAudioFileException("Not a PCM format");
            }
            
            // Read raw audio data
            ByteArrayOutputStream rawData = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = audioIn.read(buffer)) != -1) {
                rawData.write(buffer, 0, bytesRead);
            }
            byte[] sourceData = rawData.toByteArray();
            
            // Convert to our target format manually
            byte[] convertedData = convertPCMData(
                sourceData,
                sourceFormat.getSampleSizeInBits(),
                sourceFormat.getChannels(),
                sourceFormat.isBigEndian(),
                sourceFormat.getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED,
                (int) sourceFormat.getSampleRate()
            );
            
            int bytesPerSample = (MIXER_FORMAT.getSampleSizeInBits() / 8) * MIXER_FORMAT.getChannels();
            int sampleCount = convertedData.length / bytesPerSample;
            
            return new AudioSample(convertedData, sampleCount, MIXER_FORMAT);
        }
    }
    
    /**
     * Convert PCM data from various formats to our standard format.
     */
    private byte[] convertPCMData(byte[] sourceData, int sourceBits, int sourceChannels,
                                   boolean sourceBigEndian, boolean sourceUnsigned, int sourceSampleRate) {
        
        // Calculate conversion parameters
        int sourceBytesPerSample = sourceBits / 8;
        int sourceBytesPerFrame = sourceBytesPerSample * sourceChannels;
        int sourceFrames = sourceData.length / sourceBytesPerFrame;
        
        // Resample if necessary
        int targetSampleRate = (int) MIXER_FORMAT.getSampleRate();
        int targetFrames = (int) ((long) sourceFrames * targetSampleRate / sourceSampleRate);
        
        // Create output buffer
        int targetBytesPerFrame = 2 * MIXER_FORMAT.getChannels(); // 16-bit stereo
        ByteBuffer output = ByteBuffer.allocate(targetFrames * targetBytesPerFrame);
        output.order(ByteOrder.LITTLE_ENDIAN);
        
        // Process each frame
        for (int targetFrame = 0; targetFrame < targetFrames; targetFrame++) {
            // Calculate corresponding source frame (simple linear interpolation)
            double sourceFramePos = (double) targetFrame * sourceSampleRate / targetSampleRate;
            int sourceFrame = Math.min((int) sourceFramePos, sourceFrames - 1);
            int sourceOffset = sourceFrame * sourceBytesPerFrame;
            
            // Extract samples from source
            float[] samples = new float[sourceChannels];
            for (int ch = 0; ch < sourceChannels; ch++) {
                int sampleOffset = sourceOffset + ch * sourceBytesPerSample;
                if (sampleOffset + sourceBytesPerSample <= sourceData.length) {
                    samples[ch] = extractSample(sourceData, sampleOffset, sourceBits, 
                                                sourceBigEndian, sourceUnsigned);
                }
            }
            
            // Convert to target format (stereo)
            if (sourceChannels == 1) {
                // Mono to stereo
                short sample = (short) (samples[0] * 32767);
                output.putShort(sample);
                output.putShort(sample);
            } else if (sourceChannels == 2) {
                // Stereo to stereo
                output.putShort((short) (samples[0] * 32767));
                output.putShort((short) (samples[1] * 32767));
            } else {
                // Multi-channel to stereo (take first two channels)
                output.putShort((short) (samples[0] * 32767));
                output.putShort((short) (Math.min(sourceChannels, 2) > 1 ? samples[1] * 32767 : samples[0] * 32767));
            }
        }
        
        return output.array();
    }
    
    /**
     * Extract a single sample from raw PCM data and normalize to [-1, 1].
     */
    private float extractSample(byte[] data, int offset, int bits, boolean bigEndian, boolean unsigned) {
        if (offset + (bits / 8) > data.length) {
            return 0.0f;
        }
        
        long value = 0;
        
        if (bits == 8) {
            value = data[offset] & 0xFF;
            if (!unsigned) {
                value = (byte) value; // Sign extend
            }
        } else if (bits == 16) {
            if (bigEndian) {
                value = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            } else {
                value = ((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF);
            }
            if (!unsigned) {
                value = (short) value; // Sign extend
            }
        } else if (bits == 24) {
            if (bigEndian) {
                value = ((data[offset] & 0xFF) << 16) | ((data[offset + 1] & 0xFF) << 8) | (data[offset + 2] & 0xFF);
            } else {
                value = ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF);
            }
            if (!unsigned) {
                // Sign extend 24-bit to 32-bit
                if ((value & 0x800000) != 0) {
                    value |= 0xFF000000;
                }
            }
        } else if (bits == 32) {
            if (bigEndian) {
                value = ((long)(data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16) |
                        ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            } else {
                value = ((long)(data[offset + 3] & 0xFF) << 24) | ((data[offset + 2] & 0xFF) << 16) |
                        ((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF);
            }
            if (!unsigned) {
                value = (int) value; // Sign extend
            }
        }
        
        // Normalize to [-1, 1]
        float normalized;
        if (unsigned) {
            long maxValue = (1L << bits) - 1;
            normalized = (value / (float) maxValue) * 2.0f - 1.0f;
        } else {
            long maxValue = (1L << (bits - 1)) - 1;
            normalized = value / (float) maxValue;
        }
        
        return Math.max(-1.0f, Math.min(1.0f, normalized));
    }
    
    
    /**
     * Mix a one-shot sample into the buffer.
     */
    private void mixSample(float[] mixBuffer, AudioSample sample, int startIndex, float volume) {
        ByteBuffer bb = ByteBuffer.wrap(sample.data).order(ByteOrder.LITTLE_ENDIAN);
        
        int sampleIndex = 0;
        for (int i = 0; i < sample.data.length && (startIndex + sampleIndex) < mixBuffer.length; i += 2) {
            if (startIndex + sampleIndex >= 0) {
                short sampleValue = bb.getShort(i);
                float normalizedValue = sampleValue / 32768.0f;
                mixBuffer[startIndex + sampleIndex] += normalizedValue * volume;
            }
            sampleIndex++;
        }
    }
    
    /**
     * Mix a looped sample (like sliderslide) for a specific duration.
     */
    private void mixLoopedSample(float[] mixBuffer, AudioSample sample, int startIndex, int durationSamples, float volume) {
        if (sample.sampleCount == 0) return;
        
        ByteBuffer bb = ByteBuffer.wrap(sample.data).order(ByteOrder.LITTLE_ENDIAN);
        int sampleChannels = MIXER_FORMAT.getChannels();
        int samplesInFile = sample.sampleCount;
        
        for (int i = 0; i < durationSamples * sampleChannels; i++) {
            int bufferIndex = startIndex + i;
            if (bufferIndex >= 0 && bufferIndex < mixBuffer.length) {
                int sourceIndex = (i % (samplesInFile * sampleChannels)) * 2;
                if (sourceIndex >= 0 && sourceIndex < sample.data.length - 1) {
                    short sampleValue = bb.getShort(sourceIndex);
                    float normalizedValue = sampleValue / 32768.0f;
                    mixBuffer[bufferIndex] += normalizedValue * volume;
                }
            }
        }
    }
    
    /**
     * Convert float mixing buffer to byte array with limiting.
     */
    private byte[] convertToBytes(float[] mixBuffer, float mixWeight, float finalBoost) {
        ByteBuffer bb = ByteBuffer.allocate(mixBuffer.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        
        for (float sample : mixBuffer) {
            // Apply mix weight and final boost
            float finalSample = sample * mixWeight * finalBoost;
            
            // Soft limiting to prevent clipping
            if (finalSample > 0.99f) {
                finalSample = 0.99f + (finalSample - 0.99f) * 0.1f;
                if (finalSample > 1.0f) finalSample = 1.0f;
            } else if (finalSample < -0.99f) {
                finalSample = -0.99f + (finalSample + 0.99f) * 0.1f;
                if (finalSample < -1.0f) finalSample = -1.0f;
            }
            
            // Convert to 16-bit signed integer
            short shortSample = (short)(finalSample * 32767);
            bb.putShort(shortSample);
        }
        
        return bb.array();
    }
    
    /**
     * Create silence for a given duration.
     */
    private byte[] createSilence(int durationMs) {
        int samplesPerMs = (int)(MIXER_FORMAT.getSampleRate() / 1000);
        int totalSamples = samplesPerMs * durationMs;
        int bytesPerSample = MIXER_FORMAT.getSampleSizeInBits() / 8 * MIXER_FORMAT.getChannels();
        return new byte[totalSamples * bytesPerSample];
    }
    
    /**
     * Play mixed audio directly (for testing).
     */
    public void playMixedAudio(byte[] audioData) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, MIXER_FORMAT);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        
        line.open(MIXER_FORMAT);
        line.start();
        line.write(audioData, 0, audioData.length);
        line.drain();
        line.close();
    }
    
    /**
     * Save mixed audio to WAV file.
     */
    public void saveToWav(byte[] audioData, String outputPath) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
        AudioInputStream audioIn = new AudioInputStream(bais, MIXER_FORMAT, 
            audioData.length / MIXER_FORMAT.getFrameSize());
        
        File outputFile = new File(outputPath);
        AudioSystem.write(audioIn, AudioFileFormat.Type.WAVE, outputFile);
        
        logger.info("Saved mixed audio to: {}", outputPath);
    }
    
    /**
     * Load default hitsound from resources.
     */
    private AudioSample loadDefaultHitsound(String fileName) {
        // Extract the hitsound type from filename
        String baseName = fileName.replace(".wav", "").replace(".mp3", "").replace(".ogg", "");
        
        // Check cache first
        if (defaultHitsoundCache.containsKey(baseName)) {
            return defaultHitsoundCache.get(baseName);
        }
        
        // Try to load from default resources
        String[] possiblePaths = {
            "/default-skin/" + baseName + ".wav",
            "/default-skin/" + baseName + ".mp3",
            "/default-hitsounds/" + baseName + ".wav",
            "/default-hitsounds/" + baseName + ".mp3"
        };
        
        for (String resourcePath : possiblePaths) {
            try {
                InputStream stream = getClass().getResourceAsStream(resourcePath);
                if (stream != null) {
                    // Extract to temp file and load
                    File tempFile = File.createTempFile("default_" + baseName, ".wav");
                    tempFile.deleteOnExit();
                    
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = stream.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    AudioSample sample = loadAudioSample(tempFile.getAbsolutePath());
                    if (sample != null) {
                        defaultHitsoundCache.put(baseName, sample);
                        return sample;
                    }
                }
            } catch (Exception e) {
                logger.debug("Failed to load default hitsound: {}", resourcePath);
            }
        }
        
        // If no default found, create a short silent sample to prevent crashes
        return createSilentSample(100); // 100ms of silence
    }
    
    /**
     * Create a silent audio sample.
     */
    private AudioSample createSilentSample(int durationMs) {
        int sampleRate = (int) MIXER_FORMAT.getSampleRate();
        int channels = MIXER_FORMAT.getChannels();
        int bitsPerSample = MIXER_FORMAT.getSampleSizeInBits();
        int bytesPerSample = bitsPerSample / 8;
        int totalSamples = (sampleRate * durationMs) / 1000;
        int dataSize = totalSamples * channels * bytesPerSample;
        
        byte[] silentData = new byte[dataSize];
        return new AudioSample(silentData, totalSamples, MIXER_FORMAT);
    }
    
    /**
     * Clear the sample cache to free memory.
     */
    public void clearCache() {
        sampleCache.clear();
        // Keep default cache as it's small and reusable
    }
}