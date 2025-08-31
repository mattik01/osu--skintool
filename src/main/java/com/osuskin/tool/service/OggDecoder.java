package com.osuskin.tool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * OGG Vorbis decoder that converts OGG files to WAV format in cache.
 * This allows us to play OGG files without external dependencies.
 */
public class OggDecoder {
    private static final Logger logger = LoggerFactory.getLogger(OggDecoder.class);
    
    // Cache directory for converted files
    private static final Path CACHE_DIR = Path.of(System.getProperty("java.io.tmpdir"), "osu-skintool-audio-cache");
    
    // Map of original OGG path to converted WAV path
    private static final Map<String, Path> conversionCache = new ConcurrentHashMap<>();
    
    static {
        try {
            Files.createDirectories(CACHE_DIR);
            logger.info("Audio cache directory: {}", CACHE_DIR);
        } catch (IOException e) {
            logger.error("Failed to create cache directory", e);
        }
    }
    
    /**
     * Convert OGG to WAV using Java-based decoding.
     * For now, we'll use a simpler approach with ProcessBuilder but cache the results.
     */
    public static File convertOggToWav(String oggFile) throws IOException {
        Path oggPath = Path.of(oggFile);
        
        // Check if already converted and cached
        Path cachedWav = conversionCache.get(oggFile);
        if (cachedWav != null && Files.exists(cachedWav)) {
            return cachedWav.toFile();
        }
        
        // Generate cache filename based on file hash
        String cacheFileName = generateCacheFileName(oggPath);
        Path wavPath = CACHE_DIR.resolve(cacheFileName + ".wav");
        
        // Check if cached file exists
        if (Files.exists(wavPath)) {
            conversionCache.put(oggFile, wavPath);
            logger.debug("Using cached WAV: {}", wavPath);
            return wavPath.toFile();
        }
        
        // Try multiple conversion methods
        try {
            // Method 1: Try using ffmpeg if available
            if (convertUsingFFmpeg(oggPath, wavPath)) {
                conversionCache.put(oggFile, wavPath);
                return wavPath.toFile();
            }
        } catch (Exception e) {
            logger.debug("FFmpeg conversion failed, trying alternative method", e);
        }
        
        // Method 2: Try using VLC if available
        try {
            if (convertUsingVLC(oggPath, wavPath)) {
                conversionCache.put(oggFile, wavPath);
                return wavPath.toFile();
            }
        } catch (Exception e) {
            logger.debug("VLC conversion failed", e);
        }
        
        // Method 3: Return null to trigger fallback to default hitsounds
        // Don't create silent placeholders - let the system use defaults instead
        logger.warn("[OGG-DECODER] No OGG decoder available for: {} - will fall back to default hitsound", oggFile);
        return null;
    }
    
    /**
     * Try to convert using FFmpeg.
     */
    private static boolean convertUsingFFmpeg(Path oggPath, Path wavPath) {
        try {
            // Try different FFmpeg locations
            String[] ffmpegPaths = {
                "C:\\ProgramData\\chocolatey\\bin\\ffmpeg.exe",
                "ffmpeg",
                "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe"
            };
            
            String ffmpegPath = null;
            for (String path : ffmpegPaths) {
                if (new File(path).exists() || path.equals("ffmpeg")) {
                    ffmpegPath = path;
                    break;
                }
            }
            
            if (ffmpegPath == null) {
                return false;
            }
            
            ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-i", oggPath.toString(),
                "-f", "wav",
                "-acodec", "pcm_s16le",
                "-ar", "44100",
                "-ac", "2",
                "-y",
                wavPath.toString()
            );
            
            // Redirect to PIPE and consume the streams to avoid 'nul' file creation on Windows
            pb.redirectError(ProcessBuilder.Redirect.PIPE);
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
            
            Process process = pb.start();
            
            // Consume output streams to prevent blocking and avoid 'nul' file creation
            try (BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                // Consume streams in background
                Thread outConsumer = new Thread(() -> {
                    try {
                        while (stdOut.readLine() != null) {
                            // Discard output
                        }
                    } catch (IOException ignored) {}
                });
                Thread errConsumer = new Thread(() -> {
                    try {
                        while (stdErr.readLine() != null) {
                            // Discard errors
                        }
                    } catch (IOException ignored) {}
                });
                outConsumer.start();
                errConsumer.start();
                
                boolean finished = process.waitFor(10, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return false;
                }
                
                outConsumer.join(1000);
                errConsumer.join(1000);
            }
            
            int exitCode = process.exitValue();
            
            // Check if file was created regardless of exit code
            // FFmpeg on Windows sometimes returns non-zero codes even on success
            if (Files.exists(wavPath)) {
                long fileSize = Files.size(wavPath);
                logger.info("[OGG-DECODER] Converted OGG to WAV using FFmpeg: {} -> {} (size: {} bytes)", 
                    oggPath.getFileName(), wavPath.getFileName(), fileSize);
                
                // Check if the file is valid (WAV header is 44 bytes minimum)
                // Files that are exactly 78-80 bytes are likely intentionally silent (just WAV header + minimal data)
                // These should be kept as they represent intentional silence in the skin
                if (fileSize < 78) {
                    logger.warn("[OGG-DECODER] Converted WAV file is too small ({} bytes), likely corrupted: {}", 
                        fileSize, wavPath.getFileName());
                    Files.deleteIfExists(wavPath);
                    return false;
                } else if (fileSize <= 100) {
                    // Small file but valid - likely an intentionally silent hitsound
                    logger.info("[OGG-DECODER] Small WAV file detected ({} bytes) - intentionally silent hitsound: {}", 
                        fileSize, wavPath.getFileName());
                    // Keep the file - it's valid silence
                    return true;
                }
                // Log warning if exit code was non-zero but file was created
                if (exitCode != 0) {
                    logger.debug("[OGG-DECODER] FFmpeg returned non-zero exit code {} but file was created successfully", exitCode);
                }
                return true;
            } else {
                logger.warn("[OGG-DECODER] FFmpeg conversion failed - exit code: {}, file exists: {}", 
                    exitCode, Files.exists(wavPath));
            }
        } catch (Exception e) {
            // FFmpeg not available or conversion failed
        }
        return false;
    }
    
    /**
     * Try to convert using VLC.
     */
    private static boolean convertUsingVLC(Path oggPath, Path wavPath) {
        try {
            // Common VLC installation paths
            String[] vlcPaths = {
                "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe",
                "C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe",
                "vlc"
            };
            
            String vlcPath = null;
            for (String path : vlcPaths) {
                if (new File(path).exists() || path.equals("vlc")) {
                    vlcPath = path;
                    break;
                }
            }
            
            if (vlcPath == null) return false;
            
            ProcessBuilder pb = new ProcessBuilder(
                vlcPath,
                "-I", "dummy",
                "--no-video",
                oggPath.toString(),
                "--sout=#transcode{acodec=s16l,channels=2,samplerate=44100}:std{access=file,mux=wav,dst=" + wavPath.toString() + "}",
                "vlc://quit"
            );
            
            // Redirect to PIPE and consume the streams to avoid 'nul' file creation on Windows
            pb.redirectError(ProcessBuilder.Redirect.PIPE);
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
            
            Process process = pb.start();
            
            // Consume output streams to prevent blocking and avoid 'nul' file creation
            try (BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                // Consume streams in background
                Thread outConsumer = new Thread(() -> {
                    try {
                        while (stdOut.readLine() != null) {
                            // Discard output
                        }
                    } catch (IOException ignored) {}
                });
                Thread errConsumer = new Thread(() -> {
                    try {
                        while (stdErr.readLine() != null) {
                            // Discard errors
                        }
                    } catch (IOException ignored) {}
                });
                outConsumer.start();
                errConsumer.start();
                
                boolean finished = process.waitFor(10, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return false;
                }
                
                outConsumer.join(1000);
                errConsumer.join(1000);
            }
            
            // VLC doesn't always return proper exit codes, check if file was created
            Thread.sleep(500); // Give VLC time to write the file
            if (Files.exists(wavPath) && Files.size(wavPath) > 44) { // WAV header is 44 bytes
                logger.info("Converted OGG to WAV using VLC: {}", oggPath.getFileName());
                return true;
            }
        } catch (Exception e) {
            // VLC not available or conversion failed
        }
        return false;
    }
    
    /**
     * Create a silent WAV file as fallback.
     */
    private static void createSilentWav(Path wavPath, int durationMs) throws IOException {
        int sampleRate = 44100;
        int channels = 2;
        int bitsPerSample = 16;
        int bytesPerSample = bitsPerSample / 8;
        int totalSamples = (sampleRate * durationMs) / 1000;
        int dataSize = totalSamples * channels * bytesPerSample;
        
        try (FileOutputStream fos = new FileOutputStream(wavPath.toFile())) {
            // Write WAV header
            writeWavHeader(fos, dataSize, sampleRate, channels, bitsPerSample);
            
            // Write silence (zeros)
            byte[] silence = new byte[dataSize];
            fos.write(silence);
        }
    }
    
    /**
     * Write WAV file header.
     */
    private static void writeWavHeader(OutputStream out, int dataSize, int sampleRate, int channels, int bitsPerSample) throws IOException {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        
        out.write("RIFF".getBytes());
        writeInt(out, 36 + dataSize); // File size - 8
        out.write("WAVE".getBytes());
        out.write("fmt ".getBytes());
        writeInt(out, 16); // Subchunk1Size
        writeShort(out, (short) 1); // AudioFormat (PCM)
        writeShort(out, (short) channels);
        writeInt(out, sampleRate);
        writeInt(out, byteRate);
        writeShort(out, (short) blockAlign);
        writeShort(out, (short) bitsPerSample);
        out.write("data".getBytes());
        writeInt(out, dataSize);
    }
    
    private static void writeInt(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }
    
    private static void writeShort(OutputStream out, short value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }
    
    /**
     * Generate cache filename based on file content hash.
     */
    private static String generateCacheFileName(Path oggPath) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            
            // Use file path and modification time for cache key
            String key = oggPath.toString() + "_" + Files.getLastModifiedTime(oggPath).toMillis();
            byte[] hash = md.digest(key.getBytes());
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback to simple filename
            return oggPath.getFileName().toString().replace(".ogg", "");
        }
    }
    
    /**
     * Clear the conversion cache and delete cached files.
     */
    public static void clearCache() {
        conversionCache.clear();
        try {
            Files.list(CACHE_DIR)
                .filter(path -> path.toString().endsWith(".wav"))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        logger.debug("Failed to delete cached file: {}", path);
                    }
                });
            logger.info("Cleared audio conversion cache");
        } catch (IOException e) {
            logger.error("Failed to clear cache directory", e);
        }
    }
}