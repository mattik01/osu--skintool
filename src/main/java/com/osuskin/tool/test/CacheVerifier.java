package com.osuskin.tool.test;

import com.osuskin.tool.service.SkinIndexCache;
import com.osuskin.tool.service.SkinIndexCache.SkinIndex;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CacheVerifier {
    public static void main(String[] args) throws Exception {
        Path skinsDir = Paths.get("/root/skins");
        SkinIndexCache cache = new SkinIndexCache();
        
        System.out.println("=== SKIN INDEX CACHE VERIFICATION ===\n");
        
        // Test a few skins
        String[] testSkins = {
            "BubbleSkin23-10-24",
            "Vaxei 2023",
            "Incandescent Nebulae (ekoro edit)"
        };
        
        for (String skinName : testSkins) {
            Path skinDir = skinsDir.resolve(skinName);
            if (!Files.exists(skinDir)) continue;
            
            System.out.println("Testing: " + skinName);
            System.out.println("----------------------------------------");
            
            // First load (may create or use cache)
            System.out.println("First load:");
            long start1 = System.nanoTime();
            SkinIndex index1 = cache.loadOrCreateIndex(skinDir).index;
            long time1 = (System.nanoTime() - start1) / 1_000_000;
            System.out.println("  Time: " + time1 + "ms");
            System.out.println("  Elements: " + index1.availableElements.size());
            System.out.println("  Animations: " + index1.animationFrameCounts.size());
            
            // Second load (should definitely use cache)
            System.out.println("\nSecond load (should use cache):");
            long start2 = System.nanoTime();
            SkinIndex index2 = cache.loadOrCreateIndex(skinDir).index;
            long time2 = (System.nanoTime() - start2) / 1_000_000;
            System.out.println("  Time: " + time2 + "ms");
            System.out.println("  Cache hit: " + (time2 < 5 ? "YES ✓" : "NO ✗"));
            
            if (time1 > 0 && time2 > 0) {
                double speedup = (double)time1 / time2;
                System.out.println("  Speedup: " + String.format("%.1fx", speedup));
            }
            
            // Check if index file exists
            Path indexFile = skinDir.resolve(".skintool-index.json");
            System.out.println("  Index file exists: " + Files.exists(indexFile));
            if (Files.exists(indexFile)) {
                long size = Files.size(indexFile);
                System.out.println("  Index file size: " + (size / 1024) + "KB");
            }
            
            System.out.println();
        }
        
        System.out.println("=== VERIFICATION COMPLETE ===");
    }
}