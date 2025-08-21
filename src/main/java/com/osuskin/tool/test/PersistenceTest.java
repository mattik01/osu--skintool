package com.osuskin.tool.test;

import com.osuskin.tool.service.SkinIndexCache;
import com.osuskin.tool.service.SkinIndexCache.SkinIndex;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PersistenceTest {
    public static void main(String[] args) {
        System.out.println("=== TESTING INDEX PERSISTENCE ===");
        System.out.println("Simulating fresh application start...\n");
        
        // Create a NEW instance (simulating app restart)
        SkinIndexCache cache = new SkinIndexCache();
        
        // Test loading a skin that should have a cached index
        Path testSkin = Paths.get("/root/skins/BubbleSkin23-10-24");
        
        System.out.println("Loading skin: " + testSkin.getFileName());
        long startTime = System.nanoTime();
        
        SkinIndex index = cache.loadOrCreateIndex(testSkin).index;
        
        long loadTime = (System.nanoTime() - startTime) / 1_000_000;
        
        // Check the actual file timestamps
        Path indexFile = Paths.get(System.getProperty("user.home"), 
            ".config", "OsuSkinTool", "skin-indexes",
            "_root_skins_BubbleSkin23-10-24-index.json");
        
        System.out.println("\nResults:");
        System.out.println("- Load time: " + loadTime + "ms");
        System.out.println("- Elements found: " + index.availableElements.size());
        
        if (java.nio.file.Files.exists(indexFile)) {
            try {
                long fileAge = System.currentTimeMillis() - 
                    java.nio.file.Files.getLastModifiedTime(indexFile).toMillis();
                System.out.println("- Index file exists: YES");
                System.out.println("- Index file age: " + (fileAge / 1000) + " seconds");
                
                if (fileAge > 30000) { // If older than 30 seconds
                    System.out.println("\n✅ SUCCESS: Index file persisted from previous run!");
                    System.out.println("   The index was created " + (fileAge / 1000) + " seconds ago");
                    System.out.println("   Indexes ARE persistent across application restarts!");
                } else {
                    System.out.println("\n⚠️  Index was recently created (within 30 seconds)");
                }
            } catch (Exception e) {
                System.out.println("Error checking file: " + e);
            }
        } else {
            System.out.println("- Index file exists: NO");
        }
    }
}