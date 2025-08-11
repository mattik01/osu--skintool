package com.osuskin.tool.service;

import com.osuskin.tool.model.Configuration;
import com.osuskin.tool.util.ConfigurationManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("Nested Folder Extraction Tests")
class NestedFolderExtractionTest {

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
    @DisplayName("Should extract skin with files at root level normally")
    void shouldExtractRootLevelSkinNormally() throws IOException {
        // Create ZIP with files at root level
        Path zipFile = tempDir.resolve("RootLevelSkin.zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            // Root level files
            addZipEntry(zos, "skin.ini", "[General]\nName: Root Level Skin\nAuthor: Test");
            addZipEntry(zos, "hitcircle.png", "image data");
            addZipEntry(zos, "cursor.png", "cursor data");
            addZipEntry(zos, "hitnormal.wav", "sound data");
            
            // Some subdirectories
            addZipEntry(zos, "sounds/", "");
            addZipEntry(zos, "sounds/drum-hitnormal.wav", "drum sound");
        }
        
        // Extract
        boolean extracted = scannerService.extractCompressedSkin(zipFile);
        assertTrue(extracted, "Extraction should succeed");
        
        // Verify files are extracted at root level
        Path extractedDir = tempDir.resolve("RootLevelSkin");
        assertTrue(Files.exists(extractedDir.resolve("skin.ini")), "skin.ini should be at root");
        assertTrue(Files.exists(extractedDir.resolve("hitcircle.png")), "hitcircle.png should be at root");
        assertTrue(Files.exists(extractedDir.resolve("cursor.png")), "cursor.png should be at root");
        assertTrue(Files.exists(extractedDir.resolve("sounds/drum-hitnormal.wav")), "Subdirectory should be preserved");
    }

    @Test
    @DisplayName("Should strip single nested folder and extract contents to root")
    void shouldStripSingleNestedFolder() throws IOException {
        // Create ZIP with all files in a nested folder
        Path zipFile = tempDir.resolve("NestedSkin.zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            // All files in "SkinFolder/" subdirectory
            addZipEntry(zos, "SkinFolder/", "");
            addZipEntry(zos, "SkinFolder/skin.ini", "[General]\nName: Nested Skin\nAuthor: Test");
            addZipEntry(zos, "SkinFolder/hitcircle.png", "image data");
            addZipEntry(zos, "SkinFolder/cursor.png", "cursor data");
            addZipEntry(zos, "SkinFolder/hitnormal.wav", "sound data");
            
            // Nested subdirectories
            addZipEntry(zos, "SkinFolder/sounds/", "");
            addZipEntry(zos, "SkinFolder/sounds/drum-hitnormal.wav", "drum sound");
            addZipEntry(zos, "SkinFolder/images/", "");
            addZipEntry(zos, "SkinFolder/images/spinner-background.png", "spinner image");
        }
        
        // Extract
        boolean extracted = scannerService.extractCompressedSkin(zipFile);
        assertTrue(extracted, "Extraction should succeed");
        
        // Verify nested folder is stripped and files are at root level
        Path extractedDir = tempDir.resolve("NestedSkin");
        assertTrue(Files.exists(extractedDir.resolve("skin.ini")), "skin.ini should be at root (nested folder stripped)");
        assertTrue(Files.exists(extractedDir.resolve("hitcircle.png")), "hitcircle.png should be at root");
        assertTrue(Files.exists(extractedDir.resolve("cursor.png")), "cursor.png should be at root");
        assertTrue(Files.exists(extractedDir.resolve("sounds/drum-hitnormal.wav")), "Subdirectory structure should be preserved");
        assertTrue(Files.exists(extractedDir.resolve("images/spinner-background.png")), "Image subdirectory should be preserved");
        
        // Verify the nested folder itself is not created
        assertFalse(Files.exists(extractedDir.resolve("SkinFolder")), "Original nested folder should not exist");
    }

    @Test
    @DisplayName("Should handle skin with same name as nested folder")
    void shouldHandleSameNameNestedFolder() throws IOException {
        // Create ZIP where the nested folder has the same name as the ZIP file
        Path zipFile = tempDir.resolve("CoolSkin.zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            // Files in "CoolSkin/" subdirectory (same name as ZIP)
            addZipEntry(zos, "CoolSkin/", "");
            addZipEntry(zos, "CoolSkin/skin.ini", "[General]\nName: Cool Skin\nAuthor: Test");
            addZipEntry(zos, "CoolSkin/hitcircle.png", "image data");
            addZipEntry(zos, "CoolSkin/slider-body.png", "slider data");
        }
        
        // Extract
        boolean extracted = scannerService.extractCompressedSkin(zipFile);
        assertTrue(extracted, "Extraction should succeed");
        
        // Verify files are extracted at root level (nested folder stripped)
        Path extractedDir = tempDir.resolve("CoolSkin");
        assertTrue(Files.exists(extractedDir.resolve("skin.ini")), "skin.ini should be at root");
        assertTrue(Files.exists(extractedDir.resolve("hitcircle.png")), "hitcircle.png should be at root");
        assertTrue(Files.exists(extractedDir.resolve("slider-body.png")), "slider-body.png should be at root");
        
        // Should not have double nesting
        assertFalse(Files.exists(extractedDir.resolve("CoolSkin/skin.ini")), "Should not have double nesting");
    }

    @Test
    @DisplayName("Should not strip folder when multiple root folders exist")
    void shouldNotStripWithMultipleRootFolders() throws IOException {
        // Create ZIP with multiple root folders
        Path zipFile = tempDir.resolve("MultiFolderSkin.zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            // Files in multiple root folders
            addZipEntry(zos, "Folder1/", "");
            addZipEntry(zos, "Folder1/skin.ini", "[General]\nName: Skin 1");
            addZipEntry(zos, "Folder1/hitcircle.png", "image data");
            
            addZipEntry(zos, "Folder2/", "");
            addZipEntry(zos, "Folder2/cursor.png", "cursor data");
            addZipEntry(zos, "Folder2/hitnormal.wav", "sound data");
            
            // Some root level files
            addZipEntry(zos, "readme.txt", "readme content");
        }
        
        // Extract
        boolean extracted = scannerService.extractCompressedSkin(zipFile);
        assertTrue(extracted, "Extraction should succeed");
        
        // Verify structure is preserved as-is
        Path extractedDir = tempDir.resolve("MultiFolderSkin");
        assertTrue(Files.exists(extractedDir.resolve("Folder1/skin.ini")), "Folder1 structure should be preserved");
        assertTrue(Files.exists(extractedDir.resolve("Folder1/hitcircle.png")), "Folder1 files should be preserved");
        assertTrue(Files.exists(extractedDir.resolve("Folder2/cursor.png")), "Folder2 structure should be preserved");
        assertTrue(Files.exists(extractedDir.resolve("readme.txt")), "Root level files should be preserved");
    }

    @Test
    @DisplayName("Should not strip folder when it contains less than 80% of entries")
    void shouldNotStripPartialFolder() throws IOException {
        // Create ZIP where most files are at root, but some are in a subfolder
        Path zipFile = tempDir.resolve("PartialNestedSkin.zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            // Most files at root level
            addZipEntry(zos, "skin.ini", "[General]\nName: Partial Skin");
            addZipEntry(zos, "hitcircle.png", "image data");
            addZipEntry(zos, "cursor.png", "cursor data");
            addZipEntry(zos, "hitnormal.wav", "sound data");
            addZipEntry(zos, "hitclap.wav", "clap sound");
            
            // Few files in subfolder
            addZipEntry(zos, "extras/", "");
            addZipEntry(zos, "extras/bonus-sound.wav", "bonus sound");
        }
        
        // Extract
        boolean extracted = scannerService.extractCompressedSkin(zipFile);
        assertTrue(extracted, "Extraction should succeed");
        
        // Verify structure is preserved as-is (no stripping)
        Path extractedDir = tempDir.resolve("PartialNestedSkin");
        assertTrue(Files.exists(extractedDir.resolve("skin.ini")), "Root files should be preserved");
        assertTrue(Files.exists(extractedDir.resolve("hitcircle.png")), "Root files should be preserved");
        assertTrue(Files.exists(extractedDir.resolve("extras/bonus-sound.wav")), "Subfolder should be preserved");
    }

    @Test
    @DisplayName("Should handle deeply nested single folder")
    void shouldHandleDeeplyNestedFolder() throws IOException {
        // Create ZIP with deeply nested structure but all in one path
        Path zipFile = tempDir.resolve("DeeplyNested.zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            // All files in a deep nested path
            addZipEntry(zos, "Level1/", "");
            addZipEntry(zos, "Level1/skin.ini", "[General]\nName: Deep Skin");
            addZipEntry(zos, "Level1/hitcircle.png", "image data");
            addZipEntry(zos, "Level1/sounds/", "");
            addZipEntry(zos, "Level1/sounds/hitnormal.wav", "sound data");
        }
        
        // Extract
        boolean extracted = scannerService.extractCompressedSkin(zipFile);
        assertTrue(extracted, "Extraction should succeed");
        
        // Verify nested folder is stripped
        Path extractedDir = tempDir.resolve("DeeplyNested");
        assertTrue(Files.exists(extractedDir.resolve("skin.ini")), "skin.ini should be at root");
        assertTrue(Files.exists(extractedDir.resolve("hitcircle.png")), "hitcircle.png should be at root");
        assertTrue(Files.exists(extractedDir.resolve("sounds/hitnormal.wav")), "Subdirectory should be preserved");
        
        // Verify nested folder is not created
        assertFalse(Files.exists(extractedDir.resolve("Level1")), "Nested folder should be stripped");
    }

    @Test
    @DisplayName("Should handle empty folders correctly")
    void shouldHandleEmptyFolders() throws IOException {
        // Create ZIP with nested folder containing only empty folders
        Path zipFile = tempDir.resolve("EmptyNested.zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            addZipEntry(zos, "SkinFolder/", "");
            addZipEntry(zos, "SkinFolder/skin.ini", "[General]\nName: Empty Test");
            addZipEntry(zos, "SkinFolder/sounds/", ""); // Empty folder
            addZipEntry(zos, "SkinFolder/images/", ""); // Empty folder
        }
        
        // Extract
        boolean extracted = scannerService.extractCompressedSkin(zipFile);
        assertTrue(extracted, "Extraction should succeed");
        
        // Verify structure
        Path extractedDir = tempDir.resolve("EmptyNested");
        assertTrue(Files.exists(extractedDir.resolve("skin.ini")), "skin.ini should be at root");
        assertTrue(Files.exists(extractedDir.resolve("sounds")), "Empty folders should be created");
        assertTrue(Files.exists(extractedDir.resolve("images")), "Empty folders should be created");
        assertTrue(Files.isDirectory(extractedDir.resolve("sounds")), "sounds should be a directory");
        assertTrue(Files.isDirectory(extractedDir.resolve("images")), "images should be a directory");
    }

    @Test
    @DisplayName("Should handle ZIP with no skin files (edge case)")
    void shouldHandleNoSkinFiles() throws IOException {
        // Create ZIP with no skin-related files
        Path zipFile = tempDir.resolve("NoSkinFiles.zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            addZipEntry(zos, "folder/", "");
            addZipEntry(zos, "folder/readme.txt", "This is a readme");
            addZipEntry(zos, "folder/document.pdf", "fake pdf data");
            addZipEntry(zos, "folder/image.jpg", "fake image");
        }
        
        // Extract
        boolean extracted = scannerService.extractCompressedSkin(zipFile);
        assertTrue(extracted, "Extraction should succeed even without skin files");
        
        // Verify structure is preserved (no stripping since no skin files detected)
        Path extractedDir = tempDir.resolve("NoSkinFiles");
        assertTrue(Files.exists(extractedDir.resolve("folder/readme.txt")), "Structure should be preserved");
        assertTrue(Files.exists(extractedDir.resolve("folder/document.pdf")), "All files should be preserved");
    }

    // Helper method
    private void addZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        if (!name.endsWith("/")) { // Don't write content for directories
            zos.write(content.getBytes());
        }
        zos.closeEntry();
    }
}