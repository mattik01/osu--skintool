package com.osuskin.tool.service;

import com.osuskin.tool.model.Configuration;
import com.osuskin.tool.model.Skin;
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
import static org.mockito.Mockito.*;

class SkinScannerServiceTest {

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
    @DisplayName("Should detect .zip compressed skin files")
    void shouldDetectZipFiles() throws IOException {
        // Arrange
        createMockZipFile(tempDir.resolve("TestSkin.zip"));
        
        // Act
        List<Skin> skins = scannerService.scanSkins();
        
        // Assert
        assertEquals(0, skins.size(), "Should find no extracted skins");
        assertEquals(1, scannerService.getCompressedSkinCount(), "Should find 1 compressed skin");
        
        List<Path> compressedFiles = scannerService.getCompressedSkinFiles();
        assertEquals(1, compressedFiles.size());
        assertTrue(compressedFiles.get(0).toString().endsWith("TestSkin.zip"));
    }

    @Test
    @DisplayName("Should detect .osk compressed skin files")
    void shouldDetectOskFiles() throws IOException {
        // Arrange
        createMockZipFile(tempDir.resolve("TestSkin.osk"));
        
        // Act
        List<Skin> skins = scannerService.scanSkins();
        
        // Assert
        assertEquals(0, skins.size(), "Should find no extracted skins");
        assertEquals(1, scannerService.getCompressedSkinCount(), "Should find 1 compressed skin");
        
        List<Path> compressedFiles = scannerService.getCompressedSkinFiles();
        assertEquals(1, compressedFiles.size());
        assertTrue(compressedFiles.get(0).toString().endsWith("TestSkin.osk"));
    }

    @Test
    @DisplayName("Should detect .tar compressed skin files")
    void shouldDetectTarFiles() throws IOException {
        // Arrange
        Files.createFile(tempDir.resolve("TestSkin.tar"));
        
        // Act
        List<Skin> skins = scannerService.scanSkins();
        
        // Assert
        assertEquals(0, skins.size(), "Should find no extracted skins");
        assertEquals(1, scannerService.getCompressedSkinCount(), "Should find 1 compressed skin");
        
        List<Path> compressedFiles = scannerService.getCompressedSkinFiles();
        assertEquals(1, compressedFiles.size());
        assertTrue(compressedFiles.get(0).toString().endsWith("TestSkin.tar"));
    }

    @Test
    @DisplayName("Should detect multiple compressed formats")
    void shouldDetectMultipleFormats() throws IOException {
        // Arrange
        createMockZipFile(tempDir.resolve("Skin1.zip"));
        createMockZipFile(tempDir.resolve("Skin2.osk"));
        Files.createFile(tempDir.resolve("Skin3.tar"));
        Files.createFile(tempDir.resolve("Skin4.tar.gz"));
        
        // Act
        List<Skin> skins = scannerService.scanSkins();
        
        // Assert
        assertEquals(0, skins.size(), "Should find no extracted skins");
        assertEquals(4, scannerService.getCompressedSkinCount(), "Should find 4 compressed skins");
    }

    @Test
    @DisplayName("Should detect mix of extracted and compressed skins")
    void shouldDetectMixedSkins() throws IOException {
        // Arrange - create extracted skin directory
        Path extractedSkinDir = tempDir.resolve("ExtractedSkin");
        Files.createDirectories(extractedSkinDir);
        Files.createFile(extractedSkinDir.resolve("skin.ini"));
        
        // Create compressed skin
        createMockZipFile(tempDir.resolve("CompressedSkin.zip"));
        
        // Act
        List<Skin> skins = scannerService.scanSkins();
        
        // Assert
        assertEquals(1, skins.size(), "Should find 1 extracted skin");
        assertEquals(1, scannerService.getCompressedSkinCount(), "Should find 1 compressed skin");
    }

    @Test
    @DisplayName("Should find no compressed files in empty directory")
    void shouldFindNoCompressedFilesInEmptyDirectory() throws IOException {
        // Act
        List<Skin> skins = scannerService.scanSkins();
        
        // Assert
        assertEquals(0, skins.size(), "Should find no skins");
        assertEquals(0, scannerService.getCompressedSkinCount(), "Should find no compressed skins");
        assertTrue(scannerService.getCompressedSkinFiles().isEmpty());
    }

    @Test
    @DisplayName("Should ignore non-skin files")
    void shouldIgnoreNonSkinFiles() throws IOException {
        // Arrange
        Files.createFile(tempDir.resolve("document.txt"));
        Files.createFile(tempDir.resolve("image.png")); // Not compressed
        Files.createFile(tempDir.resolve("data.json"));
        Files.createFile(tempDir.resolve("archive.rar")); // Not supported format
        
        // Act
        List<Skin> skins = scannerService.scanSkins();
        
        // Assert
        assertEquals(0, skins.size(), "Should find no skins");
        assertEquals(0, scannerService.getCompressedSkinCount(), "Should find no compressed skins");
    }

    @Test
    @DisplayName("Should successfully extract ZIP file")
    void shouldSuccessfullyExtractZipFile() throws IOException {
        // Arrange
        Path zipFile = tempDir.resolve("TestSkin.zip");
        createMockZipFileWithSkinContent(zipFile);
        
        // Act
        boolean result = scannerService.extractCompressedSkin(zipFile);
        
        // Assert
        assertTrue(result, "Extraction should succeed");
        assertTrue(Files.exists(tempDir.resolve("TestSkin")), "Extracted directory should exist");
        assertTrue(Files.exists(tempDir.resolve("TestSkin/skin.ini")), "skin.ini should be extracted");
        assertTrue(Files.exists(tempDir.resolve("TestSkin/hitcircle.png")), "Image file should be extracted");
    }

    @Test
    @DisplayName("Should successfully extract OSK file")
    void shouldSuccessfullyExtractOskFile() throws IOException {
        // Arrange
        Path oskFile = tempDir.resolve("TestSkin.osk");
        createMockZipFileWithSkinContent(oskFile);
        
        // Act
        boolean result = scannerService.extractCompressedSkin(oskFile);
        
        // Assert
        assertTrue(result, "Extraction should succeed");
        assertTrue(Files.exists(tempDir.resolve("TestSkin")), "Extracted directory should exist");
        assertTrue(Files.exists(tempDir.resolve("TestSkin/skin.ini")), "skin.ini should be extracted");
    }

    @Test
    @DisplayName("Should fail extraction when directory already exists")
    void shouldFailExtractionWhenDirectoryExists() throws IOException {
        // Arrange
        Path zipFile = tempDir.resolve("TestSkin.zip");
        createMockZipFile(zipFile);
        
        // Create existing directory
        Files.createDirectories(tempDir.resolve("TestSkin"));
        
        // Act
        boolean result = scannerService.extractCompressedSkin(zipFile);
        
        // Assert
        assertFalse(result, "Extraction should fail when directory exists");
    }

    @Test
    @DisplayName("Should fail extraction when file doesn't exist")
    void shouldFailExtractionWhenFileDoesntExist() throws IOException {
        // Arrange
        Path nonExistentFile = tempDir.resolve("NonExistent.zip");
        
        // Act
        boolean result = scannerService.extractCompressedSkin(nonExistentFile);
        
        // Assert
        assertFalse(result, "Extraction should fail when file doesn't exist");
    }

    @Test
    @DisplayName("Should fail extraction for unsupported format")
    void shouldFailExtractionForUnsupportedFormat() throws IOException {
        // Arrange
        Path unsupportedFile = tempDir.resolve("TestSkin.rar");
        Files.createFile(unsupportedFile);
        
        // Act
        boolean result = scannerService.extractCompressedSkin(unsupportedFile);
        
        // Assert
        assertFalse(result, "Extraction should fail for unsupported format");
    }

    @Test
    @DisplayName("Should clear compressed files list on new scan")
    void shouldClearCompressedFilesListOnNewScan() throws IOException {
        // Arrange - first scan with compressed file
        createMockZipFile(tempDir.resolve("TestSkin.zip"));
        scannerService.scanSkins();
        assertEquals(1, scannerService.getCompressedSkinCount(), "Should initially find 1 compressed skin");
        
        // Delete the compressed file
        Files.delete(tempDir.resolve("TestSkin.zip"));
        
        // Act - second scan
        scannerService.scanSkins();
        
        // Assert
        assertEquals(0, scannerService.getCompressedSkinCount(), "Should find no compressed skins after deletion");
    }

    @Test
    @DisplayName("Should handle directory scan when skins directory doesn't exist")
    void shouldHandleNonExistentDirectory() throws IOException {
        // Arrange
        Path nonExistentDir = tempDir.resolve("nonexistent");
        when(configuration.getOsuSkinsDirectoryPath()).thenReturn(nonExistentDir);
        
        // Act
        List<Skin> skins = scannerService.scanSkins();
        
        // Assert
        assertEquals(0, skins.size(), "Should return empty list when directory doesn't exist");
        assertEquals(0, scannerService.getCompressedSkinCount(), "Should find no compressed skins");
    }

    @Test
    @DisplayName("Should not detect compressed files that are already extracted")
    void shouldNotDetectAlreadyExtractedFiles() throws IOException {
        // Arrange - create compressed file
        createMockZipFileWithSkinContent(tempDir.resolve("TestSkin.zip"));
        
        // Initial scan - should find compressed file
        var initialSkins = scannerService.scanSkins();
        assertEquals(1, scannerService.getCompressedSkinCount(), "Should initially find compressed file");
        
        // Extract the skin
        scannerService.extractCompressedSkin(tempDir.resolve("TestSkin.zip"));
        
        // Scan again - should NOT find the compressed file anymore
        var afterExtract = scannerService.scanSkins();
        assertEquals(0, scannerService.getCompressedSkinCount(), "Should not count extracted compressed files");
        assertEquals(1, afterExtract.size(), "Should find extracted skin directory");
    }

    @Test
    @DisplayName("Should return only extractable compressed files")
    void shouldReturnOnlyExtractableFiles() throws IOException {
        // Arrange
        createMockZipFileWithSkinContent(tempDir.resolve("Extractable.zip"));
        createMockZipFileWithSkinContent(tempDir.resolve("AlreadyExtracted.zip"));
        
        // Create extracted directory for one file (simulate already extracted)
        Files.createDirectories(tempDir.resolve("AlreadyExtracted"));
        Files.createFile(tempDir.resolve("AlreadyExtracted/skin.ini"));
        
        // Act
        scannerService.scanSkins();
        var extractableFiles = scannerService.getExtractableCompressedSkinFiles();
        
        // Assert
        assertEquals(1, scannerService.getCompressedSkinCount(), "Should only count non-extracted files");
        assertEquals(1, extractableFiles.size(), "Should return only extractable files");
        assertTrue(extractableFiles.get(0).toString().contains("Extractable.zip"), 
                  "Should return the non-extracted file");
    }

    // Helper methods
    private void createMockZipFile(Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            ZipEntry entry = new ZipEntry("dummy.txt");
            zos.putNextEntry(entry);
            zos.write("dummy content".getBytes());
            zos.closeEntry();
        }
    }
    
    private void createMockZipFileWithSkinContent(Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            // Add skin.ini
            ZipEntry skinIni = new ZipEntry("skin.ini");
            zos.putNextEntry(skinIni);
            zos.write("[General]\nName: Test Skin\nAuthor: Test Author".getBytes());
            zos.closeEntry();
            
            // Add image file
            ZipEntry image = new ZipEntry("hitcircle.png");
            zos.putNextEntry(image);
            zos.write("fake image data".getBytes());
            zos.closeEntry();
            
            // Add subdirectory
            ZipEntry subdir = new ZipEntry("sounds/");
            zos.putNextEntry(subdir);
            zos.closeEntry();
            
            // Add file in subdirectory
            ZipEntry sound = new ZipEntry("sounds/hitnormal.wav");
            zos.putNextEntry(sound);
            zos.write("fake sound data".getBytes());
            zos.closeEntry();
        }
    }
}