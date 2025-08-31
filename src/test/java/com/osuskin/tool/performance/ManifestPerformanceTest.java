package com.osuskin.tool.performance;

import com.osuskin.tool.service.*;
import com.osuskin.tool.util.PerformanceMonitor;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.image.Image;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Performance test to compare old vs new loading approach.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ManifestPerformanceTest {
    
    private static final String TEST_SKINS_PATH = "C:\\Users\\glaes\\AppData\\Local\\osu!\\Skins";
    private static JFXPanel jfxPanel;
    
    @BeforeAll
    public static void initJavaFX() {
        // Initialize JavaFX toolkit
        jfxPanel = new JFXPanel();
        
        // Initialize default cache
        Path defaultPath = Paths.get(TEST_SKINS_PATH, "default");
        DefaultSkinCache.getInstance().initialize(defaultPath);
        
        // Wait for initialization
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    @Test
    @DisplayName("Compare old vs new number loading performance")
    public void testNumberLoadingPerformance() throws Exception {
        List<Path> testSkins = getTestSkins();
        
        System.out.println("\n========================================");
        System.out.println("PERFORMANCE COMPARISON TEST");
        System.out.println("========================================\n");
        
        for (Path skinPath : testSkins) {
            String skinName = skinPath.getFileName().toString();
            System.out.println("\nTesting skin: " + skinName);
            System.out.println("-".repeat(40));
            
            // Test OLD approach
            long oldTime = testOldApproach(skinPath);
            
            // Clear caches
            System.gc();
            Thread.sleep(500);
            
            // Test NEW approach
            long newTime = testNewApproach(skinPath);
            
            // Calculate improvement
            double improvement = ((double)(oldTime - newTime) / oldTime) * 100;
            
            System.out.println(String.format("Results for %s:", skinName));
            System.out.println(String.format("  Old approach: %d ms", oldTime));
            System.out.println(String.format("  New approach: %d ms", newTime));
            System.out.println(String.format("  Improvement: %.1f%% (%.1fx faster)", 
                improvement, (double)oldTime / newTime));
        }
    }
    
    private long testOldApproach(Path skinPath) {
        long startTime = System.currentTimeMillis();
        
        // Simulate old loader behavior
        SkinElementLoader oldLoader = new SkinElementLoader(skinPath);
        
        // Load 20 numbers (score + combo)
        for (int i = 0; i < 10; i++) {
            oldLoader.loadImage("score-" + i);
            oldLoader.loadImage("combo-" + i);
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed;
    }
    
    private long testNewApproach(Path skinPath) {
        long startTime = System.currentTimeMillis();
        
        // Use new optimized loader
        OptimizedSkinElementLoader newLoader = new OptimizedSkinElementLoader(skinPath);
        
        // Load 20 numbers (score + combo)
        for (int i = 0; i < 10; i++) {
            newLoader.loadImage("score-" + i);
            newLoader.loadImage("combo-" + i);
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed;
    }
    
    private List<Path> getTestSkins() {
        List<Path> skins = new ArrayList<>();
        Path skinsDir = Paths.get(TEST_SKINS_PATH);
        
        // Add specific test skins
        String[] testSkinNames = {
            "HDNomod",
            "-         ￼CK￼ WhiteCat 2.1 ~ old-blue",
            "-#KW-! If there was an endpoint",
            "￼sigma￼- MIKU SHOWTIME (Conservative)"
        };
        
        for (String name : testSkinNames) {
            Path skinPath = skinsDir.resolve(name);
            if (Files.exists(skinPath)) {
                skins.add(skinPath);
            }
        }
        
        // If no specific skins found, just take first 3
        if (skins.isEmpty()) {
            try {
                Files.list(skinsDir)
                    .filter(Files::isDirectory)
                    .limit(3)
                    .forEach(skins::add);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        return skins;
    }
}