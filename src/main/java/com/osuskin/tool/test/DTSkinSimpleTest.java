package com.osuskin.tool.test;

import com.osuskin.tool.service.SkinIndexCache;
import com.osuskin.tool.service.SkinIndexCache.SkinIndex;
import com.osuskin.tool.service.SkinIndexCache.SkinIndexResult;
import com.osuskin.tool.service.SkinElementLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DTSkinSimpleTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== TESTING DT PROJECT SKIN ISSUE ===\n");
        
        // The problematic skin path
        Path skinPath = Paths.get("/root/skins/-    《DT》 - 『 東方Project 』 Paper Touhou Project  -");
        
        System.out.println("Testing skin path: " + skinPath);
        System.out.println("Path exists: " + Files.exists(skinPath));
        System.out.println("Is directory: " + Files.isDirectory(skinPath));
        
        if (!Files.exists(skinPath)) {
            System.out.println("ERROR: Skin directory does not exist!");
            return;
        }
        
        // Step 1: Test index creation/loading
        System.out.println("\n1. Testing Index Creation:");
        SkinIndexCache indexCache = new SkinIndexCache();
        
        try {
            long startTime = System.nanoTime();
            SkinIndexResult result = indexCache.loadOrCreateIndex(skinPath);
            long loadTime = (System.nanoTime() - startTime) / 1_000_000;
            
            System.out.println("  Index loaded in: " + loadTime + "ms");
            System.out.println("  Used cache: " + result.usedCache);
            System.out.println("  Elements found: " + result.index.availableElements.size());
            System.out.println("  Animations found: " + result.index.animationFrameCounts.size());
            
            // Show first few elements
            System.out.println("\n  Sample elements:");
            result.index.availableElements.stream()
                .limit(10)
                .forEach(e -> System.out.println("    - " + e));
                
        } catch (Exception e) {
            System.out.println("  ERROR: Failed to create/load index!");
            System.out.println("  Exception: " + e.getClass().getName());
            System.out.println("  Message: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Step 2: Test element loader
        System.out.println("\n2. Testing Element Loader:");
        try {
            SkinElementLoader loader = new SkinElementLoader(skinPath);
            
            // Try to check if some basic elements exist
            String[] testElements = {"cursor", "hit0", "hit50", "hit100", "hit300"};
            for (String element : testElements) {
                boolean exists = loader.elementExists(element);
                System.out.println("  " + element + " exists: " + exists);
            }
            
        } catch (Exception e) {
            System.out.println("  ERROR: Failed to initialize element loader!");
            System.out.println("  Exception: " + e.getClass().getName());
            System.out.println("  Message: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Step 3: Check for problematic file names
        System.out.println("\n3. Checking for problematic file names:");
        try {
            Files.list(skinPath)
                .filter(Files::isRegularFile)
                .limit(20)
                .forEach(path -> {
                    String name = path.getFileName().toString();
                    // Check for special characters that might cause issues
                    if (name.contains("《") || name.contains("》") || name.contains("『") || name.contains("』")) {
                        System.out.println("  Special chars in: " + name);
                    }
                });
        } catch (Exception e) {
            System.out.println("  ERROR listing files: " + e.getMessage());
        }
        
        System.out.println("\n=== TEST COMPLETE ===");
    }
}