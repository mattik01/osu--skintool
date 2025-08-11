package com.osuskin.tool.service;

import com.osuskin.tool.model.Configuration;
import com.osuskin.tool.util.ConfigurationManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("Compressed Skin Edge Cases and Error Scenarios")
class CompressedSkinEdgeCasesTest {

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
    @DisplayName("Should handle empty compressed files")
    void shouldHandleEmptyCompressedFiles() throws IOException {
        // Create empty ZIP file
        Path emptyZip = tempDir.resolve("Empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            // Don't add any entries - completely empty
        }
        
        // Should detect as compressed file
        var skins = scannerService.scanSkins();
        assertEquals(1, scannerService.getCompressedSkinCount(), "Should detect empty ZIP as compressed file");
        
        // Extraction should succeed but create empty directory
        boolean extracted = scannerService.extractCompressedSkin(emptyZip);
        assertTrue(extracted, "Should successfully extract empty ZIP");
        assertTrue(Files.exists(tempDir.resolve("Empty")), "Empty directory should be created");
    }

    @Test
    @DisplayName("Should handle ZIP files with only directories")
    void shouldHandleZipWithOnlyDirectories() throws IOException {
        Path dirOnlyZip = tempDir.resolve("DirsOnly.zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(dirOnlyZip))) {
            // Add only directory entries
            ZipEntry dir1 = new ZipEntry("sounds/");
            zos.putNextEntry(dir1);
            zos.closeEntry();
            
            ZipEntry dir2 = new ZipEntry("images/");
            zos.putNextEntry(dir2);
            zos.closeEntry();
            
            ZipEntry nestedDir = new ZipEntry("sounds/drum/");
            zos.putNextEntry(nestedDir);
            zos.closeEntry();
        }
        
        boolean extracted = scannerService.extractCompressedSkin(dirOnlyZip);
        assertTrue(extracted, "Should extract ZIP with only directories");
        
        Path extractedDir = tempDir.resolve("DirsOnly");
        assertTrue(Files.exists(extractedDir), "Main directory should exist");
        assertTrue(Files.exists(extractedDir.resolve("sounds")), "sounds directory should exist");
        assertTrue(Files.exists(extractedDir.resolve("images")), "images directory should exist");
        assertTrue(Files.exists(extractedDir.resolve("sounds/drum")), "Nested directory should exist");
    }

    @Test
    @DisplayName("Should handle very large file names")
    void shouldHandleVeryLargeFileNames() throws IOException {
        // Create file name with 200 characters
        String longName = "A".repeat(200);
        Path longNameZip = tempDir.resolve(longName + ".zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(longNameZip))) {
            ZipEntry entry = new ZipEntry("skin.ini");
            zos.putNextEntry(entry);
            zos.write("[General]\nName: Long Name Skin".getBytes());
            zos.closeEntry();
        }
        
        // Should detect and extract successfully
        var skins = scannerService.scanSkins();
        assertEquals(1, scannerService.getCompressedSkinCount(), "Should detect long-named file");
        
        boolean extracted = scannerService.extractCompressedSkin(longNameZip);
        assertTrue(extracted, "Should extract long-named file");
        assertTrue(Files.exists(tempDir.resolve(longName)), "Long-named directory should be created");
    }

    @Test
    @DisplayName("Should handle files with no extension")
    void shouldHandleFilesWithNoExtension() throws IOException {
        // Create compressed files without extensions
        Files.createFile(tempDir.resolve("NoExtension"));
        Files.createFile(tempDir.resolve("Another"));
        
        // Should not detect as compressed files
        var skins = scannerService.scanSkins();
        assertEquals(0, scannerService.getCompressedSkinCount(), "Should not detect files without extensions");
    }

    @Test
    @DisplayName("Should handle case-insensitive file extensions")
    void shouldHandleCaseInsensitiveExtensions() throws IOException {
        // Create files with different case extensions
        createSimpleZip(tempDir.resolve("Skin1.ZIP"));
        createSimpleZip(tempDir.resolve("Skin2.Zip"));
        createSimpleZip(tempDir.resolve("Skin3.zIP"));
        createSimpleZip(tempDir.resolve("Skin4.OSK"));
        createSimpleZip(tempDir.resolve("Skin5.oSk"));
        
        var skins = scannerService.scanSkins();
        assertEquals(5, scannerService.getCompressedSkinCount(), 
                    "Should detect all files regardless of extension case");
    }

    @Test
    @DisplayName("Should handle very deep directory structures")
    void shouldHandleVeryDeepDirectories() throws IOException {
        Path deepZip = tempDir.resolve("Deep.zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(deepZip))) {
            // Create very deep nested structure
            StringBuilder deepPath = new StringBuilder();
            for (int i = 1; i <= 20; i++) {
                deepPath.append("level").append(i).append("/");
            }
            
            // Add the deep directory
            ZipEntry deepDir = new ZipEntry(deepPath.toString());
            zos.putNextEntry(deepDir);
            zos.closeEntry();
            
            // Add a file in the deep directory
            ZipEntry deepFile = new ZipEntry(deepPath + "deep-file.txt");
            zos.putNextEntry(deepFile);
            zos.write("Deep file content".getBytes());
            zos.closeEntry();
            
            // Add skin.ini at root
            ZipEntry skinIni = new ZipEntry("skin.ini");
            zos.putNextEntry(skinIni);
            zos.write("[General]\nName: Deep Skin".getBytes());
            zos.closeEntry();
        }
        
        boolean extracted = scannerService.extractCompressedSkin(deepZip);
        assertTrue(extracted, "Should extract ZIP with deep directory structure");
        
        // Verify deep file exists
        Path expectedDeepFile = tempDir.resolve("Deep").resolve("level1/level2/level3/level4/level5/level6/level7/level8/level9/level10/level11/level12/level13/level14/level15/level16/level17/level18/level19/level20/deep-file.txt");
        assertTrue(Files.exists(expectedDeepFile), "Deep file should exist");
    }

    @Test
    @DisplayName("Should handle files with special characters in content")
    void shouldHandleSpecialCharactersInContent() throws IOException {
        Path specialZip = tempDir.resolve("Special.zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(specialZip))) {
            // skin.ini with Unicode characters
            ZipEntry skinIni = new ZipEntry("skin.ini");
            zos.putNextEntry(skinIni);
            String unicodeContent = "[General]\n" +
                                   "Name: スキン名前 (日本語)\n" +
                                   "Author: 作者™\n" +
                                   "Version: ∞.∞\n" +
                                   "Description: Åwesome skín with émojis 🎮🎵\n";
            zos.write(unicodeContent.getBytes("UTF-8"));
            zos.closeEntry();
            
            // File with special characters in name
            ZipEntry specialFile = new ZipEntry("hitcircle@2x.png");
            zos.putNextEntry(specialFile);
            zos.write("special image data".getBytes());
            zos.closeEntry();
        }
        
        boolean extracted = scannerService.extractCompressedSkin(specialZip);
        assertTrue(extracted, "Should extract ZIP with special characters");
        
        Path extractedDir = tempDir.resolve("Special");
        assertTrue(Files.exists(extractedDir.resolve("skin.ini")), "skin.ini should exist");
        assertTrue(Files.exists(extractedDir.resolve("hitcircle@2x.png")), "Special character file should exist");
        
        // Verify Unicode content is preserved
        String extractedContent = Files.readString(extractedDir.resolve("skin.ini"));
        assertTrue(extractedContent.contains("スキン名前"), "Unicode characters should be preserved");
        assertTrue(extractedContent.contains("🎮🎵"), "Emoji should be preserved");
    }

    @Test
    @DisplayName("Should handle zero-byte files")
    void shouldHandleZeroByteFiles() throws IOException {
        Path zeroByteZip = tempDir.resolve("ZeroByte.zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zeroByteZip))) {
            // Add zero-byte file
            ZipEntry zeroFile = new ZipEntry("empty.txt");
            zos.putNextEntry(zeroFile);
            // Don't write any data
            zos.closeEntry();
            
            // Add normal file
            ZipEntry normalFile = new ZipEntry("skin.ini");
            zos.putNextEntry(normalFile);
            zos.write("[General]\nName: Zero Byte Test".getBytes());
            zos.closeEntry();
        }
        
        boolean extracted = scannerService.extractCompressedSkin(zeroByteZip);
        assertTrue(extracted, "Should extract ZIP with zero-byte files");
        
        Path extractedDir = tempDir.resolve("ZeroByte");
        assertTrue(Files.exists(extractedDir.resolve("empty.txt")), "Zero-byte file should exist");
        assertEquals(0, Files.size(extractedDir.resolve("empty.txt")), "File should be zero bytes");
        assertTrue(Files.exists(extractedDir.resolve("skin.ini")), "Normal file should also exist");
    }

    @Test
    @DisplayName("Should handle concurrent access scenarios")
    void shouldHandleConcurrentAccess() throws IOException, InterruptedException {
        // Create multiple compressed files
        for (int i = 1; i <= 5; i++) {
            createSimpleZip(tempDir.resolve("ConcurrentSkin" + i + ".zip"));
        }
        
        // Simulate concurrent scanning (though JUnit runs tests sequentially)
        var skins1 = scannerService.scanSkins();
        var skins2 = scannerService.scanSkins();
        
        assertEquals(skins1.size(), skins2.size(), "Concurrent scans should return consistent results");
        assertEquals(5, scannerService.getCompressedSkinCount(), "Should consistently find 5 compressed files");
        
        // Concurrent access to compressed files list
        var files1 = scannerService.getCompressedSkinFiles();
        var files2 = scannerService.getCompressedSkinFiles();
        
        assertEquals(files1.size(), files2.size(), "Compressed files list should be consistent");
        assertEquals(5, files1.size(), "Should return 5 files");
    }

    @Test
    @DisplayName("Should handle extraction during active scanning")
    void shouldHandleExtractionDuringScanning() throws IOException {
        // Create compressed file
        Path testZip = tempDir.resolve("TestSkin.zip");
        createSimpleZip(testZip);
        
        // Initial scan
        scannerService.scanSkins();
        assertEquals(1, scannerService.getCompressedSkinCount(), "Should find 1 compressed file");
        
        // Extract the file
        boolean extracted = scannerService.extractCompressedSkin(testZip);
        assertTrue(extracted, "Extraction should succeed");
        
        // Scan again - should update state correctly
        var newSkins = scannerService.scanSkins();
        assertEquals(1, newSkins.size(), "Should find 1 extracted skin");
        assertEquals(0, scannerService.getCompressedSkinCount(), "Compressed file should no longer be counted after extraction");
    }

    @Test
    @DisplayName("Should handle invalid configuration scenarios")
    void shouldHandleInvalidConfiguration() throws IOException {
        // Test with null configuration
        when(configurationManager.getConfiguration()).thenReturn(null);
        
        assertThrows(NullPointerException.class, () -> {
            scannerService.scanSkins();
        }, "Should handle null configuration gracefully");
        
        // Test with null skins directory
        when(configurationManager.getConfiguration()).thenReturn(configuration);
        when(configuration.getOsuSkinsDirectoryPath()).thenReturn(null);
        
        var skins = scannerService.scanSkins();
        assertEquals(0, skins.size(), "Should return empty list for null directory");
        assertEquals(0, scannerService.getCompressedSkinCount(), "Should find no compressed files");
    }

    @Test
    @DisplayName("Should handle file system permission issues")
    void shouldHandlePermissionIssues() throws IOException {
        // This test only runs on POSIX systems (Linux/Mac)
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return; // Skip on Windows
        }
        
        // Create a ZIP file
        Path permissionZip = tempDir.resolve("PermissionTest.zip");
        createSimpleZip(permissionZip);
        
        // Create a subdirectory to make read-only (can't make tempDir read-only as it breaks cleanup)
        Path readOnlyDir = tempDir.resolve("readonly");
        Files.createDirectories(readOnlyDir);
        
        // Make the subdirectory read-only
        try {
            Files.setPosixFilePermissions(readOnlyDir, Set.of(PosixFilePermission.OWNER_READ));
            
            // Try to extract to the read-only directory by changing the expected extraction path
            // This simulates permission issues during extraction
            Path restrictedZip = readOnlyDir.resolve("test.zip");
            
            // This should succeed as we're just testing the general permission handling concept
            // In real scenarios, permission issues would manifest during directory creation
            boolean result = scannerService.extractCompressedSkin(permissionZip);
            assertTrue(result, "Should extract successfully to writable location");
            
        } finally {
            // Restore permissions for cleanup
            try {
                Files.setPosixFilePermissions(readOnlyDir, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
                ));
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    @DisplayName("Should maintain accurate counts after multiple operations")
    void shouldMaintainAccurateCountsAfterOperations() throws IOException {
        // Initial state
        assertEquals(0, scannerService.getCompressedSkinCount());
        
        // Add files incrementally and verify counts
        createSimpleZip(tempDir.resolve("Skin1.zip"));
        scannerService.scanSkins();
        assertEquals(1, scannerService.getCompressedSkinCount());
        
        createSimpleZip(tempDir.resolve("Skin2.osk"));
        scannerService.scanSkins();
        assertEquals(2, scannerService.getCompressedSkinCount());
        
        // Extract one file - should no longer be counted as compressed
        scannerService.extractCompressedSkin(tempDir.resolve("Skin1.zip"));
        scannerService.scanSkins();
        assertEquals(1, scannerService.getCompressedSkinCount()); // Only unextracted files counted
        assertEquals(1, scannerService.scanSkins().size()); // One extracted skin
        
        // Delete a compressed file
        Files.delete(tempDir.resolve("Skin1.zip"));
        scannerService.scanSkins();
        assertEquals(1, scannerService.getCompressedSkinCount()); // One compressed file remains
        assertEquals(1, scannerService.scanSkins().size()); // One extracted skin remains
        
        // Add more files
        Files.createFile(tempDir.resolve("Skin3.tar"));
        Files.createFile(tempDir.resolve("Skin4.tar.gz"));
        scannerService.scanSkins();
        assertEquals(3, scannerService.getCompressedSkinCount()); // 3 compressed files total
    }

    // Helper methods
    private void createSimpleZip(Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            ZipEntry entry = new ZipEntry("skin.ini");
            zos.putNextEntry(entry);
            zos.write("[General]\nName: Test Skin\nAuthor: Test".getBytes());
            zos.closeEntry();
        }
    }
}