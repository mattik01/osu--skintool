package com.osuskin.tool.service;

import com.osuskin.tool.model.Configuration;
import com.osuskin.tool.util.ConfigurationManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("Compressed Skin Extraction Integration Tests")
class CompressedSkinExtractionIntegrationTest {

    @TempDir
    Path tempDir;

    @Mock
    private ConfigurationManager configurationManager;

    @Mock
    private Configuration configuration;

    private SkinScannerService scannerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(configurationManager.getConfiguration()).thenReturn(configuration);
        when(configuration.getOsuSkinsDirectoryPath()).thenReturn(tempDir);
        
        scannerService = new SkinScannerService(configurationManager);
    }

    @Test
    @DisplayName("Complete workflow: scan -> extract -> scan again")
    void shouldCompleteFullWorkflow() throws IOException {
        // Phase 1: Create compressed skins
        createRealisticOskFile(tempDir.resolve("CoolSkin.osk"), "Cool Skin", "Author1");
        createRealisticZipFile(tempDir.resolve("AwesomeSkin.zip"), "Awesome Skin", "Author2");
        
        // Phase 2: Initial scan - should find compressed files
        var initialSkins = scannerService.scanSkins();
        assertEquals(0, initialSkins.size(), "Should find no extracted skins initially");
        assertEquals(2, scannerService.getCompressedSkinCount(), "Should find 2 compressed skins");
        
        // Phase 3: Extract all compressed files
        var compressedFiles = scannerService.getCompressedSkinFiles();
        assertEquals(2, compressedFiles.size(), "Should have 2 files to extract");
        
        for (Path compressedFile : compressedFiles) {
            boolean extracted = scannerService.extractCompressedSkin(compressedFile);
            assertTrue(extracted, "Should successfully extract " + compressedFile.getFileName());
        }
        
        // Phase 4: Verify extraction results
        assertTrue(Files.exists(tempDir.resolve("CoolSkin")), "CoolSkin directory should exist");
        assertTrue(Files.exists(tempDir.resolve("CoolSkin/skin.ini")), "CoolSkin skin.ini should exist");
        assertTrue(Files.exists(tempDir.resolve("AwesomeSkin")), "AwesomeSkin directory should exist");
        assertTrue(Files.exists(tempDir.resolve("AwesomeSkin/skin.ini")), "AwesomeSkin skin.ini should exist");
        
        // Phase 5: Scan again - should find extracted skins but no longer count compressed files as extractable
        var finalSkins = scannerService.scanSkins();
        assertEquals(2, finalSkins.size(), "Should find 2 extracted skins after extraction");
        assertEquals(0, scannerService.getCompressedSkinCount(), "Compressed files should no longer be counted after extraction");
        
        // Phase 6: Verify skin data
        var skinNames = finalSkins.stream().map(skin -> skin.getName()).toList();
        assertTrue(skinNames.contains("Cool Skin") || skinNames.contains("CoolSkin"), 
                  "Should find Cool Skin");
        assertTrue(skinNames.contains("Awesome Skin") || skinNames.contains("AwesomeSkin"), 
                  "Should find Awesome Skin");
    }

    @Test
    @DisplayName("Should handle already extracted scenario")
    void shouldHandleAlreadyExtractedScenario() throws IOException {
        // Create compressed file
        Path oskFile = tempDir.resolve("TestSkin.osk");
        createRealisticOskFile(oskFile, "Test Skin", "Test Author");
        
        // Extract it once
        boolean firstExtraction = scannerService.extractCompressedSkin(oskFile);
        assertTrue(firstExtraction, "First extraction should succeed");
        
        // Try to extract again - should fail because directory exists
        boolean secondExtraction = scannerService.extractCompressedSkin(oskFile);
        assertFalse(secondExtraction, "Second extraction should fail - directory already exists");
        
        // Directory should still exist with correct content
        assertTrue(Files.exists(tempDir.resolve("TestSkin")), "Directory should still exist");
        assertTrue(Files.exists(tempDir.resolve("TestSkin/skin.ini")), "skin.ini should still exist");
    }

    @Test
    @DisplayName("Should handle mixed content directory")
    void shouldHandleMixedContentDirectory() throws IOException {
        // Create extracted skin directory
        Path extractedSkinDir = tempDir.resolve("ExtractedSkin");
        Files.createDirectories(extractedSkinDir);
        Files.write(extractedSkinDir.resolve("skin.ini"), 
                   "[General]\nName: Extracted Skin\nAuthor: Manual Author".getBytes());
        Files.createFile(extractedSkinDir.resolve("hitcircle.png"));
        
        // Create compressed skins
        createRealisticOskFile(tempDir.resolve("CompressedSkin1.osk"), "Compressed Skin 1", "Author1");
        createRealisticZipFile(tempDir.resolve("CompressedSkin2.zip"), "Compressed Skin 2", "Author2");
        
        // Create non-skin files (should be ignored)
        Files.createFile(tempDir.resolve("readme.txt"));
        Files.createFile(tempDir.resolve("image.png"));
        Files.createFile(tempDir.resolve("unsupported.rar"));
        
        // Scan directory
        var skins = scannerService.scanSkins();
        
        // Verify results
        assertEquals(1, skins.size(), "Should find 1 extracted skin");
        assertEquals(2, scannerService.getCompressedSkinCount(), "Should find 2 compressed skins");
        
        var compressedFiles = scannerService.getCompressedSkinFiles();
        assertEquals(2, compressedFiles.size(), "Should have 2 compressed files in list");
        
        // Verify file names
        var compressedFileNames = compressedFiles.stream()
            .map(path -> path.getFileName().toString())
            .toList();
        assertTrue(compressedFileNames.contains("CompressedSkin1.osk"));
        assertTrue(compressedFileNames.contains("CompressedSkin2.zip"));
    }

    @Test
    @DisplayName("Should handle complex directory structure extraction")
    void shouldHandleComplexDirectoryStructure() throws IOException {
        Path complexZip = tempDir.resolve("ComplexSkin.zip");
        
        // Create zip with nested directories
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(complexZip))) {
            // Root files
            addZipEntry(zos, "skin.ini", "[General]\nName: Complex Skin\nAuthor: Complex Author");
            addZipEntry(zos, "hitcircle.png", "fake image data");
            
            // Sounds directory
            addZipEntry(zos, "sounds/", "");
            addZipEntry(zos, "sounds/hitnormal.wav", "fake sound data");
            addZipEntry(zos, "sounds/hitclap.wav", "fake sound data");
            
            // Nested subdirectory
            addZipEntry(zos, "sounds/drum/", "");
            addZipEntry(zos, "sounds/drum/drum-hitnormal.wav", "fake drum sound");
            
            // Images directory
            addZipEntry(zos, "images/", "");
            addZipEntry(zos, "images/spinner-background.png", "fake spinner image");
        }
        
        // Extract the complex zip
        boolean extracted = scannerService.extractCompressedSkin(complexZip);
        assertTrue(extracted, "Complex zip should extract successfully");
        
        // Verify structure
        Path extractedDir = tempDir.resolve("ComplexSkin");
        assertTrue(Files.exists(extractedDir), "Main directory should exist");
        assertTrue(Files.exists(extractedDir.resolve("skin.ini")), "skin.ini should exist");
        assertTrue(Files.exists(extractedDir.resolve("hitcircle.png")), "Root image should exist");
        
        // Verify subdirectories
        assertTrue(Files.exists(extractedDir.resolve("sounds")), "sounds directory should exist");
        assertTrue(Files.exists(extractedDir.resolve("sounds/hitnormal.wav")), "Sound file should exist");
        assertTrue(Files.exists(extractedDir.resolve("sounds/drum")), "Nested drum directory should exist");
        assertTrue(Files.exists(extractedDir.resolve("sounds/drum/drum-hitnormal.wav")), "Nested drum sound should exist");
        assertTrue(Files.exists(extractedDir.resolve("images")), "images directory should exist");
        assertTrue(Files.exists(extractedDir.resolve("images/spinner-background.png")), "Image should exist");
    }

    @Test
    @DisplayName("Should handle extraction failure and cleanup")
    void shouldHandleExtractionFailureAndCleanup() throws IOException {
        // Create a compressed file that will cause extraction issues
        Path problematicZip = tempDir.resolve("Problematic.zip");
        
        // Create an empty/corrupted zip file
        Files.write(problematicZip, "not a valid zip file".getBytes());
        
        // Attempt extraction
        assertThrows(IOException.class, () -> {
            scannerService.extractCompressedSkin(problematicZip);
        }, "Should throw IOException for corrupted zip");
        
        // Verify no partial directories were left behind
        assertFalse(Files.exists(tempDir.resolve("Problematic")), 
                   "No partial directory should exist after failed extraction");
    }

    @Test
    @DisplayName("Should handle file names with special characters")
    void shouldHandleSpecialCharacterFileNames() throws IOException {
        // Create files with special characters
        createRealisticOskFile(tempDir.resolve("- YUGEN -.osk"), "YUGEN", "Author");
        createRealisticZipFile(tempDir.resolve("Skin [HD].zip"), "HD Skin", "HD Author");
        createRealisticZipFile(tempDir.resolve("Skin (v2.1).zip"), "Skin v2.1", "Version Author");
        
        // Scan
        var skins = scannerService.scanSkins();
        assertEquals(0, skins.size(), "Should find no extracted skins");
        assertEquals(3, scannerService.getCompressedSkinCount(), "Should find 3 compressed skins");
        
        // Extract all
        var compressedFiles = scannerService.getCompressedSkinFiles();
        for (Path file : compressedFiles) {
            boolean extracted = scannerService.extractCompressedSkin(file);
            assertTrue(extracted, "Should extract file with special characters: " + file.getFileName());
        }
        
        // Verify extraction
        assertTrue(Files.exists(tempDir.resolve("- YUGEN -")), "Special character directory should exist");
        assertTrue(Files.exists(tempDir.resolve("Skin [HD]")), "Bracket directory should exist");
        assertTrue(Files.exists(tempDir.resolve("Skin (v2.1)")), "Parenthesis directory should exist");
    }

    @Test
    @DisplayName("Should maintain scan state between multiple operations")
    void shouldMaintainScanStateBetweenOperations() throws IOException {
        // Initial state - empty directory
        var initialSkins = scannerService.scanSkins();
        assertEquals(0, initialSkins.size());
        assertEquals(0, scannerService.getCompressedSkinCount());
        
        // Add compressed file and scan
        createRealisticOskFile(tempDir.resolve("Skin1.osk"), "Skin 1", "Author");
        var afterAdd = scannerService.scanSkins();
        assertEquals(1, scannerService.getCompressedSkinCount());
        
        // Extract and scan again
        scannerService.extractCompressedSkin(tempDir.resolve("Skin1.osk"));
        var afterExtract = scannerService.scanSkins();
        assertEquals(1, afterExtract.size());
        assertEquals(0, scannerService.getCompressedSkinCount()); // OSK file no longer counted after extraction
        
        // Add more compressed files and scan
        createRealisticZipFile(tempDir.resolve("Skin2.zip"), "Skin 2", "Author");
        createRealisticOskFile(tempDir.resolve("Skin3.osk"), "Skin 3", "Author");
        var afterMoreFiles = scannerService.scanSkins();
        assertEquals(1, afterMoreFiles.size()); // Still 1 extracted
        assertEquals(2, scannerService.getCompressedSkinCount()); // Now 2 new compressed files (Skin1 no longer counted)
        
        // Remove original compressed file and scan
        Files.delete(tempDir.resolve("Skin1.osk"));
        var afterDelete = scannerService.scanSkins();
        assertEquals(1, afterDelete.size()); // Still 1 extracted
        assertEquals(2, scannerService.getCompressedSkinCount()); // Still 2 compressed files remain
    }

    // Helper methods
    private void createRealisticOskFile(Path oskPath, String skinName, String author) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(oskPath))) {
            // skin.ini with realistic content
            String skinIni = String.format(
                "[General]\n" +
                "Name: %s\n" +
                "Author: %s\n" +
                "Version: 2.7\n" +
                "AnimationFramerate: 60\n" +
                "AllowSliderBallTint: 1\n" +
                "ComboBurstRandom: 1\n" +
                "CursorExpand: 1\n" +
                "CursorRotate: 1\n" +
                "HitCircleOverlayAboveNumber: 1\n" +
                "LayeredHitSounds: 1\n" +
                "SliderBallFlip: 1\n" +
                "SpinnerFadePlayfield: 1\n" +
                "SpinnerNoBlink: 1\n\n" +
                "[Colours]\n" +
                "Combo1: 255,192,0\n" +
                "Combo2: 0,202,0\n" +
                "Combo3: 18,124,255\n" +
                "Combo4: 242,24,104\n",
                skinName, author);
            
            addZipEntry(zos, "skin.ini", skinIni);
            
            // Common skin elements
            addZipEntry(zos, "hitcircle.png", "fake hitcircle image");
            addZipEntry(zos, "hitcircleoverlay.png", "fake overlay image");
            addZipEntry(zos, "approachcircle.png", "fake approach circle");
            addZipEntry(zos, "cursor.png", "fake cursor image");
            addZipEntry(zos, "cursortrail.png", "fake cursor trail");
            
            // Sound files
            addZipEntry(zos, "hitnormal.wav", "fake hit sound");
            addZipEntry(zos, "hitclap.wav", "fake clap sound");
            addZipEntry(zos, "hitfinish.wav", "fake finish sound");
        }
    }
    
    private void createRealisticZipFile(Path zipPath, String skinName, String author) throws IOException {
        createRealisticOskFile(zipPath, skinName, author); // Same format as OSK
    }
    
    private void addZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        if (!name.endsWith("/")) { // Don't write content for directories
            zos.write(content.getBytes());
        }
        zos.closeEntry();
    }
}