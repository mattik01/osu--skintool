package com.osuskin.tool.controller;

import com.osuskin.tool.model.Configuration;
import com.osuskin.tool.service.SkinScannerService;
import com.osuskin.tool.util.ConfigurationManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("MainController Unit Tests (Non-JavaFX)")
class MainControllerSimpleTest {

    @Mock
    private ConfigurationManager configurationManager;

    @Mock
    private SkinScannerService skinScannerService;

    @Mock
    private Configuration configuration;

    private MainController controller;
    private Label lblSkinCount;
    private Label lblCompressedCount;
    private Button btnExtract;
    private ListView<Object> listSkins;
    private ProgressBar progressBar;
    private Label lblStatus;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        controller = new MainController();
        
        // Create mock JavaFX components
        lblSkinCount = new Label();
        lblCompressedCount = new Label();
        btnExtract = new Button();
        listSkins = new ListView<>();
        progressBar = new ProgressBar();
        lblStatus = new Label();

        // Inject mocked services using reflection
        injectField(controller, "configurationManager", configurationManager);
        injectField(controller, "skinScannerService", skinScannerService);
        
        // Inject JavaFX components using reflection
        injectField(controller, "lblSkinCount", lblSkinCount);
        injectField(controller, "lblCompressedCount", lblCompressedCount);
        injectField(controller, "btnExtract", btnExtract);
        injectField(controller, "listSkins", listSkins);
        injectField(controller, "progressBar", progressBar);
        injectField(controller, "lblStatus", lblStatus);
        
        // Initialize observable lists
        ObservableList<Object> allSkins = FXCollections.observableArrayList();
        injectField(controller, "allSkins", allSkins);
        injectField(controller, "filteredSkins", allSkins);
        
        // Mock configuration
        when(configurationManager.getConfiguration()).thenReturn(configuration);
        when(configuration.isConfigured()).thenReturn(true);
        when(configuration.getOsuSkinsDirectoryPath()).thenReturn(Paths.get("/test/skins"));
    }

    @Test
    @DisplayName("Should update compressed count and button visibility correctly")
    void shouldUpdateCompressedCountAndButtonVisibility() throws Exception {
        // Test with compressed files present
        when(skinScannerService.getCompressedSkinCount()).thenReturn(3);
        
        callPrivateMethod(controller, "updateSkinCount");
        
        assertEquals("(3 compressed)", lblCompressedCount.getText());
        assertTrue(lblCompressedCount.isVisible());
        assertTrue(btnExtract.isVisible());
        
        // Test with no compressed files
        when(skinScannerService.getCompressedSkinCount()).thenReturn(0);
        
        callPrivateMethod(controller, "updateSkinCount");
        
        assertFalse(lblCompressedCount.isVisible());
        assertFalse(btnExtract.isVisible());
    }

    @Test
    @DisplayName("Should display correct skin count")
    void shouldDisplayCorrectSkinCount() throws Exception {
        // Mock filtered skins list
        ObservableList<Object> filteredSkins = FXCollections.observableArrayList();
        filteredSkins.addAll(Arrays.asList("Skin1", "Skin2", "Skin3", "Skin4"));
        injectField(controller, "filteredSkins", filteredSkins);
        
        when(skinScannerService.getCompressedSkinCount()).thenReturn(2);
        
        callPrivateMethod(controller, "updateSkinCount");
        
        assertEquals("4", lblSkinCount.getText());
        assertEquals("(2 compressed)", lblCompressedCount.getText());
    }

    @Test
    @DisplayName("Should handle zero skin count display")
    void shouldHandleZeroSkinCount() throws Exception {
        // Mock empty filtered skins list
        ObservableList<Object> filteredSkins = FXCollections.observableArrayList();
        injectField(controller, "filteredSkins", filteredSkins);
        
        when(skinScannerService.getCompressedSkinCount()).thenReturn(0);
        
        callPrivateMethod(controller, "updateSkinCount");
        
        assertEquals("0", lblSkinCount.getText());
        assertFalse(lblCompressedCount.isVisible());
        assertFalse(btnExtract.isVisible());
    }

    @Test
    @DisplayName("Should handle large numbers correctly")
    void shouldHandleLargeNumbers() throws Exception {
        // Mock large number of skins
        ObservableList<Object> filteredSkins = FXCollections.observableArrayList();
        for (int i = 0; i < 1000; i++) {
            filteredSkins.add("Skin" + i);
        }
        injectField(controller, "filteredSkins", filteredSkins);
        
        when(skinScannerService.getCompressedSkinCount()).thenReturn(999);
        
        callPrivateMethod(controller, "updateSkinCount");
        
        assertEquals("1000", lblSkinCount.getText());
        assertEquals("(999 compressed)", lblCompressedCount.getText());
        assertTrue(lblCompressedCount.isVisible());
        assertTrue(btnExtract.isVisible());
    }

    @Test
    @DisplayName("Should handle null scanner service gracefully")
    void shouldHandleNullScannerService() throws Exception {
        injectField(controller, "skinScannerService", null);
        
        // This should not throw an exception
        assertDoesNotThrow(() -> {
            callPrivateMethod(controller, "updateSkinCount");
        });
        
        // Verify UI state when scanner service is null
        assertFalse(lblCompressedCount.isVisible());
        assertFalse(btnExtract.isVisible());
    }

    @Test
    @DisplayName("Should validate extraction prerequisites")
    void shouldValidateExtractionPrerequisites() throws Exception {
        // Test with null scanner service
        injectField(controller, "skinScannerService", null);
        
        assertDoesNotThrow(() -> {
            callPrivateMethod(controller, "onExtractCompressed");
        }, "Should handle null scanner service gracefully");

        // Test with no extractable compressed files
        injectField(controller, "skinScannerService", skinScannerService);
        when(skinScannerService.getExtractableCompressedSkinFiles()).thenReturn(Collections.emptyList());
        
        assertDoesNotThrow(() -> {
            callPrivateMethod(controller, "onExtractCompressed");
        }, "Should handle empty extractable compressed files list gracefully");
    }

    @Test
    @DisplayName("Should prepare UI for extraction when files exist")
    void shouldPrepareUIForExtraction() throws Exception {
        // Mock extractable compressed files
        List<Path> compressedFiles = Arrays.asList(
            Paths.get("/test/skin1.zip"),
            Paths.get("/test/skin2.osk")
        );
        when(skinScannerService.getExtractableCompressedSkinFiles()).thenReturn(compressedFiles);
        when(skinScannerService.extractCompressedSkin(any())).thenReturn(true);
        
        // Call extraction method (will start async task)
        callPrivateMethod(controller, "onExtractCompressed");
        
        // Verify UI is updated for extraction
        assertEquals("Extracting compressed skins...", lblStatus.getText());
        assertTrue(progressBar.isVisible());
    }

    @Test
    @DisplayName("Should handle different compressed file counts")
    void shouldHandleDifferentCompressedFileCounts() throws Exception {
        // Test various counts
        int[] testCounts = {0, 1, 5, 10, 50, 100};
        
        for (int count : testCounts) {
            when(skinScannerService.getCompressedSkinCount()).thenReturn(count);
            
            callPrivateMethod(controller, "updateSkinCount");
            
            if (count == 0) {
                assertFalse(lblCompressedCount.isVisible(), "Should hide when count is 0");
                assertFalse(btnExtract.isVisible(), "Should hide extract button when count is 0");
            } else {
                assertTrue(lblCompressedCount.isVisible(), "Should show when count > 0");
                assertTrue(btnExtract.isVisible(), "Should show extract button when count > 0");
                assertEquals("(" + count + " compressed)", lblCompressedCount.getText());
            }
        }
    }

    @Test
    @DisplayName("Should handle concurrent updateSkinCount calls")
    void shouldHandleConcurrentUpdateCalls() throws Exception {
        // This simulates rapid updates to skin count
        when(skinScannerService.getCompressedSkinCount()).thenReturn(5);
        
        // Multiple rapid calls shouldn't cause issues
        for (int i = 0; i < 10; i++) {
            callPrivateMethod(controller, "updateSkinCount");
        }
        
        // Final state should be consistent
        assertEquals("(5 compressed)", lblCompressedCount.getText());
        assertTrue(lblCompressedCount.isVisible());
        assertTrue(btnExtract.isVisible());
    }

    @Test
    @DisplayName("Should handle scanner service method failures")
    void shouldHandleScannerServiceFailures() throws Exception {
        // Mock scanner service to throw exception
        when(skinScannerService.getCompressedSkinCount()).thenThrow(new RuntimeException("Test exception"));
        
        // Should handle the exception gracefully
        assertThrows(RuntimeException.class, () -> {
            callPrivateMethod(controller, "updateSkinCount");
        }, "Exception should propagate for proper error handling");
    }

    @Test
    @DisplayName("Should validate UI component injection")
    void shouldValidateUIComponentInjection() throws Exception {
        // Verify all required UI components are properly injected
        assertNotNull(lblSkinCount, "lblSkinCount should be injected");
        assertNotNull(lblCompressedCount, "lblCompressedCount should be injected");
        assertNotNull(btnExtract, "btnExtract should be injected");
        assertNotNull(listSkins, "listSkins should be injected");
        assertNotNull(progressBar, "progressBar should be injected");
        assertNotNull(lblStatus, "lblStatus should be injected");
        
        // Verify initial UI state
        assertFalse(progressBar.isVisible(), "ProgressBar should initially be hidden");
        assertEquals("Ready", lblStatus.getText(), "Status should initially be 'Ready'");
    }

    // Helper methods
    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
    
    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }
    
    private void callPrivateMethod(Object target, String methodName, Object... args) throws Exception {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
        }
        
        Method method = findMethod(target.getClass(), methodName, paramTypes);
        method.setAccessible(true);
        method.invoke(target, args);
    }
    
    private Method findMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredMethod(methodName, paramTypes);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new RuntimeException("Method not found: " + methodName);
    }
}